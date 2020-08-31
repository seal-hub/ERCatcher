#!/bin/bash
DSNAME=${1:-BenchERoid}
APKS=$(find Datasets/BenchERoid/NonUAF -name "*.apk" -not -type d)
if [ $DSNAME = BenchERoid ]
then
	for APK in $APKS; do
		./run.sh $APK false
	done
	APKS=$(find Datasets/BenchERoid/UAF -name "*.apk" -not -type d)
elif [ $DSNAME = Curated ]
then
	APKS=$(find Datasets/Curated -name "*.apk" -not -type d)
elif [ $DSNAME = FDroid ]
then
	APKS=$(find Datasets/FDroid -name "*.apk" -not -type d)
else
	echo Please provide the name of dataset "BenchERoid", "Curated", or "FDroid"
	exit
fi
for APK in $APKS; do
	./run.sh $APK
done
