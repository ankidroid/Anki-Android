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
    const SCHEME = "gesture";
    const MULTI_TOUCH_TIMEOUT = 300;

    let startX = 0,
        startY = 0,
        touchCount = 0,
        touchStartTime = 0;

    document.addEventListener(
        "touchstart",
        event => {
            touchCount = event.touches.length;
            startX = event.touches[0].pageX;
            startY = event.touches[0].pageY;
            // start counting from the first finger touch
            if (touchCount == 1) {
                touchStartTime = Date.now();
            }
        },
        { passive: true },
    );

    document.addEventListener(
        "touchend",
        event => {
            // Only process after the final finger is lifted
            if (
                event.touches.length > 0 ||
                touchCount > 4 ||
                isTextSelected() ||
                isInteractable(event)
            )
                return;

            // Multi-finger detection
            if (touchCount > 1) {
                if (Date.now() - touchStartTime > MULTI_TOUCH_TIMEOUT) {
                    return;
                }
                window.location.href = `${SCHEME}://multiFingerTap/?touchCount=${touchCount}`;
                return;
            }

            // Swipes and tap detection
            const endX = event.changedTouches[0].pageX;
            const endY = event.changedTouches[0].pageY;
            const scrollDirection = getScrollDirection(event.target);
            const params = new URLSearchParams({
                x: Math.round(endX),
                y: Math.round(endY),
                deltaX: Math.round(endX - startX),
                deltaY: Math.round(endY - startY),
                time: Date.now(),
            });
            if (scrollDirection !== null) {
                params.append("scrollDirection", scrollDirection);
            }
            window.location.href = `${SCHEME}://tapOrSwipe/?${params.toString()}`;
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
