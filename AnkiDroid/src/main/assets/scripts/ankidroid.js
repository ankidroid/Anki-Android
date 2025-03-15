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
    var hints = document.querySelectorAll("a.hint");
    for (var i = 0; i < hints.length; i++) {
        if (hints[i].style.display != "none") {
            hints[i].click();
            break;
        }
    }
};

globalThis.ankidroid.showAllHints = function () {
    document.querySelectorAll("a.hint").forEach(el => {
        el.click();
    });
};
