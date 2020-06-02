<h4 align="center">Kotlin Metadata Printer</h4>

<!-- Badges -->
<p align="center">
  <!-- CI -->
  <!--a href="https://github.com/Guardsquare/kotlin-metadata-printer/actions?query=workflow%3A%22Continuous+Integration%22">
    <img src="https://github.com/Guardsquare/kotlin-metadata-printer/workflows/Continuous%20Integration/badge.svg?branch=github-workflow">
  </a-->>

  <!-- Github version -->
  <!--a href="releases">
    <img src="https://img.shields.io/github/v/release/guardsquare/kotlin-metadata-printer">
  </a-->

  <!-- Maven -->
  <!--a href="https://search.maven.org/search?q=g:com.guardsquare">
    <img src="https://img.shields.io/maven-central/v/com.guardsquare/kotlin-metadata-printer">
  </a-->

  <!-- License -->
  <a href="LICENSE">
    <img src="https://img.shields.io/github/license/guardsquare/kotlin-metadata-printer">
  </a>

  <!-- Twitter -->
  <a href="https://twitter.com/Guardsquare">
    <img src="https://img.shields.io/twitter/follow/guardsquare?style=social">
  </a>
</p>

The Kotlin metadata printer is a free tool to print the Kotlin metadata in
a human-readable format. The printer is built on the
[ProGuardCORE](https://github.com/Guardsquare/proguard-core) library and can
process class files, zip files, jars or apks.

## Dependencies

As ProGuard works only with Java class files the tool uses the free dex2jar library to convert the dex files to
class files for processing, these are included in the libs/ folder. It requires a Java Runtime Environment (JRE 1.8 or higher).

## Building

You can build the Kotlin metadata printer jar by executing the following Gradle command:

    ./gradlew jar

Once built a jar will be created in build/libs/kotlin-metadata-printer.jar

## Executing

You can execute the printer directly through gradle as follows:

    ./gradlew run --args "input.{apk,jar,zip,class}"

Or you can execute the built printer jar as follows:

    java -jar build/libs/kotlin-metadata-printer.jar input.{apk,jar,zip,class}

## Options

    --filter '<classNameFilter>' class name filter e.g. --filter '!android.**,com.mypackage.**'
    --output '<outputFile>'      write output to this file instead of stdout e.g. --output 'myfile.txt'
    --json                       output the metadata in a JSON structure
    --divider                    a string that is printed between each Kotlin metadata

## Example

The following example is a basic Android activity class written in Kotlin:

```kotlin
/**
 * Sample activity that displays "Hello world!".
 */
class HelloWorldActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
```

and this is its associated metadata as printed by the Kotlin metadata printer:

```
/**
* Kotlin class.
* From Java class: com.example.HelloWorldActivity
*/
class HelloWorldActivity : android.support.v7.app.AppCompatActivity {

    // Functions

    protected open fun onCreate(savedInstanceState: android.os.Bundle?) { }

```

## Contributing

The **Kotlin metadata printer** is build on the
[ProGuardCORE](https://github.com/Guardsquare/proguard-core) library.

Contributions, issues and feature requests are welcome in both projects.
Feel free to check the [issues](issues) page and the [contributing
guide](CONTRIBUTING.md) if you would like to contribute.

## License

The **Kotlin metadata printer** is distributed under the terms of
the [Apache License Version 2.0](LICENSE).

Enjoy!

Copyright (c) 2002-2020 Guardsquare NV
