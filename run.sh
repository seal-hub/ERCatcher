#!/bin/bash

MY_ANDROID_HOME=${ANDROID_JARS:-~/Library/Android/sdk/platforms}
APK=$1
#VERBOSITY=${2:-3}
VERBOSITY=3
ONLY_UAF=${2:-true}
COMP_ISOLATION=true
APKNAME=$(basename "$APK")
APKNAME=${APKNAME%.*}
REPORT_DIR=Report/$APKNAME
mkdir -p $REPORT_DIR
rm -rf $REPORT_DIR/* &> /dev/null
echo Analyzing "$APKNAME" ...
java -Xmx12G -jar build/libs/EventRaceDetection-1.0-SNAPSHOT.jar $MY_ANDROID_HOME "$APK" $REPORT_DIR "$VERBOSITY" $ONLY_UAF $COMP_ISOLATION || exit 1
