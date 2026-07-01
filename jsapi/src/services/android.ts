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
 * Service to access Android specific general functionalities.
 */
export class Android extends Service {
    protected readonly base: string = "android";

    /**
     * Shows an Android dismissable snackbar.
     * @param text the message to be shown in the snackbar.
     * @param duration the duration in milliseconds to show the snackbar.
     * For Android standard durations, use 0 for long, -1 for short, and -2 for indefinite.
     */
    public showSnackbar(text: string, duration: number): Promise<Result<void>> {
        return this.request("show-snackbar", { text, duration });
    }

    /**
     * @returns whether the system is using night mode.
     */
    public isSystemInDarkMode(): Promise<Result<boolean>> {
        return this.request("is-system-in-dark-mode");
    }

    /**
     * @returns whether the active network is metered
     */
    public isNetworkMetered(): Promise<Result<boolean>> {
        return this.request("is-network-metered");
    }
}
