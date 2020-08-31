# ER Catcher: A Static Analysis Framework for Accurate and Scalable Event-Race Detection in Android (ASE'20)

## Abstract
Android platform provisions a number of sophisticated concurrency mechanisms for the development of apps. The concurrency mechanisms, while powerful, are quite difficult to properly master by mobile developers. In fact, prior studies have shown concurrency issues, such as event-race defects, to be prevalent among real-world Android apps. We propose a flow-, context-, and thread-sensitive static analysis framework, called *ER Catcher*, for detection of event-race defects in Android apps. This repository contains the [appendix](https://github.com/ercatcher/ercatcher/blob/master/ERCatcher_Appendix.pdf) of ER Catcher paper (formal proofs), [the source code](https://github.com/ercatcher/ercatcher/tree/master/src/main/java/com/ercatcher) of ER Catcher, and the [datasets of APKs](https://github.com/ercatcher/ercatcher/tree/master/Datasets) that are used in its evaluation.

## Publication

Navid Salehnamadi, Abdulaziz Alshayban, Iftekhar Ahmed, and Sam Malek, "ER Catcher: A Static Analysis Framework for Accurate and Scalable Event-Race Detection in Android" in 2020 35th IEEE/ACM International Conference on Automated Software Engineering (ASE), 2020

---

## Appendix (Formal Proofs)
You can find the PDF version of proofs about the soundness of Static Vector Clocks [here](https://github.com/ercatcher/ercatcher/blob/master/ERCatcher_Appendix.pdf).

## Detecting Event-Races with ER Catcher
### Setup
Clone this repository:

```
git clone git@github.com:ercatcher/ercatcher.git
cd ercatcher
```
We used three datasets for evaluating ER Catcher. The APKs of [BenchERoid](https://github.com/seal-hub/bencheroid) and Curated are existed in this repository. If you want to evaluate the FDroid dataset, first download `FDroid.zip` from this [link](https://drive.google.com/open?id=16no0yTEKnAz-v22z764zA3yyXzM_N8B6). Then unzip it and move it to the base directory of ER Catcher's repo; alternatively, run the following command:

```
unzip path/to/FDroid.zip -d Datasets

```
### Build and Execute

<!--You can build and execute the framework with or without Docker.

#### With Docker
Just run the following command to build the Docker image ([Docker must be installed on your system](https://docs.docker.com/get-docker/)) :

```
docker build . -t=ercatcher
```
To analyze a single APK located in `path/to/apk_dir/app_name.apk`:

```
docker run -v path/to/apk_dir:/APKs ercatcher ./run.sh app_name.apk
```
To analyze the apps in each dataset, run the following command:

```
docker run ercatcher ./run-dataset.sh dataset_name
```
where `dataset_name` can be `BenchERoid`, `Curated`, or `FDroid`.


### Without Docker
-->
Install Java (version 8) and Android SDK. Then add the path to Android Jars to the environment:

```
export ANDROID_JARS=path/Android/sdk/platforms/
```
Build ER Catcher by running

```
./gradlew clean jar
```
For a single app:

```
./run.sh path/app_name.apk
```
This command analyze the app and report Use-after-Free (UAF) event races. If you are interested in non-UAF bugs, execute `./run.sh path/app.apk false`.


To analyze the apps in each dataset, run the following command:
```
./run-dataset.sh dataset_name
```
where `dataset_name` can be `BenchERoid`, `Curated`, or `FDroid`.

### Report
For each analyzed app (`app_name.apk`), a list of prioritized event-races is produced in `Report/app_name/races.csv`.


## A short guideline for the source code
ER Catcher consists of four packages:

* [Memory](https://github.com/ercatcher/ercatcher/tree/master/src/main/java/com/ercatcher/memory) is responsible for analysis related to identifying memory locations statically, e.g., Alias-Analysis.
* [ConcurrencyAnalysis](https://github.com/ercatcher/ercatcher/tree/master/src/main/java/com/ercatcher/ConcurrencyAnalysis) consists of several subpackages:
	* CSF generates the CSF for each method.
	* C2G is responsible for augmenting methods with CSF and add new detected edges.
	* C3G augments C2G with contextual information (caller site and their thread).

	The rest of the classes in ConcurrencyAnalysis package are shared classes or LibraryCSFGenerator classes (corresponding to external CSF libraries).

* 	[SVC](https://github.com/ercatcher/ercatcher/tree/master/src/main/java/com/ercatcher/svc) is the implementation of static vector clock for Android.
*  [RaceDetector](https://github.com/ercatcher/ercatcher/tree/master/src/main/java/com/ercatcher/RaceDetector) is responsible for detecting, filtering, prioritizing, and reporting event-races.
