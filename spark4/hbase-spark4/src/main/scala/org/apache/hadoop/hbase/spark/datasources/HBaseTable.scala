/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.spark.datasources

import java.util
import org.apache.spark.sql.connector.catalog.{SupportsRead, SupportsWrite, Table, TableCapability}
import org.apache.spark.sql.connector.read.ScanBuilder
import org.apache.spark.sql.connector.write.{LogicalWriteInfo, WriteBuilder}
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.util.CaseInsensitiveStringMap
import org.apache.yetus.audience.InterfaceAudience
import scala.jdk.CollectionConverters._

/**
 * This is a new class in the spark4 module. Implements Table, SupportsRead, and SupportsWrite.
 * Represents the HBase table as a Spark entity. Declares its schema (from the catalog) and
 * capabilities (BATCH_READ, BATCH_WRITE).
 * When Spark wants to read, it calls newScanBuilder(). When Spark wants to write, it calls newWriteBuilder().
 * In the spark 3 DS V1 model, there was no separate "table" concept, BaseRelation bundled schema and
 * read/write logic together.
 * @param tableSchema
 * @param properties
 */
@InterfaceAudience.Private
class HBaseTable(tableSchema: StructType, properties: Map[String, String])
    extends Table
    with SupportsRead
    with SupportsWrite {

  override def name(): String = {
    val catalog = HBaseTableCatalog(properties)
    s"${catalog.namespace}:${catalog.name}"
  }

  override def schema(): StructType = tableSchema

  override def capabilities(): util.Set[TableCapability] = {
    Set(TableCapability.BATCH_READ, TableCapability.BATCH_WRITE).asJava
  }

  override def newScanBuilder(options: CaseInsensitiveStringMap): ScanBuilder = {
    val mergedProps = properties ++ options.asScala.toMap
    new HBaseScanBuilder(tableSchema, mergedProps)
  }

  override def newWriteBuilder(info: LogicalWriteInfo): WriteBuilder = {
    val mergedProps = properties ++ info.options().asScala.toMap
    new HBaseWriteBuilder(info.schema(), mergedProps)
  }
}
