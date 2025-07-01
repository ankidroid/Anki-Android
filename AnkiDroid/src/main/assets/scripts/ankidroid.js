"use strict";
globalThis.ankidroid = globalThis.ankidroid || {};

globalThis.ankidroid.userAction = function (number) {
    try {
        let userJs = globalThis[`userJs${number}`];
        if (userJs != null) {
            userJs();
        } else {
            window.location.href = `missing-user-action:${number}`;
        }
    } catch (e) {
        alert(e);
    }
};

globalThis.ankidroid.showHint = function () {
    document.querySelector("a.hint:not([style*='display: none'])")?.click();
};

globalThis.ankidroid.showAllHints = function () {
    document.querySelectorAll("a.hint").forEach(el => el.click());
};

/**
 * @param {InputEvent} event - the oninput event of the type answer <input>
 */
globalThis.ankidroid.onTypeAnswerInput = function (event) {
    const encodedValue = encodeURIComponent(event.target.value);
    window.location.href = `ankidroid://typeinput/${encodedValue}`;
};

document.addEventListener("focusin", event => {
    window.location.href = `ankidroid://focusin`;
});

document.addEventListener("focusout", event => {
    window.location.href = `ankidroid://focusout`;
});

(() => {
    const DOUBLE_TAP_TIMEOUT = 250; // Max ms between taps for a double tap.
    const SCHEME = "gesture";

    let startX = 0,
        startY = 0,
        tapTimer = null,
        isSingleTouch = false;

    document.addEventListener(
        "touchstart",
        event => {
            // Ignore multi-touch gestures (like two-finger taps)
            if (event.touches.length > 1) {
                isSingleTouch = false;
                return;
            }
            isSingleTouch = true;
            startX = event.touches[0].pageX;
            startY = event.touches[0].pageY;
        },
        { passive: true },
    );

    document.addEventListener(
        "touchend",
        event => {
            if (!isSingleTouch || isTextSelected() || isInteractable(event)) return;

            if (tapTimer != null) {
                clearTimeout(tapTimer);
                tapTimer = null;
                window.location.href = `${SCHEME}://doubleTap`;
                return;
            }

            const endX = event.changedTouches[0].pageX;
            const endY = event.changedTouches[0].pageY;
            const scrollDirection = getScrollDirection(event.target);
            const params = new URLSearchParams({
                x: Math.round(endX),
                y: Math.round(endY),
                deltaX: Math.round(endX - startX),
                deltaY: Math.round(endY - startY),
            });
            if (scrollDirection !== null) {
                params.append("scrollDirection", scrollDirection);
            }
            const requestUrl = `${SCHEME}://tapOrSwipe/?${params.toString()}`;

            tapTimer = setTimeout(() => {
                window.location.href = requestUrl;
                tapTimer = null;
            }, DOUBLE_TAP_TIMEOUT);
        },
        { passive: true },
    );

    /**
     * Checks if the target element or its parents are interactive.
     * @param {TouchEvent} event
     * @returns {boolean}
     */
    function isInteractable(event) {
        let node = event.target;
        while (node && node !== document) {
            if (
                node.nodeName === "A" ||
                node.onclick ||
                node.nodeName === "BUTTON" ||
                node.nodeName === "VIDEO" ||
                node.nodeName === "SUMMARY" ||
                node.nodeName === "INPUT" ||
                node.getAttribute("contentEditable") ||
                (node.classList && node.classList.contains("tappable"))
            ) {
                return true;
            }
            node = node.parentNode;
        }
        return false;
    }

    /**
     * Checks if the user is selecting text.
     * @returns {boolean}
     */
    function isTextSelected() {
        return !document.getSelection().isCollapsed;
    }

    /**
     * Checks if an element or its parents are scrollable and returns the direction(s).
     * It traverses up the DOM from the event target, checking the first scrollable ancestor.
     * @param {HTMLElement} target - The element where the touch ended.
     * @returns {'h'|'v'|'hv'|null} - The scroll direction(s) if found, otherwise null.
     */
    function getScrollDirection(target) {
        let node = target;
        while (node && node.nodeType === Node.ELEMENT_NODE) {
            const style = window.getComputedStyle(node);

            const isHorizontallyScrollable =
                (style.overflowX === "auto" || style.overflowX === "scroll") &&
                node.scrollWidth > node.clientWidth;

            const isVerticallyScrollable =
                (style.overflowY === "auto" || style.overflowY === "scroll") &&
                node.scrollHeight > node.clientHeight;

            if (isHorizontallyScrollable && isVerticallyScrollable) {
                return "hv";
            }
            if (isHorizontallyScrollable) {
                return "h";
            }
            if (isVerticallyScrollable) {
                return "v";
            }
            node = node.parentNode;
        }
        // No scrollable parent was found.
        return null;
    }
})();
