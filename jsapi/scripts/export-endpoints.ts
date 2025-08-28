import { MethodDeclaration, Node, Project, StringLiteral, SyntaxKind } from "ts-morph";
import * as fs from "fs";
import * as path from "path";

const packageJson = JSON.parse(
    fs.readFileSync(path.resolve(process.cwd(), "package.json"), "utf-8"),
);
const packageVersion = packageJson.version;

const SOURCE_DIRECTORY = "src";
const SERVICES_DIRECTORY = "src/services";
const OUTPUT_FILE = "dist/js-api-endpoints.json";
const API_SERVICE_BASE_CLASS = "Service";
const API_METHOD_RETURN_TYPE_REGEX = /^Promise<Result<(.+)>>$/;

/**
 * Converts a string from camelCase to kebab-case.
 * @param str The camelCase string.
 * @returns The kebab-case string.
 */
function toKebabCase(str: string): string {
    return str.replace(/([a-z0-9]|(?=[A-Z]))([A-Z])/g, "$1-$2").toLowerCase();
}

async function generateApiEndpoints() {
    console.log("🔍 Starting API endpoint analysis...");

    const project = new Project();
    project.addSourceFilesAtPaths(`${SOURCE_DIRECTORY}/**/*.ts`);

    const result: Record<
        string,
        Record<string, { params: Record<string, string>; return: string }>
    > = {};
    let errorCount = 0;

    const typeResolutionMap = new Map<string, string>();

    // -------------------------------------------------------------------------
    // Analyze Type Aliases
    // -------------------------------------------------------------------------
    console.log("📚 Analyzing type aliases...");
    for (const sourceFile of project.getSourceFiles()) {
        const typeAliases = sourceFile.getTypeAliases();
        for (const typeAlias of typeAliases) {
            const name = typeAlias.getName();

            const type = typeAlias.getType();
            let resolvedType: string | undefined;

            if (type.isBoolean() || type.isBooleanLiteral()) {
                resolvedType = "boolean";
            } else if (type.isString() || type.isStringLiteral()) {
                resolvedType = "string";
            } else if (type.isNumber() || type.isNumberLiteral()) {
                resolvedType = "number";
            } else if (type.isUnion()) {
                const unionTypes = type.getUnionTypes();

                const allNumbers = unionTypes.every(t => t.isNumber() || t.isNumberLiteral());
                const allStrings = unionTypes.every(t => t.isString() || t.isStringLiteral());

                if (allNumbers) {
                    resolvedType = "number";
                } else if (allStrings) {
                    resolvedType = "string";
                }
            } else if (type.isIntersection()) {
                const intersectionTypes = type.getIntersectionTypes();
                const hasNumber = intersectionTypes.some(t => t.isNumber() || t.isNumberLiteral());
                const hasString = intersectionTypes.some(t => t.isString() || t.isStringLiteral());

                if (hasNumber) {
                    resolvedType = "number";
                } else if (hasString) {
                    resolvedType = "string";
                }
            }

            if (resolvedType) {
                console.log(`  - Found type alias "${name}" -> resolving to "${resolvedType}"`);
                typeResolutionMap.set(name, resolvedType);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Process Services
    // -------------------------------------------------------------------------
    const serviceFiles = project
        .getSourceFiles()
        .filter(f => f.getFilePath().includes(SERVICES_DIRECTORY));
    console.log(`Found ${serviceFiles.length} service files to analyze.`);

    for (const sourceFile of serviceFiles) {
        const classes = sourceFile.getClasses();
        for (const classDec of classes) {
            const extendsClause = classDec.getExtends();
            if (extendsClause?.getText() !== API_SERVICE_BASE_CLASS) {
                continue;
            }

            const usedEndpoints = new Set<string>();

            const baseProperty = classDec.getProperty("base");
            if (!baseProperty) {
                console.error(
                    `❌ ERROR: Class "${classDec.getName()}" extends ${API_SERVICE_BASE_CLASS} but lacks a 'base' property.`,
                );
                errorCount++;
                continue;
            }

            const baseInitializer = baseProperty.getInitializer();
            if (!baseInitializer || !Node.isStringLiteral(baseInitializer)) {
                console.error(
                    `❌ ERROR: The 'base' property in class "${classDec.getName()}" must be a string literal.`,
                );
                errorCount++;
                continue;
            }

            const baseName = baseInitializer.getLiteralText();
            result[baseName] = {};
            console.log(
                `\nProcessing service: "${baseName}" from class "${classDec.getName()}"...`,
            );

            const methods = classDec.getMethods();
            for (const method of methods) {
                const methodName = method.getName();

                if (methodName === "request") {
                    continue;
                }

                const returnType = method.getReturnType().getText(method);
                const match = returnType.match(API_METHOD_RETURN_TYPE_REGEX);

                if (!match) {
                    console.log(`  - ℹ️ "${methodName}" - not an API method`);
                    continue;
                }

                const returnTypeT = match[1];
                const endpointLiteral = findEndpointInMethod(method);
                if (!endpointLiteral) {
                    console.error(
                        `  - ❌ Method "${methodName}" seems to be an API method but no endpoint string literal could be found.`,
                    );
                    errorCount++;
                    continue;
                }

                const endpoint = endpointLiteral.getLiteralText();

                if (usedEndpoints.has(endpoint)) {
                    console.error(
                        `  - ❌ Validation failed for method "${methodName}": Endpoint "${endpoint}" is already in use in the "${baseName}" service.`,
                    );
                    errorCount++;
                } else {
                    usedEndpoints.add(endpoint);
                }

                const expectedEndpoint = toKebabCase(methodName);
                if (endpoint !== expectedEndpoint) {
                    console.error(
                        `  - ❌ Validation failed for method "${methodName}": Endpoint "${endpoint}" should be "${expectedEndpoint}".`,
                    );
                    errorCount++;
                }

                const parameters = method.getParameters();
                const paramsObject: Record<string, string> = {};
                for (const p of parameters) {
                    const paramName = p.getName();
                    // Clean up explicit import types if present (e.g., import("...").Flag -> Flag)
                    let typeNodeText = p.getTypeNodeOrThrow().getText();
                    if (typeNodeText.includes(".")) {
                        typeNodeText = typeNodeText.split(".").pop()!;
                    }

                    // Look up the type in our resolution map, fallback to the raw text
                    paramsObject[paramName] = typeResolutionMap.get(typeNodeText) ?? typeNodeText;
                }

                // Resolving the return type T as well if it matches a known alias
                let resolvedReturnType = typeResolutionMap.get(returnTypeT) ?? returnTypeT;

                if (returnTypeT.endsWith("[]")) {
                    const baseType = returnTypeT.slice(0, -2); // Strip "[]"
                    const resolvedBase = typeResolutionMap.get(baseType);
                    if (resolvedBase) {
                        resolvedReturnType = `${resolvedBase}[]`;
                    }
                } else {
                    resolvedReturnType = typeResolutionMap.get(returnTypeT) ?? returnTypeT;
                }

                result[baseName][endpoint] = {
                    params: paramsObject,
                    return: resolvedReturnType,
                };
                const paramsString = JSON.stringify(paramsObject);
                console.log(
                    `  - ✅ "${methodName}" -> endpoint: "${endpoint}", params: ${paramsString}, returns: "${resolvedReturnType}"`,
                );
            }
        }
    }

    if (errorCount > 0) {
        console.error(`\n🚨 Found ${errorCount} error(s). Halting JSON file generation.`);
        process.exit(1);
    }

    const finalOutput = {
        version: packageVersion,
        endpoints: result,
    };

    try {
        const outputDir = path.dirname(OUTPUT_FILE);
        if (!fs.existsSync(outputDir)) {
            fs.mkdirSync(outputDir, { recursive: true });
        }
        fs.writeFileSync(OUTPUT_FILE, JSON.stringify(finalOutput, null, 2));
        console.log(`\n✨ Success! API endpoints JSON file created at "${OUTPUT_FILE}"`);
    } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        console.error(`\n🔥 Failed to write output file: ${message}`);
        process.exit(1);
    }
}

function findEndpointInMethod(method: MethodDeclaration): StringLiteral | undefined {
    const callExpressions = method.getDescendantsOfKind(SyntaxKind.CallExpression);
    for (const callExpr of callExpressions) {
        const expression = callExpr.getExpression();
        if (Node.isPropertyAccessExpression(expression)) {
            if (expression.getExpression().getKind() === SyntaxKind.ThisKeyword) {
                const args = callExpr.getArguments();
                if (args.length > 0 && Node.isStringLiteral(args[0])) {
                    return args[0] as StringLiteral;
                }
            }
        }
    }
    return undefined;
}

generateApiEndpoints().catch(err => {
    console.error("An unexpected error occurred:", err);
    process.exit(1);
});
