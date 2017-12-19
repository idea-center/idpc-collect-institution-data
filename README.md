# IDEA Data Portal CLI - Collection Report Data for an Institution

This utility provides an example of how to pull report data (the model and the response data) from the IDEA Data Portal using
the API. It is a Groovy-based application that uses Gradle as the build tool.

## Building

To build this project, you will need Gradle installed and have access to the required dependencies (if connected to the
internet, they will be downloaded for you).

You can use the provided Gradle wrapper, which will install the correct version of Gradle, or you can install and use
a local instance of Gradle. To use the Gradle wrapper to build, the following command can be used.
```
./gradlew build
```

### Project Dependencies
This project is implemented with Groovy and uses Gradle as the build tool. Therefore, you need to be sure to install
the following:
* [Git](http://git-scm.com/downloads)
* [Java (version 6+)](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
* [Groovy](http://groovy-lang.org/)
* [Gradle](http://gradle.org/installation) (or use [SDKMAN](http://sdkman.io/) to install Gradle)
* Libraries Used
  * [HTTP Client Framework for Groovy](http://mvnrepository.com/artifact/org.codehaus.groovy.modules.http-builder/http-builder)
  * [Apache Commons CLI](http://mvnrepository.com/artifact/commons-cli/commons-cli)
  * [Google GSON](http://mvnrepository.com/artifact/com.google.code.gson/gson)

## Installing

You can install the application using Gradle as well. You can run the following to install it (relative to the project root)
in build/install/idpc-collect-institution-data
```
./gradlew installDist
```

## Running

Once installed, you can run using the following.
```
cd build/install/idpc-collect-institution-data/bin
./idpc-collect-institution-data -a "TestClient" -k "ABCDEFG1234567890" -iid 1029 -h reststage.ideasystem.org -p 80 --start '2017-01-01' --end '2017-12-30' --directory myData
```
This will collect all of the data for the given institution (ID 1029 is "IDEA Education") starting on/after Jan 1, 2017 and ending on/before Dec 30, 2017. The
JSON data that is pulled will be saved into the given directory.

The following command line parameters are available:

Short | Long             | Required | Default             | Description
------|------------------|----------|---------------------|------------
v     | verbose          | No       | Off                 | Provide verbose output
s     | ssl              | No       | Off                 | Connect via SSL
h     | host             | No       | localhost           | The host that provides the Data Portal API
p     | port             | No       | 8091                | The port on the host that is listening for requests.
b     | basePath         | No       | IDEA-REST-SERVER/v1 | The path on the host.
iid   | institutionID    | No       | 3019                | The institution ID the data is associated with.
a     | app              | Yes      | None                | The application to connect as (credentials).
k     | key              | Yes      | None                | The key to use (credentials).
st    | start            | No       | None                | The earliest start date of the data to retrieve.
en    | end              | No       | None                | The latest start date of data to retrieve.
d     | directory        | No       | None                | The directory to create and then store the data in.
