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
 * distributed under this License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.spark;

import org.apache.yetus.audience.InterfaceAudience;

/**
 * Column mapping for {@link SparkSQLPushDownFilter} built from datasource catalog rows. Defined in
 * Java so Spark 3 and Spark 4 connector modules can contribute mappings without tying the filter to
 * a single Scala {@code Field} type.
 */
@InterfaceAudience.Private
public interface PushdownMappedField {

    String colName();

    byte[] cfBytes();

    byte[] colBytes();
}
