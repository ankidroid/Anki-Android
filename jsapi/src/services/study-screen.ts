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
import { Service } from "./service";
import type { ColorHex, Result } from "../types";
import type { Rating } from "../constants";

/**
 * Service for making actions in the Study Screen.
 *
 * Doesn't work on other screens, such as the previewing one.
 */
export class StudyScreen extends Service {
    protected readonly base: string = "study-screen";

    /**
     * @returns the count of New cards in the queue.
     */
    public getNewCount(): Promise<Result<number>> {
        return this.request("get-new-count");
    }

    /**
     * @returns the count of Learning cards in the queue.
     */
    public getLearningCount(): Promise<Result<number>> {
        return this.request("get-learning-count");
    }

    /**
     * @returns the count of To Review cards in the queue.
     */
    public getToReviewCount(): Promise<Result<number>> {
        return this.request("get-to-review-count");
    }

    /**
     * Show the card answer
     */
    public showAnswer(): Promise<Result<void>> {
        return this.request("show-answer");
    }

    /**
     * @returns whether the answer is being shown.
     */
    public isShowingAnswer(): Promise<Result<boolean>> {
        return this.request("is-showing-answer");
    }

    /**
     * Gets the next time a card should be shown after rating it.
     */
    public getNextTime(rating: Rating): Promise<Result<string>> {
        return this.request("get-next-time", { rating: rating });
    }

    /**
     * Opens the card info of the specified ID.
     * @param cardId the ID of the card.
     */
    public openCardInfo(cardId: number): Promise<Result<void>> {
        return this.request("open-card-info", { cardId: cardId });
    }

    /**
     * Opens the note editor to edit the note of the specified card ID.
     * @param cardId the ID of the card.
     */
    public openNoteEditor(cardId: number): Promise<Result<void>> {
        return this.request("open-note-editor", { cardId: cardId });
    }

    /**
     * Sets the background color of the Study screen itself,
     * which includes the toolbar and system bars.
     * This does not change the card background color.
     *
     * @param colorHex the hexadecimal RGB code of the color.
     */
    public setBackgroundColor(colorHex: ColorHex): Promise<Result<void>> {
        return this.request("set-background-color", { colorHex: colorHex });
    }

    /**
     * Answers the top card with the specified rating.
     * @param rating the rating to answer the card.
     */
    public answer(rating: Rating): Promise<Result<void>> {
        return this.request("answer", { rating: rating });
    }

    /**
     * Deletes the current note.
     */
    public deleteNote(): Promise<Result<void>> {
        return this.request("delete-note");
    }
}
