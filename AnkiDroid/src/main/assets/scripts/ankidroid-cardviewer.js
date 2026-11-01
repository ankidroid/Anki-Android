// JS common to the app card viewers (reviewer, previewer and template previewer)
// Ignore bridgeCommand
bridgeCommand = function () {};

// https://github.com/ankitects/anki/blob/2d4de33cf3160342c4c704c294e643c3e11071b1/ts/reviewer/index.ts#L32
globalThis.ankiPlatform = "ankidroid";

globalThis.ankidroid = globalThis.ankidroid || {};

/**
 * Sends a POST request to the `/ankidroid/` endpoints of the app.
 *
 * Requests are sent one at a time, so the app handles them in the order they were made.
 * A request that isn't answered within a few seconds is abandoned, so it doesn't block the next ones.
 * @param {string} endpoint
 * @param {Object} [payload] - sent as JSON
 * @returns {Promise<void>} resolved after the app handled the request, or it was abandoned
 */
globalThis.ankidroid.request = (() => {
    const TIMEOUT_MS = 2000;
    let lastRequest = Promise.resolve();
    return function (endpoint, payload = {}) {
        // Not a relative URL: a <base> element in the card would change its destination
        const url = new URL(`/ankidroid/${endpoint}`, location.origin);
        lastRequest = lastRequest
            .then(() => {
                const controller = new AbortController();
                const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);
                return fetch(url, {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                    },
                    body: JSON.stringify(payload),
                    signal: controller.signal,
                }).finally(() => clearTimeout(timeout));
            })
            .then(
                () => {},
                err => console.log(`Failed to send ${endpoint} request:`, err),
            );
        return lastRequest;
    };
})();
