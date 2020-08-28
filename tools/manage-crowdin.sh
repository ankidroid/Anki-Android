#/bin/bash

# Copyright (c) 2020 Mike Hardy <github@mikehardy.net>
#
# This program is free software; you can redistribute it and/or modify it under
# the terms of the GNU General Public License as published by the Free Software
# Foundation; either version 3 of the License, or (at your option) any later
# version.
#
# This program is distributed in the hope that it will be useful, but WITHOUT ANY
# WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
# PARTICULAR PURPOSE. See the GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License along with
# this program.  If not, see <http://www.gnu.org/licenses/>.
#

# This script updates the master file(s) for crowdin.net

CROWDIN_KEY_FILE="${HOME}/src/crowdin_key.txt"
if [ "${CROWDIN_KEY}" == "" ] && ! [ -f "${CROWDIN_KEY_FILE}" ]; then
  echo "API key must be set as CROWDIN_KEY or in ${CROWDIN_KEY_FILE}"
  exit 1
fi

if [ "${CROWDIN_KEY}" == "" ]; then
  CROWDIN_KEY=$(cat "${CROWDIN_KEY_FILE}")
fi

PROJECT_IDENTIFIER="ankidroid"
I18N_FILE_BASE="./AnkiDroid/src/main/res/values/"

declare -a I18N_FILES=(
  '01-core'
  '02-strings'
  '03-dialogs'
  '04-network'
  '05-feedback'
  '06-statistics'
  '07-cardbrowser'
  '08-widget'
  '09-backup'
  '10-preferences'
  '11-arrays'
  '12-dont-translate'
  '14-marketdescription'
  '16-multimedia-editor'
  '17-model-manager'
  '18-standard-models'
);

for i in "${I18N_FILES[@]}"; do
  echo "$i"
  I18N_FILE_TARGET_NAME="${i}.xml"
  I18N_FILE_SOURCE_NAME="${I18N_FILE_BASE}${I18N_FILE_TARGET_NAME}"

  if [ "$i" == "14-marketdescription" ]; then
    I18N_FILE_TARGET_NAME="14-marketdescription.txt"
    I18N_FILE_SOURCE_NAME="./docs/marketing/localized_description/marketdescription.txt"
  fi

  if [ "$I18N_FILE_TARGET_NAME" != "" ]; then  
    echo "Update of Master File ${I18N_FILE_TARGET_NAME} from ${I18N_FILE_SOURCE_NAME}"

    echo "FILE arg is -F \"files[${I18N_FILE_TARGET_NAME}]=@${I18N_FILE_SOURCE_NAME}\" "
    curl \
      -F "files[${I18N_FILE_TARGET_NAME}]=@${I18N_FILE_SOURCE_NAME}" \
      -F "update_option=update_without_changes" \
      https://api.crowdin.com/api/project/${PROJECT_IDENTIFIER}/update-file?key=${CROWDIN_KEY}
  fi
done
