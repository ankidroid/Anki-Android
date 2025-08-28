/**
 * Copyright 2025 Brayan Oliveira
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import type { Contract, Result } from "./types";

/**
 * API requests handler
 */
export class Handler {
    constructor(private readonly contract: Contract) {}
    async request(endpoint: string, data?: any): Promise<Result<any>> {
        const url = `/jsapi/${endpoint}`;

        try {
            const response = await fetch(url, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    version: this.contract.version,
                    developer: this.contract.developer,
                    data: data,
                }),
            });

            if (!response.ok) {
                return {
                    success: false,
                    error: `Request failed with status ${response.status}`,
                };
            }

            const json = await response.json();
            if (this.isResult(json)) {
                return json;
            }

            return {
                success: false,
                error: "Invalid response format received from server",
            };
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : "Unknown error occurred";
            return {
                success: false,
                error: errorMessage,
            };
        }
    }

    private isResult(obj: any): obj is Result<any> {
        if (typeof obj !== "object" || obj === null || typeof obj.success !== "boolean") {
            return false;
        }
        if (!obj.success) {
            return typeof obj.error === "string";
        }
        return "value" in obj;
    }
}
