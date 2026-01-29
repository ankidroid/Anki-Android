import * as fs from "fs";
import * as path from "path";
import { ErrorCode } from "../src/constants";

const DIST_DIR = "dist";
const OUTPUT_FILENAME = "js-api-error-codes.json";

function exportErrorCodes() {
    try {
        const distPath = path.join(process.cwd(), DIST_DIR);
        const filePath = path.join(distPath, OUTPUT_FILENAME);

        if (!fs.existsSync(distPath)) {
            fs.mkdirSync(distPath, { recursive: true });
        }

        const values = Object.values(ErrorCode);
        const jsonContent = JSON.stringify(values, null, 4);

        fs.writeFileSync(filePath, jsonContent, "utf8");

        console.log(`File created at ${DIST_DIR}/${OUTPUT_FILENAME}`);
    } catch (error) {
        console.error("Failed to export error codes:", error);
        process.exit(1);
    }
}

exportErrorCodes();
