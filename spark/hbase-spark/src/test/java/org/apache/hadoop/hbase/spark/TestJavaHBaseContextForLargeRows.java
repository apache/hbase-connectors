/**
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
package org.apache.hadoop.hbase.spark;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.MiscTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.spark.api.java.JavaSparkContext;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.experimental.categories.Category;

@Category({ MiscTests.class, MediumTests.class })
public class TestJavaHBaseContextForLargeRows extends TestJavaHBaseContext {

  @ClassRule public static final HBaseClassTestRule TIMEOUT =
      HBaseClassTestRule.forClass(TestJavaHBaseContextForLargeRows.class);

  @BeforeClass public static void setUpBeforeClass() throws Exception {
    JSC = new JavaSparkContext("local", "JavaHBaseContextSuite");

    init();
  }

  protected void populateTableWithMockData(Configuration conf, TableName tableName)
      throws IOException {
    try (Connection conn = ConnectionFactory.createConnection(conf);
        Table table = conn.getTable(tableName);
        Admin admin = conn.getAdmin()) {

      List<Put> puts = new ArrayList<>(5);

      for (int i = 1; i < 6; i++) {
        Put put = new Put(Bytes.toBytes(Integer.toString(i)));
        // We are trying to generate a large row value here
        char[] chars = new char[1024 * 1024 * 2];
        // adding '0' to convert int to char
        Arrays.fill(chars, (char) (i + '0'));
        put.addColumn(columnFamily, columnFamily, Bytes.toBytes(String.valueOf(chars)));
        puts.add(put);
      }
      table.put(puts);
      admin.flush(tableName);
    }
  }
}
