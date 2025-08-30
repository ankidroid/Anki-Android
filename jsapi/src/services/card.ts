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
import { Flag } from "../constants";
import type { Result } from "../types";
import type { Handler } from "../types";
import { Service } from "./service";

/**
 * Service for manipulating Anki Cards.
 */
export class Card extends Service {
    protected readonly base: string = "card";
    /**
     * The card ID. If null, it will represent the queue's top card.
     */
    private readonly id: number | null;
    constructor(handler: Handler, id: number | null = null) {
        super(handler);
        if (id !== null && (!Number.isInteger(id) || id < 0)) {
            throw new Error("Card ID must be a positive integer.");
        }
        this.id = id;
    }

    /**
     * @returns The card ID.
     */
    public getId(): Promise<Result<number>> {
        if (this.id !== null) {
            return Promise.resolve({ success: true, value: this.id });
        }
        return this.request("get-id");
    }

    /**
     * Gets the flag of the current card.
     * 0: No flag, 1: Red, 2: Orange, 3: Green, 4: Blue, 5: Pink, 6: Turquoise, 7: Purple
     * @returns The card flag number.
     */
    public getFlag(): Promise<Result<number>> {
        return this.request("get-flag");
    }

    /**
     * Gets the `reps` property value of the card.
     * It represents the card's number of reviews.
     * @returns The card `reps` property
     */
    public getReps(): Promise<Result<number>> {
        return this.request("get-reps");
    }

    /**
     * Gets the card interval value.
     * Negative = seconds, positive = days.
     * v3 scheduler uses seconds only for intraday (re)learning cards
     * and days for interday (re)learning cards and review cards
     * @returns The card interval value.
     */
    public getInterval(): Promise<Result<number>> {
        return this.request("get-interval");
    }

    /**
     * @returns The ease factor of the card in permille (parts per thousand)
     */
    public getFactor(): Promise<Result<number>> {
        return this.request("get-factor");
    }

    /**
     * Gets the card's last modification time.
     * @returns The card `mod` property.
     */
    public getMod(): Promise<Result<number>> {
        return this.request("get-mod");
    }

    /**
     * Gets the ID of the note of this card.
     * @returns The card `nid` property
     */
    public getNid(): Promise<Result<number>> {
        return this.request("get-nid");
    }

    /**
     * Whether the card is New (0), Learning (1), Review (2), Relearning (3)
     * @returns The card `type` property
     */
    public getType(): Promise<Result<number>> {
        return this.request("get-type");
    }

    /**
     * Gets the ID of the deck containing this card.
     * @returns The card `did` property
     */
    public getDid(): Promise<Result<number>> {
        return this.request("get-did");
    }

    /**
     * Gets the card remaining steps
     * @returns The card `left` property
     */
    public getLeft(): Promise<Result<number>> {
        return this.request("get-left");
    }

    /**
     * Gets the ID of the original deck containing this card.
     * Only used when the card is currently in filtered deck.
     * @returns The card `odid` property
     */
    public getODid(): Promise<Result<number>> {
        return this.request("get-o-did");
    }

    /**
     * Gets the original due value of the card.
     * In filtered decks, it's the original due date that the card had before moving to filtered.
     * @returns The card `odue` property
     */
    public getODue(): Promise<Result<number>> {
        return this.request("get-o-due");
    }

    /**
     * Gets the `queue` property of the card. It can be:
     * * -3 = buried by the user,
     * * -2 = buried by the scheduler,
     * * -1 = suspended
     * * 0 = new
     * * 1 = learning
     * * 2 = review
     * * 3 = in learning, next review in at least a day after the previous review
     * * 4 = review
     * @returns The card `queue` property
     */
    public getQueue(): Promise<Result<number>> {
        return this.request("get-queue");
    }

    /**
     * Gets the number of times the card went from a "was answered correctly" to "was answered incorrectly" state
     * @returns The card `lapses` property
     */
    public getLapses(): Promise<Result<number>> {
        return this.request("get-lapses");
    }

    /**
     * @returns the card's question
     */
    public getQuestion(): Promise<Result<string>> {
        return this.request("get-question");
    }

    /**
     * @returns the card's answer
     */
    public getAnswer(): Promise<Result<string>> {
        return this.request("get-answer");
    }

    /**
     * Gets the card due value.
     * Due is used differently for different card types:
     * * New: the order in which cards are to be studied; starts from 1.
     * * Learning/relearning: epoch timestamp in seconds
     * * Review: days since the collection's creation time
     * * Filtered decks: the position of the card in the filtered deck.
     * The value of due before moving to the filtered deck is saved as odue.
     * @returns The card `due` property
     */
    public getDue(): Promise<Result<number>> {
        return this.request("get-due");
    }

    /**
     * @returns whether the card is marked
     */
    public isMarked(): Promise<Result<boolean>> {
        return this.request("is-marked");
    }

    /**
     * Buries the card.
     * @returns the number of buried cards.
     */
    public bury(): Promise<Result<number>> {
        return this.request("bury");
    }

    /**
     * Suspends the card.
     * @returns the number of suspended cards.
     */
    public suspend(): Promise<Result<number>> {
        return this.request("suspend");
    }

    /**
     * Unburies the card
     */
    public unbury(): Promise<Result<void>> {
        return this.request("unbury");
    }

    /**
     * Unsuspends the card
     */
    public unsuspend(): Promise<Result<void>> {
        return this.request("unsuspend");
    }

    /**
     * Resets the card progress.
     */
    public resetProgress(): Promise<Result<void>> {
        return this.request("reset-progress");
    }

    /**
     * Toggles a flag on the current card.
     * @param flag The flag to set on the card.
     */
    public toggleFlag(flag: Flag): Promise<Result<void>> {
        if (flag < 0 || flag > 7) {
            return Promise.resolve({
                success: false,
                error: "Flag must be an integer between 0 and 7.",
            });
        }
        return this.request("toggle-flag", { flag });
    }

    override async request(endpoint: string, data?: Record<string, any>): Promise<any> {
        return super.request(endpoint, { id: this.id, ...(data || {}) });
    }
}
