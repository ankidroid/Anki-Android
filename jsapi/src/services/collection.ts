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
import { Service } from "./service";

/**
 * Service to access Anki Collection functionalities
 */
export class Collection extends Service {
    protected readonly base: string = "collection";

    /**
     * Undoes the last action
     * @returns the label of the undone action.
     */
    public undo(): Promise<Result<string>> {
        return this.request("undo");
    }

    /**
     * Redoes the last action
     * @returns the label of the redone action.
     */
    public redo(): Promise<Result<string>> {
        return this.request("redo");
    }

    /**
     * @returns whether undo is available
     */
    public isUndoAvailable(): Promise<Result<boolean>> {
        return this.request("is-undo-available");
    }

    /**
     * @returns whether redo is available
     */
    public isRedoAvailable(): Promise<Result<boolean>> {
        return this.request("is-redo-available");
    }

    /**
     * @param search the query to find the cards
     * @returns the IDs of the cards found by the search
     */
    public findCards(search: string): Promise<Result<number[]>> {
        return this.request("find-cards", { search: search });
    }

    /**
     * @param search the query to find the notes
     * @returns the IDs of the notes found by the search
     */
    public findNotes(search: string): Promise<Result<number[]>> {
        return this.request("find-notes", { search: search });
    }
}
