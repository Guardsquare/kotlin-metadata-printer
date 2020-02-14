Kotlin Metadata Printer
=======================

The Kotlin metadata printer is a free tool to print the Kotlin metadata in a human-readable format. The printer is
built on the ProGuard Core library. The tool can process class files, zip files, jars or apks.

# Dependencies

As ProGuard works only with Java class files the tool uses the free dex2jar library to convert the dex files to
class files for processing, these are included in the libs/ folder.

# Building

You can build the Kotlin metadata printer by executing the following Gradle command:

    ./gradlew jar
    
Once built a jar will be created in build/libs/kotlin-metadata-printer.jar

# Executing

You can execute the printer as follows:

    java -jar build/libs/kotlin-metadata-printer.jar input.{apk,jar,zip,class}
    
# Options

    --filter=<classNameFilter> class name filter
    --output=<outputFile>      write output to this file instead of stdout
    --printclass               print corresponding class
    --printmetadata            print metadata
    --printmodule              print Kotlin module information
