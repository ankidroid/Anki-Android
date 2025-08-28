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

    console.log("📚 Analyzing enums...");
    for (const sourceFile of project.getSourceFiles()) {
        const enums = sourceFile.getEnums();
        for (const enumDec of enums) {
            let isNumeric = true;
            for (const member of enumDec.getMembers()) {
                const initializer = member.getInitializer();
                if (initializer && !Node.isNumericLiteral(initializer)) {
                    isNumeric = false;
                    break;
                }
            }
            const enumType = isNumeric ? "number" : "string";
            console.log(`  - Found enum "${enumDec.getName()}" -> resolving to "${enumType}"`);
            typeResolutionMap.set(enumDec.getName(), enumType);
        }
    }

    console.log("📚 Analyzing type aliases...");
    for (const sourceFile of project.getSourceFiles()) {
        const typeAliases = sourceFile.getTypeAliases();
        for (const typeAlias of typeAliases) {
            const typeNode = typeAlias.getTypeNode();
            if (typeNode) {
                const kind = typeNode.getKind();
                if (kind === SyntaxKind.StringKeyword || kind === SyntaxKind.TemplateLiteralType) {
                    console.log(
                        `  - Found type alias "${typeAlias.getName()}" -> resolving to "string"`,
                    );
                    typeResolutionMap.set(typeAlias.getName(), "string");
                } else if (kind === SyntaxKind.NumberKeyword) {
                    console.log(
                        `  - Found type alias "${typeAlias.getName()}" -> resolving to "number"`,
                    );
                    typeResolutionMap.set(typeAlias.getName(), "number");
                }
            }
        }
    }

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
                const returnType = method.getReturnType().getText(method);
                const match = returnType.match(API_METHOD_RETURN_TYPE_REGEX);

                if (!match) {
                    continue;
                }

                const methodName = method.getName();
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
                    const typeNodeText = p.getTypeNodeOrThrow().getText();
                    paramsObject[paramName] = typeResolutionMap.get(typeNodeText) ?? typeNodeText;
                }

                result[baseName][endpoint] = {
                    params: paramsObject,
                    return: returnTypeT,
                };
                const paramsString = JSON.stringify(paramsObject);
                console.log(
                    `  - ✅ Parsed method "${methodName}" -> endpoint: "${endpoint}", params: ${paramsString}, returns: "${returnTypeT}"`,
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
