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
import org.apache.spark.sql.connector.catalog.{Table, TableProvider}
import org.apache.spark.sql.connector.expressions.Transform
import org.apache.spark.sql.sources.DataSourceRegister
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.util.CaseInsensitiveStringMap
import org.apache.yetus.audience.InterfaceAudience
import scala.jdk.CollectionConverters._

/**
 * This is a new class in the spark4 module, and is the entry point for sparkSQL in spark 4 DataSource V2.
 * It's the equivalent to what DefaultSource (a RelationProvider) was in spark 3 DataSource V1.
 *
 * Implements DS V2 TableProvider and DataSourceRegister, so when you call:
 * <code>
 *  ...
 *    spark.read.format("org.apache.hadoop.hbase.spark.datasources.HBaseTableProvider")
 *  ...
 * </code>
 *
 * Spark instantiates this class and calls its getTable() method. In V1, DefaultSource.createRelation() returned a
 * BaseRelation directly, but now in V2, it returns an HBaseTable object that describes the table's capabilities.
 *
 * Alternatively, it overrides shortName() to provide a short name "hbase" for this data source, so you can also call:
 * <code>
 *  ...
 *    spark.read.format("hbase")
 *  ...
 * </code>
 *
 *
 */
@InterfaceAudience.Public
class HBaseTableProvider extends TableProvider with DataSourceRegister {

  /**
   * Short name of the data source, used to allow users to specify format("hbase")
   * as a short name for format("org.apache.hadoop.hbase.spark.datasources.HBaseTableProvider").
   * @return
   */
  override def shortName(): String = "hbase"

  override def inferSchema(options: CaseInsensitiveStringMap): StructType = {
    val params = options.asCaseSensitiveMap().asScala.toMap
    HBaseTableCatalog(params).toDataType
  }

  override def getTable(
      schema: StructType,
      partitioning: Array[Transform],
      properties: util.Map[String, String]): Table = {
    val params = properties.asScala.toMap
    new HBaseTable(schema, params)
  }
}
