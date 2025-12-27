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

import type { Contract } from "./contract";

/**
 * API requests handler
 */
export class Handler {
    constructor(private readonly contract: Contract) {}
    async request(endpoint: string, data?: any): Promise<any> {
        const url = `/jsapi/${endpoint}`;

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
            throw new Error(`Request failed with status ${response.status}`);
        }

        const responseData = await response.text();
        return JSON.parse(responseData);
    }
}
