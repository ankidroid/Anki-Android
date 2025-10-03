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

/**
 * @param value The value returned by the API call.
 */
export type Success<T> = { success: true; value: T };

/**
 * @param error Error message.
 */
export type Failure = { success: false; error: string };

/**
 * Base type for API results. Typescript automatically discriminates
 * if it is a `Success` or `Failure` by checking the value of `success`.
 *
 * @example
 * ```ts
 * if (result.success) {
 *     return result.value * 2;
 * } else {
 *     console.error(result.error);
 * }
 * ```
 */
export type Result<T> = Success<T> | Failure;
