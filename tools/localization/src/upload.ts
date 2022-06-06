import fs from "fs";
import path from "path";
import crowdin, { Credentials } from '@crowdin/crowdin-api-client';

require('dotenv').config({path: path.join(__dirname, "../.env")});

const CROWDIN_API_KEY = process.env.CROWDIN_API_KEY ?? "";
const projectId = 720;

// credentials
const credentials: Credentials = {
    token: CROWDIN_API_KEY
};

// initialization of crowdin client
const {
    uploadStorageApi,
    sourceFilesApi
} = new crowdin(credentials);

const I18N_FILE_BASE = "../../AnkiDroid/src/main/res/values/"
const I18N_FILES = [
    '01-core',
    '02-strings',
    '03-dialogs',
    '04-network',
    '05-feedback',
    '06-statistics',
    '07-cardbrowser',
    '08-widget',
    '09-backup',
    '10-preferences',
    '11-arrays',
    '12-dont-translate',
    '14-marketdescription',
    '16-multimedia-editor',
    '17-model-manager',
    '18-standard-models'
];

export async function uploadI18nFiles() {
    const files = await sourceFilesApi.listProjectFiles(projectId);

    try {
        for (let file of I18N_FILES) {
            let I18N_FILE_TARGET_NAME = `${file}.xml`;
            let I18N_FILE_SOURCE_NAME = `${I18N_FILE_BASE}${I18N_FILE_TARGET_NAME}`;

            // console.log(I18N_FILE_TARGET_NAME);
            // console.log(I18N_FILE_SOURCE_NAME);

            if (file == '14-marketdescription') {
                I18N_FILE_TARGET_NAME = "14-marketdescription.txt";
                I18N_FILE_SOURCE_NAME = "../../docs/marketing/localized_description/marketdescription.txt";
            }

            if (`${I18N_FILE_TARGET_NAME}` != "") {
                console.log(`Update of Main File ${I18N_FILE_TARGET_NAME} from ${I18N_FILE_SOURCE_NAME}`);

                if (fs.existsSync(I18N_FILE_SOURCE_NAME)) {
                
                    fs.readFile(I18N_FILE_SOURCE_NAME, {encoding: 'utf-8'}, function(err, data){
                        if (!err) {   
                            
                            // if exists then update, else create new file
                            let id = checkIfExistInCrowdin(I18N_FILE_TARGET_NAME, files);
                            if (id) {
                                updateFile(id, I18N_FILE_TARGET_NAME, data);
                            } else {
                                createFile(I18N_FILE_TARGET_NAME, data);
                            }

                        } else {
                            console.log(err);
                        }
                    });
                    
                } else {
                    throw `File not exist ${I18N_FILE_SOURCE_NAME}`
                }
            }
        }
    } catch (error) {
        console.error(error);
    }
}


// Create file with xml content to translate
async function createFile(fileName: string, fileData: any) {
    const storage = await uploadStorageApi.addStorage(fileName, fileData);
    const file = await sourceFilesApi.createFile(projectId, {
        name: fileName,
        title: fileName,
        storageId: storage.data.id,
        type: "auto"
    });
    console.log(file, storage.data.id);
}


// Update file with txt, xml content to translate
async function updateFile(id: number, fileName: string, fileData: any) {
    const storage = await uploadStorageApi.addStorage(fileName, fileData);
    const file = await sourceFilesApi.updateOrRestoreFile(projectId, id, {
        storageId: storage.data.id
    });
    console.log(file, storage.data.id);
}


// check if file exists on crowdin, if exist then return id
function checkIfExistInCrowdin(fileName: string, files: any) {
    for (let file of files.data) {
        if (file.data.name === fileName) {
            return file.data.id;
        }
    }
    return null;
}
