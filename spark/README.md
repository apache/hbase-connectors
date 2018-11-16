# Apache HBase&trade; Spark Connector

## Scala and Spark Versions

To generate an artifact for a different [spark version](https://mvnrepository.com/artifact/org.apache.spark/spark-core) and/or [scala version](https://www.scala-lang.org/download/all.html), pass command-line options as follows (changing version numbers appropriately):

```
$ mvn -Dspark.version=2.2.2 -Dscala.version=2.11.7 -Dscala.binary.version=2.11 clean install
```

See above linked spark version to match spark version and supported scala version.
