/*
 * AnkiDroid JavaScript API
 * Version: 0.0.2
 */

/**
 * jsApiList
 *
 * name: method name
 * value: endpoint
 */
const jsApiList = {
    ankiGetNewCardCount: "newCardCount",
    ankiGetLrnCardCount: "lrnCardCount",
    ankiGetRevCardCount: "revCardCount",
    ankiGetETA: "eta",
    ankiGetCardMark: "cardMark",
    ankiGetCardFlag: "cardFlag",
    ankiGetNextTime1: "nextTime1",
    ankiGetNextTime2: "nextTime2",
    ankiGetNextTime3: "nextTime3",
    ankiGetNextTime4: "nextTime4",
    ankiGetCardReps: "cardReps",
    ankiGetCardInterval: "cardInterval",
    ankiGetCardFactor: "cardFactor",
    ankiGetCardMod: "cardMod",
    ankiGetCardId: "cardId",
    ankiGetCardNid: "cardNid",
    ankiGetCardType: "cardType",
    ankiGetCardDid: "cardDid",
    ankiGetCardLeft: "cardLeft",
    ankiGetCardODid: "cardODid",
    ankiGetCardODue: "cardODue",
    ankiGetCardQueue: "cardQueue",
    ankiGetCardLapses: "cardLapses",
    ankiGetCardDue: "cardDue",
    ankiIsInFullscreen: "isInFullscreen",
    ankiIsTopbarShown: "isTopbarShown",
    ankiIsInNightMode: "isInNightMode",
    ankiIsDisplayingAnswer: "isDisplayingAnswer",
    ankiGetDeckName: "deckName",
    ankiIsActiveNetworkMetered: "isActiveNetworkMetered",
    ankiTtsFieldModifierIsAvailable: "ttsFieldModifierIsAvailable",
    ankiTtsIsSpeaking: "ttsIsSpeaking",
    ankiTtsStop: "ttsStop",
    ankiBuryCard: "buryCard",
    ankiBuryNote: "buryNote",
    ankiSuspendCard: "suspendCard",
    ankiSuspendNote: "suspendNote",
    ankiAddTagToCard: "addTagToCard",
    ankiResetProgress: "resetProgress",
    ankiMarkCard: "markCard",
    ankiToggleFlag: "toggleFlag",
    ankiSearchCard: "searchCard",
    ankiSearchCardWithCallback: "searchCardWithCallback",
    ankiTtsSpeak: "ttsSpeak",
    ankiTtsSetLanguage: "ttsSetLanguage",
    ankiTtsSetPitch: "ttsSetPitch",
    ankiTtsSetSpeechRate: "ttsSetSpeechRate",
    ankiEnableHorizontalScrollbar: "enableHorizontalScrollbar",
    ankiEnableVerticalScrollbar: "enableVerticalScrollbar",
    ankiSetCardDue: "setCardDue",
};

class AnkiDroidJS {
    constructor({ developer, version }) {
        this.developer = developer;
        this.version = version;
        this.init({ developer, version });
    }

    static init({ developer, version }) {
        return new AnkiDroidJS({ developer, version });
    }

    async init({ developer, version }) {
        this.developer = developer;
        this.version = version;
        return await this.handleRequest(`init`);
    }

    handleRequest = async (endpoint, data) => {
        if (!this.developer || !this.version) {
            throw new Error("You must initialize API before using other JS API");
        }

        const url = `/jsapi/${endpoint}`;
        try {
            const response = await fetch(url, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({
                    developer: this.developer,
                    version: this.version,
                    data,
                }),
            });

            if (!response.ok) {
                throw new Error("Failed to make the request");
            }

            const responseData = await response.text();
            if (endpoint.includes("nextTime") || endpoint.includes("deckName")) {
                return responseData;
            }
            return JSON.parse(responseData);
        } catch (error) {
            console.error("Request error:", error);
            throw error;
        }
    };
}

Object.keys(jsApiList).forEach(method => {
    if (method === "ankiTtsSpeak") {
        AnkiDroidJS.prototype[method] = async function (text, queueMode = 0) {
            if (this.version < "0.0.2") {
                throw new Error("You must update AnkiDroid JS API version.");
            }
            const endpoint = jsApiList[method];
            const data = JSON.stringify({ text, queueMode });
            return await this.handleRequest(endpoint, data);
        };
        return;
    }
    AnkiDroidJS.prototype[method] = async function (data) {
        if (this.version < "0.0.2") {
            throw new Error("You must update AnkiDroid JS API version.");
        }
        const endpoint = jsApiList[method];
        return await this.handleRequest(endpoint, data);
    };
});
