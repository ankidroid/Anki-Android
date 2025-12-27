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
import { Handler, type Contract } from "./types";
import { Android, Card, Collection, Deck, Note, NoteType, StudyScreen, Tts } from "./services";

/**
 * Main entry point for the AnkiDroid JavaScript API.
 *
 * This class provides access to all the available API services, such as `card`, `note`, `studyScreen`, etc.
 * It acts as a central hub for interacting with AnkiDroid's functionalities from within a card template.
 *
 * @example
 * ```javascript
 * const contract = { version: "1.0.0", developer: "Your Contact (e-mail or website)" };
 * const api = new AnkiDroidJs.Api(contract);
 *
 * // Now you can access the different services.
 * const flagResult = await api.card.getFlag();
 * if (flagResult.success) {
 *     console.log("Current card flag:", flagResult.value);
 * }
 * ```
 *
 * @param contract An object containing the API version and developer contact information.
 */
export class Api {
    private readonly handler: Handler;
    /** Service for interacting with Android-specific features. */
    public readonly android: Android;
    /** Service for interacting with the current card. */
    public readonly card: Card;
    /** Service for using general Collection features. */
    public readonly collection: Collection;
    /** Service for interacting with the current deck. */
    public readonly deck: Deck;
    /** Service for interacting with the current note. */
    public readonly note: Note;
    /** Service for interacting with the current note type. */
    public readonly noteType: NoteType;
    /** Service for interacting with the study screen. */
    public readonly studyScreen: StudyScreen;
    /** Service for controlling Text-to-Speech. */
    public readonly tts: Tts;

    constructor(contract: Contract) {
        this.handler = new Handler(contract);
        this.android = new Android(this.handler);
        this.card = new Card(this.handler);
        this.collection = new Collection(this.handler);
        this.deck = new Deck(this.handler);
        this.note = new Note(this.handler);
        this.noteType = new NoteType(this.handler);
        this.studyScreen = new StudyScreen(this.handler);
        this.tts = new Tts(this.handler);
    }

    /**
     * Gets a Card instance for a specific card ID.
     */
    public getCard(id: number): Card {
        return new Card(this.handler, id);
    }

    /**
     * Gets a Note instance for a specific note ID.
     */
    public getNote(id: number): Note {
        return new Note(this.handler, id);
    }

    /**
     * Gets a Deck instance for a specific deck ID.
     */
    public getDeck(id: number): Deck {
        return new Deck(this.handler, id);
    }

    /**
     * Gets a NoteType instance for a specific note type ID.
     */
    public getNoteType(id: number): NoteType {
        return new NoteType(this.handler, id);
    }
}
