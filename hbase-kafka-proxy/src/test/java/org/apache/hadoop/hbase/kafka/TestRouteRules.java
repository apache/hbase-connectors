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

import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.testclassification.SmallTests;
import org.apache.hadoop.hbase.util.Bytes;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Test drop rules
 */
@Category(SmallTests.class)
public class TestRouteRules {
  private static final String ROUTE_RULE1 =
      "<rules><rule action=\"route\" table=\"default:MyTable\" "
      + "topic=\"foo\"/></rules>";
  private static final String ROUTE_RULE2 =
      "<rules><rule action=\"route\" table=\"default:MyTable\" "
      + "columnFamily=\"data\" topic=\"foo\"/></rules>";
  private static final String ROUTE_RULE3 =
      "<rules><rule action=\"route\" table=\"default:MyTable\" "
      + "columnFamily=\"data\" qualifier=\"dhold\" topic=\"foo\"/></rules>";

  private static final String ROUTE_RULE4 =
      "<rules><rule action=\"route\" table=\"default:MyTable\" "
      + "columnFamily=\"data\" qualifier=\"dhold:*\" topic=\"foo\" /></rules>";
  private static final String ROUTE_RULE5 =
      "<rules><rule action=\"route\" table=\"default:MyTable\" "
      + "columnFamily=\"data\" qualifier=\"*pickme\" topic=\"foo\" /></rules>";

  private static final String ROUTE_RULE6 =
      "<rules><rule action=\"route\" table=\"default:MyTable\" "
      + "columnFamily=\"data\" qualifier=\"*pickme*\" topic=\"foo\"  /></rules>";

  @Test
  public void testTopic1() {
    TopicRoutingRules rules = new TopicRoutingRules();
    try {
      rules.parseRules(new ByteArrayInputStream(ROUTE_RULE1.getBytes("UTF-8")));
      Assert.assertEquals(1, rules.getRouteRules().size());
      Assert.assertEquals(TableName.valueOf("default:MyTable"),
        rules.getRouteRules().get(0).getTableName());
      Assert.assertEquals(null, rules.getRouteRules().get(0).getColumnFamily());
      Assert.assertEquals(null, rules.getRouteRules().get(0).getQualifier());
      Assert.assertEquals(0, rules.getDropRules().size());
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void testTopic2() {
    TopicRoutingRules rules = new TopicRoutingRules();
    try {
      rules.parseRules(new ByteArrayInputStream(ROUTE_RULE2.getBytes("UTF-8")));
      Assert.assertEquals(1, rules.getRouteRules().size());
      Assert.assertEquals(TableName.valueOf("default:MyTable"),
        rules.getRouteRules().get(0).getTableName());
      Assert.assertTrue(
        Bytes.equals("data".getBytes("UTF-8"), rules.getRouteRules().get(0).getColumnFamily()));
      Assert.assertEquals(null, rules.getRouteRules().get(0).getQualifier());
      Assert.assertEquals(0, rules.getDropRules().size());
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void testTopic3() {
    TopicRoutingRules rules = new TopicRoutingRules();
    try {
      rules.parseRules(new ByteArrayInputStream(ROUTE_RULE3.getBytes("UTF-8")));
      Assert.assertEquals(1, rules.getRouteRules().size());
      Assert.assertEquals(TableName.valueOf("default:MyTable"),
        rules.getRouteRules().get(0).getTableName());
      Assert.assertTrue(
        Bytes.equals("data".getBytes("UTF-8"), rules.getRouteRules().get(0).getColumnFamily()));
      Assert.assertTrue(
        Bytes.equals("dhold".getBytes("UTF-8"), rules.getRouteRules().get(0).getQualifier()));
      Assert.assertTrue(rules.getRouteRules().get(0).getTopics().contains("foo"));
      Assert.assertEquals(rules.getRouteRules().get(0).getTopics().size(), 1);

      Assert.assertEquals(0, rules.getDropRules().size());
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void testTopic4() {
    TopicRoutingRules rules = new TopicRoutingRules();
    try {
      rules.parseRules(new ByteArrayInputStream(ROUTE_RULE4.getBytes("UTF-8")));
      Assert.assertEquals(1, rules.getRouteRules().size());
      Assert.assertEquals(TableName.valueOf("default:MyTable"),
        rules.getRouteRules().get(0).getTableName());
      Assert.assertTrue(
        Bytes.equals("data".getBytes("UTF-8"), rules.getRouteRules().get(0).getColumnFamily()));
      Assert.assertTrue(
        Bytes.equals("dhold:".getBytes("UTF-8"), rules.getRouteRules().get(0).getQualifier()));
      Assert.assertEquals(0, rules.getDropRules().size());

      TopicRule route = rules.getRouteRules().get(0);
      Assert.assertFalse(
        route.match(TableName.valueOf("default:MyTable"),
                "data".getBytes("UTF-8"),
                "blah".getBytes("UTF-8")));
      Assert.assertFalse(
        route.match(TableName.valueOf("default:MyTable"),
                "data".getBytes("UTF-8"),
                "dholdme".getBytes("UTF-8")));
      Assert.assertTrue(route.match(TableName.valueOf("default:MyTable"),
              "data".getBytes("UTF-8"),
              "dhold:me".getBytes("UTF-8")));

    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void testTopic5() {
    TopicRoutingRules rules = new TopicRoutingRules();
    try {
      rules.parseRules(new ByteArrayInputStream(ROUTE_RULE5.getBytes("UTF-8")));
      Assert.assertEquals(1, rules.getRouteRules().size());
      Assert.assertEquals(TableName.valueOf("default:MyTable"),
        rules.getRouteRules().get(0).getTableName());
      Assert.assertTrue(
        Bytes.equals("data".getBytes("UTF-8"), rules.getRouteRules().get(0).getColumnFamily()));
      Assert.assertTrue(
        Bytes.equals("pickme".getBytes("UTF-8"), rules.getRouteRules().get(0).getQualifier()));
      Assert.assertEquals(0, rules.getDropRules().size());

      TopicRule route = rules.getRouteRules().get(0);
      Assert.assertFalse(
        route.match(TableName.valueOf("default:MyTable"),
                "data".getBytes("UTF-8"),
                "blah".getBytes("UTF-8")));
      Assert.assertFalse(route.match(TableName.valueOf("default:MyTable"),
              "data".getBytes("UTF-8"),
              "blacickme".getBytes("UTF-8")));
      Assert.assertTrue(route.match(TableName.valueOf("default:MyTable"),
              "data".getBytes("UTF-8"),
              "hithere.pickme".getBytes("UTF-8")));

    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

  @Test
  public void testTopic6() {
    TopicRoutingRules rules = new TopicRoutingRules();
    try {
      rules.parseRules(new ByteArrayInputStream(ROUTE_RULE6.getBytes("UTF-8")));
      Assert.assertEquals(1, rules.getRouteRules().size());
      Assert.assertEquals(TableName.valueOf("default:MyTable"),
        rules.getRouteRules().get(0).getTableName());
      Assert.assertTrue(
        Bytes.equals("data".getBytes("UTF-8"),
                rules.getRouteRules().get(0).getColumnFamily()));
      Assert.assertTrue(
        Bytes.equals("pickme".getBytes("UTF-8"),
                rules.getRouteRules().get(0).getQualifier()));
      Assert.assertEquals(0, rules.getDropRules().size());

      TopicRule route = rules.getRouteRules().get(0);
      Assert.assertFalse(
        route.match(TableName.valueOf("default:MyTable"),
                "data".getBytes("UTF-8"),
                "blah".getBytes("UTF-8")));
      Assert.assertFalse(route.match(TableName.valueOf("default:MyTable"),
              "data".getBytes("UTF-8"),
        "blacickme".getBytes("UTF-8")));
      Assert.assertTrue(route.match(TableName.valueOf("default:MyTable"),
              "data".getBytes("UTF-8"),
        "hithere.pickme".getBytes("UTF-8")));
      Assert.assertTrue(route.match(TableName.valueOf("default:MyTable"),
              "data".getBytes("UTF-8"),
        "pickme.pleaze.do.it".getBytes("UTF-8")));
      Assert.assertFalse(route.match(TableName.valueOf("default:MyTable"),
              "data".getBytes("UTF-8"),
        "please.pickme.pleaze".getBytes("UTF-8")));
      Assert.assertTrue(route.match(TableName.valueOf("default:MyTable"),
              "data".getBytes("UTF-8"),
        "pickme.pleaze.pickme".getBytes("UTF-8")));

    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.getMessage());
    }
  }

}
