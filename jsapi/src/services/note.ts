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
import type { CardId, NoteId, NoteTypeId, Result } from "../types";
import { Service } from "./service";
import type { Handler } from "../handler";

/**
 * Service for manipulating Anki notes.
 */
export class Note extends Service {
    protected readonly base: string = "note";

    /**
     * The note ID. If null, it will represent the note of the queue's top card.
     */
    private readonly id: NoteId | null;

    /**
     * Service for manipulating Anki notes.
     *
     * @param handler
     * @param id the ID of the note. If null, it will represent the note
     *  of the queue's top card. If not, it must be a positive integer.
     *
     * @throws {RangeError} if the provided ID is invalid.
     */
    constructor(handler: Handler, id: NoteId | null = null) {
        super(handler);
        if (id !== null && (!Number.isInteger(id) || id <= 0)) {
            throw new RangeError("Note ID must be a positive integer.");
        }
        this.id = id;
    }
    /**
     * @returns The note ID.
     */
    public getId(): Promise<Result<NoteId>> {
        if (this.id !== null) {
            return Promise.resolve({ success: true, value: this.id });
        }
        return this.request("get-id");
    }

    /**
     * @returns the ID of the note type.
     */
    public getNoteTypeId(): Promise<Result<NoteTypeId>> {
        return this.request("get-note-type-id");
    }

    /**
     * @returns the IDs of cards of this note
     */
    public getCardIds(): Promise<Result<CardId[]>> {
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

    /**
     * Sends a request to the note service.
     *
     * This automatically injects the current instance's {@link id} into the request payload
     * to ensure the operation is performed on the correct note context.
     *
     * @param endpoint The specific endpoint operation to execute (e.g., "get-card-ids").
     * @param data Optional additional parameters for the request.
     * @returns A promise resolving to the handler's response.
     */
    override async request<T>(endpoint: string, data?: Record<string, any>): Promise<Result<T>> {
        return super.request(endpoint, { id: this.id, ...(data || {}) });
    }
}
