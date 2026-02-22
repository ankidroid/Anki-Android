This API provides a powerful, type-safe JavaScript/TypeScript interface for interacting with the AnkiDroid application from within its webview. It enables developers to create rich, dynamic, and custom study experiences by exposing core AnkiDroid functionalities.

The API is built on a **Remote Procedure Call (RPC)** architecture. All operations are asynchronous and communicate with the AnkiDroid backend by dispatching requests to specific, named endpoints. This design ensures a clear and robust bridge between your JavaScript code and the underlying AnkiDroid collection and study state.

## Core Features

* **Study Screen Interaction**: Control the study session by showing the answer, answering cards with a specific rating (Again, Hard, Good, Easy), and retrieving the counts for new, learning, and review cards.

* **Card, Note, and Deck Management**: Access detailed information about the current card, note, and deck. Perform actions like burying, suspending, toggling flags, and modifying note tags directly from your JavaScript code.

* **Android System Integration**: Query device-level information, such as whether the system is in dark mode or if the network connection is metered, allowing your custom UI to adapt to the user's environment.

* **Type-Safe by Default**: Written entirely in TypeScript, the API provides full type definitions, enabling autocompletion and compile-time error checking for a more reliable development experience.

## Contributing

You can contribute by submitting code, discussing, or [donating](https://opencollective.com/ankidroid).

## License

* Licensed under the [Apache-2.0](LICENSE) license.
