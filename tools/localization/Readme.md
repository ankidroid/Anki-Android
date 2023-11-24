# Localization Tool

This project provides a localization tool built with Yarn. The tool is located in the `tools/localization` directory.

## Getting Started

Follow these steps to set up and run the localization tool. Navigate to the localization directory and install the required dependencies.

```bash
cd ./tools/localization
yarn
```

## Commands

The following commands are available. Run them from the `./tools/localization` directory:

- **Upload**: Uploads English `res/values` files to Crowdin.
```
yarn start upload
```
- **Build and Download**: Builds and downloads English `res/values` files from Crowdin.
```
yarn start download
```
- **Extract**: Extracts files from `ankidroid.zip`.
```
yarn start extract
```
- **Update**: Updates the files from the extracted `ankidroid.zip` file.
```
yarn start update
```

After building the project, following commands can also be used:
```
node .\dist\index.js upload
node .\dist\index.js download
node .\dist\index.js extract
node .\dist\index.js update
```

## Note

The scripts in the `.ts` files are conversions of Python code to TypeScript, done on a line-by-line basis. This ensures that the functionality of the original Python code is preserved in the TypeScript version.
