// this progress bar addon with AnkiDroid JS API for testing

var progressDiv = document.createElement("div");
progressDiv.id = "h-progress";
progressDiv.className = "progress-1";
progressDiv.innerHTML = '<div class="progress-bar-1" id="h-bar"></div>';

// v0.5.2 - https://github.com/SimonLammer/anki-persistence/blob/62463a7f63e79ce12f7a622a8ca0beb4c1c5d556/script.js
if (void 0 === window.Persistence) { var _persistenceKey = "github.com/SimonLammer/anki-persistence/", _defaultKey = "_default"; if (window.Persistence_sessionStorage = function () { var e = !1; try { "object" == typeof window.sessionStorage && (e = !0, this.clear = function () { for (var e = 0; e < sessionStorage.length; e++) { var t = sessionStorage.key(e); 0 == t.indexOf(_persistenceKey) && (sessionStorage.removeItem(t), e--) } }, this.setItem = function (e, t) { void 0 == t && (t = e, e = _defaultKey), sessionStorage.setItem(_persistenceKey + e, JSON.stringify(t)) }, this.getItem = function (e) { return void 0 == e && (e = _defaultKey), JSON.parse(sessionStorage.getItem(_persistenceKey + e)) }, this.removeItem = function (e) { void 0 == e && (e = _defaultKey), sessionStorage.removeItem(_persistenceKey + e) }) } catch (e) { } this.isAvailable = function () { return e } }, window.Persistence_windowKey = function (e) { var t = window[e], i = !1; "object" == typeof t && (i = !0, this.clear = function () { t[_persistenceKey] = {} }, this.setItem = function (e, i) { void 0 == i && (i = e, e = _defaultKey), t[_persistenceKey][e] = i }, this.getItem = function (e) { return void 0 == e && (e = _defaultKey), t[_persistenceKey][e] || null }, this.removeItem = function (e) { void 0 == e && (e = _defaultKey), delete t[_persistenceKey][e] }, void 0 == t[_persistenceKey] && this.clear()), this.isAvailable = function () { return i } }, window.Persistence = new Persistence_sessionStorage, Persistence.isAvailable() || (window.Persistence = new Persistence_windowKey("py")), !Persistence.isAvailable()) { var titleStartIndex = window.location.toString().indexOf("title"), titleContentIndex = window.location.toString().indexOf("main", titleStartIndex); titleStartIndex > 0 && titleContentIndex > 0 && titleContentIndex - titleStartIndex < 10 && (window.Persistence = new Persistence_windowKey("qt")) } }

var UA = navigator.userAgent;

var isMobile = /Android/i.test(UA);
var isAndroidWebview = /wv/i.test(UA);

if (isMobile && isAndroidWebview) {
    progressBarInit();
} else {
    progressBarHide();
}

function progressBarHide() {
    document.getElementById("h-progress").style.display = "none";
    document.getElementById("h-bar").style.display = "none";
}

function progressBarInit() {
    document.body.insertBefore(progressDiv, document.body.firstChild);
    var cardCount = parseInt(AnkiDroidJS.ankiGetNewCardCount()) + parseInt(AnkiDroidJS.ankiGetLrnCardCount()) + parseInt(AnkiDroidJS.ankiGetRevCardCount());

    var totalCardCount = 1;
    if (Persistence.isAvailable()) {
        totalCardCount = Persistence.getItem("total");
        if (totalCardCount == null) {
            totalCardCount = cardCount;    // count set to total card count at first card, it will not change for current session
            Persistence.setItem("total", totalCardCount);
        }
    }

    // progress bar percentage
    var per = Math.trunc(100 - cardCount * 100 / totalCardCount) + "%";
    document.getElementById("h-bar").style.width = per;
}

var progressBarCSS = `
.progress-1 {
width: 100%;
border-radius: 2px;
background-color: #e6e6e6;
}

.progress-bar-1 {
height: 12px;
border-radius: 2px;
background-color: limegreen;
}
`;

var styleSheet = document.createElement("style");
styleSheet.type = "text/css";
styleSheet.innerText = progressBarCSS;
document.head.appendChild(styleSheet);