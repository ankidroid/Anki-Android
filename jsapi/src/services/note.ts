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
 * Service for manipulating Anki Notes.
 */
export class Note extends Service {
    protected readonly base: string = "note";

    /**
     * The note ID. If null, it will represent the note of the queue's top card.
     */
    private readonly id: number | null;
    constructor(handler: Handler, id: number | null = null) {
        super(handler);
        if (id !== null && (!Number.isInteger(id) || id < 0)) {
            throw new Error("Note ID must be a positive integer.");
        }
        this.id = id;
    }
    /**
     * @returns The note ID.
     */
    public getId(): Promise<Result<number>> {
        if (this.id !== null) {
            return Promise.resolve({ success: true, value: this.id });
        }
        return this.request("get-id");
    }

    /**
     * @returns the ID of the note type.
     */
    public getNoteTypeId(): Promise<Result<number>> {
        return this.request("get-note-type-id");
    }

    /**
     * @returns the IDs of cards of this note
     */
    public getCardIds(): Promise<Result<number[]>> {
        return this.request("get-card-ids");
    }

    /**
     * Buries the note.
     * @returns the number of buried cards.
     */
    public bury(): Promise<Result<number>> {
        return this.request("bury");
    }

    /**
     * Suspends the note.
     @returns the number of suspended cards.
     */
    public suspend(): Promise<Result<number>> {
        return this.request("suspend");
    }

    /**
     * @returns the note tags, separated by spaces
     */
    public getTags(): Promise<Result<string>> {
        return this.request("get-tags");
    }

    /**
     * @param tags space separated tags
     */
    public setTags(tags: string): Promise<Result<void>> {
        return this.request("set-tags", { tags });
    }

    /**
     * Toggles the "marked" status of the note.
     */
    public toggleMark(): Promise<Result<void>> {
        return this.request("toggle-mark");
    }

    override async request(endpoint: string, data?: Record<string, any>): Promise<any> {
        return super.request(endpoint, { id: this.id, ...(data || {}) });
    }
}
