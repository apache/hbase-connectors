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

import org.apache.spark.sql.connector.write.{BatchWrite, WriteBuilder}
import org.apache.spark.sql.types.StructType
import org.apache.yetus.audience.InterfaceAudience

/**
 * This is a new class in the spark4 module. Implements WriteBuilder for the DS V2 write path.
 *
 * In the spark 3 DS V1 model, there was no WriteBuilder. The write path was handled by
 * CreatableRelationProvider.createRelation() which called InsertableRelation.insert() directly.
 *
 * @param schema
 * @param properties
 */
@InterfaceAudience.Private
class HBaseWriteBuilder(schema: StructType, properties: Map[String, String])
    extends WriteBuilder {

  override def buildForBatch(): BatchWrite = {
    new HBaseBatchWrite(schema, properties)
  }
}
