#!/bin/bash

# This attempts to stop all your emulators (on Ubuntu 18.04.01 LTS at least...) nicely
#
# ...then if they don't stop it kills them

killall -9 adb
adb devices -l > /dev/null
sleep 2
adb devices -l > /dev/null
sleep 2

for EMU_ID in `adb devices -l | grep emulator | cut -d' ' -f1`; do
  echo Stopping emulator $EMU_ID...
  adb -s $EMU_ID emu kill
done

sleep 10
for PID in `ps -eo pid,cmd,args |grep emulator|grep Android|grep -v bash|grep -v crash|grep -v grep|cut -d/ -f1`; do
  echo "Stopping emulator with $PID..."
  kill $PID
done
