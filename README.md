# Kotlin stdlib compatibility test for Jaeger java library

Contains three maven profiles:

* **kotlin-default** - test libraries resolved with standard maven rules
* **kotlin-okhttp** - test *kotlin-stdlib* and *kotlin-stdlib-common* with *okhttp* dependency version 
* **kotlin-okio** - test *kotlin-stdlib* and *kotlin-stdlib-common* with *okio* dependency version

### Usage

`mvn -P kotlin-default clean install`

`mvn -P kotlin-okhttp clean install`

`mvn -P kotlin-okio clean install`

This all should be done without failures.