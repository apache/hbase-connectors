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

import org.apache.hadoop.hbase.testclassification.SmallTests;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Make sure match rules work
 */
@Category(SmallTests.class)
public class TestQualifierMatching {

  @Test
  public void testMatchQualfier() throws Exception {
    DropRule rule = new DropRule();
    rule.setQualifier("data".getBytes("UTF-8"));
    Assert.assertTrue(rule.qualifierMatch("data".getBytes("UTF-8")));

    rule = new DropRule();
    rule.setQualifier("data1".getBytes("UTF-8"));
    Assert.assertFalse(rule.qualifierMatch("data".getBytes("UTF-8")));

    // if not set, it is a wildcard
    rule = new DropRule();
    Assert.assertTrue(rule.qualifierMatch("data".getBytes("UTF-8")));
  }

  @Test
  public void testStartWithQualifier() throws  Exception{
    DropRule rule = new DropRule();
    rule.setQualifier("data*".getBytes("UTF-8"));
    Assert.assertTrue(rule.isQualifierStartsWith());
    Assert.assertFalse(rule.isQualifierEndsWith());

    Assert.assertTrue(rule.qualifierMatch("data".getBytes("UTF-8")));
    Assert.assertTrue(rule.qualifierMatch("data1".getBytes("UTF-8")));
    Assert.assertTrue(rule.qualifierMatch("datafoobar".getBytes("UTF-8")));
    Assert.assertFalse(rule.qualifierMatch("datfoobar".getBytes("UTF-8")));
    Assert.assertFalse(rule.qualifierMatch("d".getBytes("UTF-8")));
    Assert.assertFalse(rule.qualifierMatch("".getBytes("UTF-8")));
  }

  @Test
  public void testEndsWithQualifier() throws Exception {
    DropRule rule = new DropRule();
    rule.setQualifier("*data".getBytes("UTF-8"));
    Assert.assertFalse(rule.isQualifierStartsWith());
    Assert.assertTrue(rule.isQualifierEndsWith());

    Assert.assertTrue(rule.qualifierMatch("data".getBytes("UTF-8")));
    Assert.assertTrue(rule.qualifierMatch("1data".getBytes("UTF-8")));
    Assert.assertTrue(rule.qualifierMatch("foobardata".getBytes("UTF-8")));
    Assert.assertFalse(rule.qualifierMatch("foobardat".getBytes("UTF-8")));
    Assert.assertFalse(rule.qualifierMatch("d".getBytes("UTF-8")));
    Assert.assertFalse(rule.qualifierMatch("".getBytes("UTF-8")));
  }

}
