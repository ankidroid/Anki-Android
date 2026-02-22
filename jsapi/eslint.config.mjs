import tseslint from "typescript-eslint";
import headers from "eslint-plugin-headers";

export default tseslint.config(
    ...tseslint.configs.recommended,

    {
        rules: {
            "@typescript-eslint/no-explicit-any": "off",
        },
    },

    {
        files: ["src/**/*.ts"],
        plugins: {
            headers,
        },
        rules: {
            "headers/header-format": [
                "error",
                {
                    source: "file",
                    path: "templates/copyright.txt",
                    style: "jsdoc",
                    patterns: {
                        "year": {
                            "pattern": "\\d{4}",
                            "defaultValue": "2025"
                        },
                        "author": {
                            "pattern": "\\w.*",
                            "defaultValue": "AnkiDroid"
                        }
                    }
                },
            ],
        },
    },

    {
        ignores: [
            "dist/",
            "node_modules/",
            "vite.config.ts"
        ],
    }
);
