Android privacy changes require that AnkiDroid moves its collection & media to a location only accessible by the app. 

We cannot distribute the app on the Play Store if we do not make these changes

* Data is accessible via USB at `Device\Internal shared storage\Android\data\com.ichi2.anki\1`
* [[Third Party Apps]] will need to use the [[AnkiDroid API]] to access & modify AnkiDroid data

## How does this affect data on SD cards?

TODO

## Benefits

Android is much faster at writing files to this new location

* Media synchronization will be much faster
* Deck imports will be much faster

## Downsides

### Before migration

* AnkiDroid will lose access to its previous data location when it is uninstalled
  * If you uninstall AnkiDroid before completing a data migration, you will either need to restore your data from AnkiWeb, or contact us for instructions to manually move the data.

### After migration

* Android's "Clear Data" will clear all AnkiDroid data, rather than just clearing preferences
* Uninstalling the app will give you the option of clearing all data