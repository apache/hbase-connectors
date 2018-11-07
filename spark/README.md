# Apache HBase&trade; Spark Connector

## Scala and Spark Versions

To generate an artifact for a different [spark version](https://mvnrepository.com/artifact/org.apache.spark/spark-core) and/or [scala version](https://www.scala-lang.org/download/all.html), pass command-line options as follows (changing version numbers appropriately):

```
$ mvn -Dspark.version=2.2.2 -Dscala.version=2.11.7 -Dscala.binary.version=2.11 clean install
```

Spark 2.1.x, 2.2.x, 2.3.x, and 2.4.0 work as does scala 2.11.x. Scala 2.12.x and 2.10.x need work.
