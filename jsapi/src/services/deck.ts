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
import type { Result } from "../types";
import type { Handler } from "../types";
import { Service } from "./service";

/**
 * Service for manipulating Anki Decks.
 */
export class Deck extends Service {
    protected readonly base: string = "deck";
    /**
     * The deck ID. If null, it will represent the deck of the queue's top card.
     */
    private readonly id: number | null;
    constructor(handler: Handler, id: number | null = null) {
        super(handler);
        if (id !== null && (!Number.isInteger(id) || id < 0)) {
            throw new Error("Deck ID must be a positive integer.");
        }
        this.id = id;
    }

    /**
     * @returns The deck ID.
     */
    public getId(): Promise<Result<number>> {
        if (this.id !== null) {
            return Promise.resolve({ success: true, value: this.id });
        }
        return this.request("get-id");
    }

    /**
     * @returns The deck name.
     */
    public getName(): Promise<Result<string>> {
        return this.request("get-name");
    }

    /**
     * @returns whether the deck is a filtered deck.
     */
    public isFiltered(): Promise<Result<boolean>> {
        return this.request("is-filtered");
    }

    override async request(endpoint: string, data?: Record<string, any>): Promise<any> {
        return super.request(endpoint, { id: this.id, ...(data || {}) });
    }
}
