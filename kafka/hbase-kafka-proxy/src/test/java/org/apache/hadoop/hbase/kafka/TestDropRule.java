/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.hadoop.hbase.kafka;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.testclassification.SmallTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Test different cases of drop rules.
 */

@Category(SmallTests.class)
public class TestDropRule {
  private static final String DROP_RULE1 =
      "<rules><rule action=\"drop\" table=\"default:MyTable\" /></rules>";
  private static final String DROP_RULE2 =
      "<rules><rule action=\"drop\" table=\"default:MyTable\" "
      + "columnFamily=\"data\"/></rules>";
  private static final String DROP_RULE3 =
      "<rules><rule action=\"drop\" table=\"default:MyTable\" "
      + "columnFamily=\"data\" qualifier=\"dhold\"/></rules>";

  private static final String DROP_RULE4 =
      "<rules><rule action=\"drop\" table=\"default:MyTable\" "
      + "columnFamily=\"data\" qualifier=\"dhold:*\"/></rules>";
  private static final String DROP_RULE5 =
      "<rules><rule action=\"drop\" table=\"default:MyTable\" "
      + "columnFamily=\"data\" qualifier=\"*pickme\"/></rules>";

  private static final String DROP_RULE6 =
      "<rules><rule action=\"drop\" table=\"default:MyTable\" "
      + "columnFamily=\"data\" qualifier=\"*pickme*\"/></rules>";

  @Test
  public void testDropies1() {
    TopicRoutingRules rules = new TopicRoutingRules();
    try {
      rules.parseRules(new ByteArrayInputStream(DROP_RULE1.getBytes(StandardCharsets.UTF_8)));
      Assert.assertEquals(1, rules.getDropRules().size());
      Assert.assertEquals(TableName.valueOf("default:MyTable"),
        rules.getDropRules().get(0).getTableName());
      Assert.assertNull(rules.getDropRules().get(0).getColumnFamily());
      Assert.assertNull(rules.getDropRules().get(0).getQualifier());
      Assert.assertEquals(0, rules.getRouteRules().size());
    } catch (Exception e) {
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void testDropies2() {
    TopicRoutingRules rules = new TopicRoutingRules();
    try {
      rules.parseRules(new ByteArrayInputStream(DROP_RULE2.getBytes(StandardCharsets.UTF_8)));
      Assert.assertEquals(1, rules.getDropRules().size());
      Assert.assertEquals(TableName.valueOf("default:MyTable"),
        rules.getDropRules().get(0).getTableName());
      Assert.assertTrue(Bytes.equals("data".getBytes(StandardCharsets.UTF_8),
              rules.getDropRules().get(0).getColumnFamily()));
      Assert.assertNull(rules.getDropRules().get(0).getQualifier());
      Assert.assertEquals(0, rules.getRouteRules().size());
    } catch (Exception e) {
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void testDropies3() {
    TopicRoutingRules rules = new TopicRoutingRules();
    try {
      rules.parseRules(new ByteArrayInputStream(DROP_RULE3.getBytes(StandardCharsets.UTF_8)));
      Assert.assertEquals(1, rules.getDropRules().size());
      Assert.assertEquals(TableName.valueOf("default:MyTable"),
        rules.getDropRules().get(0).getTableName());
      Assert.assertTrue(Bytes.equals("data".getBytes(StandardCharsets.UTF_8),
              rules.getDropRules().get(0).getColumnFamily()));
      Assert.assertTrue(Bytes.equals("dhold".getBytes(StandardCharsets.UTF_8),
              rules.getDropRules().get(0).getQualifier()));
      Assert.assertEquals(0, rules.getRouteRules().size());
    } catch (Exception e) {
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void testDropies4() {
    TopicRoutingRules rules = new TopicRoutingRules();
    try {
      rules.parseRules(new ByteArrayInputStream(DROP_RULE4.getBytes(StandardCharsets.UTF_8)));
      Assert.assertEquals(1, rules.getDropRules().size());
      Assert.assertEquals(TableName.valueOf("default:MyTable"),
        rules.getDropRules().get(0).getTableName());
      Assert.assertTrue(Bytes.equals("data".getBytes(StandardCharsets.UTF_8),
              rules.getDropRules().get(0).getColumnFamily()));
      Assert.assertTrue(Bytes.equals("dhold:".getBytes(StandardCharsets.UTF_8),
              rules.getDropRules().get(0).getQualifier()));
      Assert.assertEquals(0, rules.getRouteRules().size());

      DropRule drop = rules.getDropRules().get(0);
      Assert.assertFalse(drop.match(TableName.valueOf("default:MyTable"),
              "data".getBytes(StandardCharsets.UTF_8), "blah".getBytes(StandardCharsets.UTF_8)));
      Assert.assertFalse(drop.match(TableName.valueOf("default:MyTable"),
              "data".getBytes(StandardCharsets.UTF_8), "dholdme".getBytes(StandardCharsets.UTF_8)));
      Assert.assertTrue(drop.match(TableName.valueOf("default:MyTable"),
              "data".getBytes(StandardCharsets.UTF_8),
              "dhold:me".getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void testDropies5() {
    TopicRoutingRules rules = new TopicRoutingRules();
    try {
      rules.parseRules(new ByteArrayInputStream(DROP_RULE5.getBytes(StandardCharsets.UTF_8)));
      Assert.assertEquals(1, rules.getDropRules().size());
      Assert.assertEquals(TableName.valueOf("default:MyTable"),
        rules.getDropRules().get(0).getTableName());
      Assert.assertTrue(Bytes.equals("data".getBytes(StandardCharsets.UTF_8),
              rules.getDropRules().get(0).getColumnFamily()));
      Assert.assertTrue(Bytes.equals("pickme".getBytes(StandardCharsets.UTF_8),
              rules.getDropRules().get(0).getQualifier()));
      Assert.assertEquals(0, rules.getRouteRules().size());

      DropRule drop = rules.getDropRules().get(0);
      Assert.assertFalse(drop.match(TableName.valueOf("default:MyTable"),
              "data".getBytes(StandardCharsets.UTF_8), "blah".getBytes(StandardCharsets.UTF_8)));
      Assert.assertFalse(drop.match(TableName.valueOf("default:MyTable"),
              "data".getBytes(StandardCharsets.UTF_8),
              "blacickme".getBytes(StandardCharsets.UTF_8)));
      Assert.assertTrue(drop.match(TableName.valueOf("default:MyTable"),
              "data".getBytes(StandardCharsets.UTF_8),
              "hithere.pickme".getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void testDropies6() {
    TopicRoutingRules rules = new TopicRoutingRules();
    try {
      rules.parseRules(new ByteArrayInputStream(DROP_RULE6.getBytes(StandardCharsets.UTF_8)));
      Assert.assertEquals(1, rules.getDropRules().size());
      Assert.assertEquals(TableName.valueOf("default:MyTable"),
              rules.getDropRules().get(0).getTableName());
      Assert.assertTrue(Bytes.equals("data".getBytes(StandardCharsets.UTF_8),
              rules.getDropRules().get(0).getColumnFamily()));
      Assert.assertTrue(Bytes.equals("pickme".getBytes(StandardCharsets.UTF_8),
              rules.getDropRules().get(0).getQualifier()));
      Assert.assertEquals(0, rules.getRouteRules().size());

      DropRule drop = rules.getDropRules().get(0);
      Assert.assertFalse(drop.match(TableName.valueOf("default:MyTable"),
              "data".getBytes(StandardCharsets.UTF_8), "blah".getBytes(StandardCharsets.UTF_8)));
      Assert.assertFalse(drop.match(TableName.valueOf("default:MyTable"),
              "data".getBytes(StandardCharsets.UTF_8),
              "blacickme".getBytes(StandardCharsets.UTF_8)));
      Assert.assertTrue(drop.match(TableName.valueOf("default:MyTable"),
              "data".getBytes(StandardCharsets.UTF_8),
              "hithere.pickme".getBytes(StandardCharsets.UTF_8)));
      Assert.assertTrue(drop.match(TableName.valueOf("default:MyTable"),
              "data".getBytes(StandardCharsets.UTF_8),
              "pickme.pleaze.do.it".getBytes(StandardCharsets.UTF_8)));
      Assert.assertFalse(drop.match(TableName.valueOf("default:MyTable"),
              "data".getBytes(StandardCharsets.UTF_8),
              "please.pickme.pleaze".getBytes(StandardCharsets.UTF_8)));
      Assert.assertTrue(drop.match(TableName.valueOf("default:MyTable"),
              "data".getBytes(StandardCharsets.UTF_8),
              "pickme.pleaze.pickme".getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      Assert.fail(e.getMessage());
    }
  }
}
