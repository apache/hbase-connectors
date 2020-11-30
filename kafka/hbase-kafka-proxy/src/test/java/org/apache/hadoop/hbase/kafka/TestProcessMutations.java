/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.kafka;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Row;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.testclassification.SmallTests;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Test that mutations are getting published to the topic
 */
@Category(SmallTests.class)
public class TestProcessMutations {
  private static final String ROUTE_RULE1 =
      "<rules><rule action=\"route\" table=\"MyNamespace:MyTable\" "
          + "topic=\"foo\"/></rules>";

  ProducerForTesting myTestingProducer;

  @Before
  public void setup() {
    this.myTestingProducer=new ProducerForTesting();
  }

  @Test
  public void testSendMessage() {
    TopicRoutingRules rules = new TopicRoutingRules();
    try {
      rules.parseRules(new ByteArrayInputStream(ROUTE_RULE1.getBytes(StandardCharsets.UTF_8)));
      Configuration conf = new Configuration();
      KafkaBridgeConnection connection = new KafkaBridgeConnection(conf,rules,myTestingProducer);
      long zeTimestamp = System.currentTimeMillis();
      Put put = new Put("key1".getBytes(StandardCharsets.UTF_8),zeTimestamp);
      put.addColumn("FAMILY".getBytes(StandardCharsets.UTF_8),
              "not foo".getBytes(StandardCharsets.UTF_8),
              "VALUE should NOT pass".getBytes(StandardCharsets.UTF_8));
      put.addColumn("FAMILY".getBytes(StandardCharsets.UTF_8),
              "foo".getBytes(StandardCharsets.UTF_8),
              "VALUE should pass".getBytes(StandardCharsets.UTF_8));
      Table myTable = connection.getTable(TableName.valueOf("MyNamespace:MyTable"));
      List<Row> rows = new ArrayList<>();
      rows.add(put);
      myTable.batch(rows,new Object[0]);

      Assert.assertFalse(myTestingProducer.getMessages().isEmpty());
    } catch (Exception e){
      Assert.fail(e.getMessage());
    }
  }
}
