# Permissions

This page contains a list of the permissions required by AnkiDroid and an explanation of what they are used for.

You can see the most up-to-date list of permission required by AnkiDroid and their description in the Google Play Store:

https://play.google.com/store/apps/details?id=com.ichi2.anki

## Photos / Media / Files & Storage

### Read the contents of your USB storage & Modify or delete the contents of your USB storage

_Allows the app to write to the USB storage. Allows the app to write to the SD card._

**android.permission.WRITE_EXTERNAL_STORAGE**

AnkiDroid used the SD card of your device to store the decks you are reviewing.

## Microphone

### Record audio

**android.permission.RECORD_AUDIO**

This is an optional permission that we request, allowing the "Check Pronunciation" and "Insert Audio" features to work.
 
## Camera

### Take pictures and videos
**android.permission.CAMERA**

This is an optional permission that we request, allowing images to be inserted into flashcards from the camera.


## NETWORK COMMUNICATION


### FULL INTERNET ACCESS

_Allows the app to create network sockets._

**android.permission.INTERNET**

AnkiDroid accesses the internet to download shared decks, synchronize your existing decks, upload errors, and check for notifications.

## SYSTEM TOOLS

## HARDWARE CONTROLS


### CONTROL VIBRATOR

_Allows the app to control the vibrator._

**android.permission.VIBRATE**

AnkiDroid makes your phone vibrate when you have cards due.


## NETWORK COMMUNICATION


### VIEW NETWORK STATE

_Allows the app to view the state of all networks._

**android.permission.ACCESS_NETWORK_STATE**

AnkiDroid uses the network state to determine whether you are offline and cannot access the features that require internet access.

### Full Network Access

**android.permission.INTERNET**

AnkiDroid uses the internet to connect to AnkiWeb.  
Decks may reference images and media via the internet.  
AnkiDroid's opt-in analytics use the internet.  
AnkiDroid's opt-in crash reporting uses the internet.  

## SYSTEM TOOLS

### AUTOMATICALLY START AT BOOT

_Allows the app to have itself started as soon as the system has finished booting. This can make it take longer to start the tablet and allow the app to slow down the overall tablet by always running. Allows the app to have itself started as soon as the system has finished booting. This can make it take longer to start the phone and allow the app to slow down the overall phone by always running._

**android.permission.RECEIVE_BOOT_COMPLETED**

AnkiDroid is started on device boot so that it can check if you have any cards due and let you know via notifications.


### Prevent device from sleeping

**android.permission.WAKE_LOCK**

We prevent the application from sleeping while a sync is occurring. This is used to ensure we do not have timing conflicts with AnkiWeb when the application is resumed and paused.