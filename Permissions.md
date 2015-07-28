# Permissions

This page contains a list of the permissions required by AnkiDroid and an explanation of what they are used for.

You can see the most up-to-date list of permission required by AnkiDroid and their description in the Google Play Store:

https://play.google.com/store/apps/details?id=com.ichi2.anki


## NETWORK COMMUNICATION


### FULL INTERNET ACCESS

_Allows the app to create network sockets._

**android.permission.INTERNET**

AnkiDroid accesses the internet to download shared decks, synchronize your existing decks, upload errors, and check for notifications.


### CREATE BLUETOOTH CONNECTIONS

_Allows the app to view the configuration of the local Bluetooth tablet, and to make and accept connections with paired devices. Allows the app to view the configuration of the local Bluetooth phone, and to make and accept connections with paired devices._

**android.permission.BLUETOOTH**

AnkiDroid can be controlled using a [Zeemote](Zeemote.md) via Bluetooth.


## STORAGE


### MODIFY/DELETE USB STORAGE CONTENTS MODIFY/DELETE SD CARD CONTENTS

_Allows the app to write to the USB storage. Allows the app to write to the SD card._

**android.permission.WRITE\*EXTERNAL\*STORAGE**

AnkiDroid used the SD card of your device to store the decks you are reviewing.


## SYSTEM TOOLS


### BLUETOOTH ADMINISTRATION

_Allows the app to configure the local Bluetooth tablet, and to discover and pair with remote devices. Allows the app to configure the local Bluetooth phone, and to discover and pair with remote devices._

**android.permission.BLUETOOTH\*ADMIN**

AnkiDroid can configure a [Zeemote](Zeemote.md) via Bluetooth.


## HARDWARE CONTROLS


### CONTROL VIBRATOR

_Allows the app to control the vibrator._

**android.permission.VIBRATE**

AnkiDroid makes your phone vibrate when you have cards due.


## NETWORK COMMUNICATION


### VIEW NETWORK STATE

_Allows the app to view the state of all networks._

**android.permission.ACCESS\*NETWORK\*STATE**

AnkiDroid uses the network state to determine whether you are offline and cannot access the features that require internet access.


## SYSTEM TOOLS


### AUTOMATICALLY START AT BOOT

_Allows the app to have itself started as soon as the system has finished booting. This can make it take longer to start the tablet and allow the app to slow down the overall tablet by always running. Allows the app to have itself started as soon as the system has finished booting. This can make it take longer to start the phone and allow the app to slow down the overall phone by always running._

**android.permission.RECEIVE\*BOOT\*COMPLETED**

AnkiDroid is started on device boot so that it can check if you have any cards due and let you know.