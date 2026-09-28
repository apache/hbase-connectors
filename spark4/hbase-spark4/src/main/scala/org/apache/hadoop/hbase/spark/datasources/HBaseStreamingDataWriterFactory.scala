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

import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.connector.write.DataWriter
import org.apache.spark.sql.connector.write.streaming.StreamingDataWriterFactory
import org.apache.spark.sql.types.StructType
import org.apache.yetus.audience.InterfaceAudience

/**
 * This is a new class in the spark4 module. Implements StreamingDataWriterFactory for the DS V2
 * streaming write path. Serialized to executors. Creates one HBaseDataWriter per Spark partition
 * per micro-batch epoch.
 *
 * The epochId is not used because HBase puts are idempotent at the latest version, a retried
 * epoch overwrites the cell with the same value, producing no duplicates. Note that the
 * connector doesn't expose cell timestamps schema mapping, so multi-versioning is out of the scope.
 *
 * @param schema
 * @param properties
 * @param catalog
 * @param wrappedConf
 */
@InterfaceAudience.Private
class HBaseStreamingDataWriterFactory(
    schema: StructType,
    properties: Map[String, String],
    catalog: HBaseTableCatalog,
    wrappedConf: SerializableConfiguration)
    extends StreamingDataWriterFactory
    with Serializable {

  override def createWriter(
      partitionId: Int,
      taskId: Long,
      epochId: Long): DataWriter[InternalRow] = {
    new HBaseDataWriter(schema, properties, catalog, wrappedConf)
  }
}
