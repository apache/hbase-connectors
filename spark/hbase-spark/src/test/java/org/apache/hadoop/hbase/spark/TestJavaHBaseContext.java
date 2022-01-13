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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.spark.example.hbasecontext.JavaHBaseBulkDeleteExample;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.MiscTests;
import org.apache.hadoop.hbase.tool.LoadIncrementalHFiles;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Tuple2;

@Category({MiscTests.class, MediumTests.class})
public class TestJavaHBaseContext implements Serializable {

  @ClassRule
  public static final HBaseClassTestRule TIMEOUT =
      HBaseClassTestRule.forClass(TestJavaHBaseContext.class);

  private static transient JavaSparkContext JSC;
  private static HBaseTestingUtility TEST_UTIL;
  private static JavaHBaseContext HBASE_CONTEXT;
  private static final Logger LOG = LoggerFactory.getLogger(TestJavaHBaseContext.class);

  byte[] tableName = Bytes.toBytes("t1");
  byte[] columnFamily = Bytes.toBytes("c");
  byte[] columnFamily1 = Bytes.toBytes("d");
  String columnFamilyStr = Bytes.toString(columnFamily);
  String columnFamilyStr1 = Bytes.toString(columnFamily1);

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {

    JSC = new JavaSparkContext("local", "JavaHBaseContextSuite");
    TEST_UTIL = new HBaseTestingUtility();
    Configuration conf = TEST_UTIL.getConfiguration();

    HBASE_CONTEXT = new JavaHBaseContext(JSC, conf);

    LOG.info("cleaning up test dir");

    TEST_UTIL.cleanupTestDir();

    LOG.info("starting minicluster");

    TEST_UTIL.startMiniCluster();

    LOG.info(" - minicluster started");
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    LOG.info("shuting down minicluster");
    TEST_UTIL.shutdownMiniCluster();
    LOG.info(" - minicluster shut down");
    TEST_UTIL.cleanupTestDir();

    JSC.stop();
    JSC = null;
  }

  @Before
  public void setUp() throws Exception {

    try {
      TEST_UTIL.deleteTable(TableName.valueOf(tableName));
    } catch (Exception e) {
      LOG.info(" - no table {} found", Bytes.toString(tableName));
    }

    LOG.info(" - creating table {}", Bytes.toString(tableName));
    TEST_UTIL.createTable(TableName.valueOf(tableName),
        new byte[][]{columnFamily, columnFamily1});
    LOG.info(" - created table");
  }

  @After
  public void tearDown() throws Exception {
    TEST_UTIL.deleteTable(TableName.valueOf(tableName));
  }

  @Test
  public void testBulkPut() throws IOException {

    List<String> list = new ArrayList<>(5);
    list.add("1," + columnFamilyStr + ",a,1");
    list.add("2," + columnFamilyStr + ",a,2");
    list.add("3," + columnFamilyStr + ",a,3");
    list.add("4," + columnFamilyStr + ",a,4");
    list.add("5," + columnFamilyStr + ",a,5");

    JavaRDD<String> rdd = JSC.parallelize(list);

    Configuration conf = TEST_UTIL.getConfiguration();

    Connection conn = ConnectionFactory.createConnection(conf);
    Table table = conn.getTable(TableName.valueOf(tableName));

    try {
      List<Delete> deletes = new ArrayList<>(5);
      for (int i = 1; i < 6; i++) {
        deletes.add(new Delete(Bytes.toBytes(Integer.toString(i))));
      }
      table.delete(deletes);
    } finally {
      table.close();
    }

    HBASE_CONTEXT.bulkPut(rdd,
            TableName.valueOf(tableName),
            new PutFunction());

    table = conn.getTable(TableName.valueOf(tableName));

    try {
      Result result1 = table.get(new Get(Bytes.toBytes("1")));
      Assert.assertNotNull("Row 1 should had been deleted", result1.getRow());

      Result result2 = table.get(new Get(Bytes.toBytes("2")));
      Assert.assertNotNull("Row 2 should had been deleted", result2.getRow());

      Result result3 = table.get(new Get(Bytes.toBytes("3")));
      Assert.assertNotNull("Row 3 should had been deleted", result3.getRow());

      Result result4 = table.get(new Get(Bytes.toBytes("4")));
      Assert.assertNotNull("Row 4 should had been deleted", result4.getRow());

      Result result5 = table.get(new Get(Bytes.toBytes("5")));
      Assert.assertNotNull("Row 5 should had been deleted", result5.getRow());
    } finally {
      table.close();
      conn.close();
    }
  }

  public static class PutFunction implements Function<String, Put> {

    private static final long serialVersionUID = 1L;

    @Override
    public Put call(String v) throws Exception {
      String[] cells = v.split(",");
      Put put = new Put(Bytes.toBytes(cells[0]));

      put.addColumn(Bytes.toBytes(cells[1]), Bytes.toBytes(cells[2]),
              Bytes.toBytes(cells[3]));
      return put;
    }
  }

  @Test
  public void testBulkDelete() throws IOException {
    List<byte[]> list = new ArrayList<>(3);
    list.add(Bytes.toBytes("1"));
    list.add(Bytes.toBytes("2"));
    list.add(Bytes.toBytes("3"));

    JavaRDD<byte[]> rdd = JSC.parallelize(list);

    Configuration conf = TEST_UTIL.getConfiguration();

    populateTableWithMockData(conf, TableName.valueOf(tableName));

    HBASE_CONTEXT.bulkDelete(rdd, TableName.valueOf(tableName),
            new JavaHBaseBulkDeleteExample.DeleteFunction(), 2);



    try (
            Connection conn = ConnectionFactory.createConnection(conf);
            Table table = conn.getTable(TableName.valueOf(tableName))
    ){
      Result result1 = table.get(new Get(Bytes.toBytes("1")));
      Assert.assertNull("Row 1 should had been deleted", result1.getRow());

      Result result2 = table.get(new Get(Bytes.toBytes("2")));
      Assert.assertNull("Row 2 should had been deleted", result2.getRow());

      Result result3 = table.get(new Get(Bytes.toBytes("3")));
      Assert.assertNull("Row 3 should had been deleted", result3.getRow());

      Result result4 = table.get(new Get(Bytes.toBytes("4")));
      Assert.assertNotNull("Row 4 should had been deleted", result4.getRow());

      Result result5 = table.get(new Get(Bytes.toBytes("5")));
      Assert.assertNotNull("Row 5 should had been deleted", result5.getRow());
    }
  }

  @Test
  public void testDistributedScan() throws IOException {
    Configuration conf = TEST_UTIL.getConfiguration();

    populateTableWithMockData(conf, TableName.valueOf(tableName));

    Scan scan = new Scan();
    scan.setCaching(100);

    JavaRDD<String> javaRdd =
            HBASE_CONTEXT.hbaseRDD(TableName.valueOf(tableName), scan)
                    .map(new ScanConvertFunction());

    List<String> results = javaRdd.collect();

    Assert.assertEquals(results.size(), 5);
  }

  private static class ScanConvertFunction implements
          Function<Tuple2<ImmutableBytesWritable, Result>, String> {
    @Override
    public String call(Tuple2<ImmutableBytesWritable, Result> v1) throws Exception {
      return Bytes.toString(v1._1().copyBytes());
    }
  }

  @Test
  public void testBulkGet() throws IOException {
    List<byte[]> list = new ArrayList<>(5);
    list.add(Bytes.toBytes("1"));
    list.add(Bytes.toBytes("2"));
    list.add(Bytes.toBytes("3"));
    list.add(Bytes.toBytes("4"));
    list.add(Bytes.toBytes("5"));

    JavaRDD<byte[]> rdd = JSC.parallelize(list);

    Configuration conf = TEST_UTIL.getConfiguration();

    populateTableWithMockData(conf, TableName.valueOf(tableName));

    final JavaRDD<String> stringJavaRDD =
            HBASE_CONTEXT.bulkGet(TableName.valueOf(tableName), 2, rdd,
              new GetFunction(),
              new ResultFunction());

    Assert.assertEquals(stringJavaRDD.count(), 5);
  }

  @Test
  public void testBulkLoad() throws Exception {

    Path output = TEST_UTIL.getDataTestDir("testBulkLoad");
    // Add cell as String: "row,falmily,qualifier,value"
    List<String> list= new ArrayList<String>();
    // row1
    list.add("1," + columnFamilyStr + ",b,1");
    // row3
    list.add("3," + columnFamilyStr + ",a,2");
    list.add("3," + columnFamilyStr + ",b,1");
    list.add("3," + columnFamilyStr1 + ",a,1");
    //row2
    list.add("2," + columnFamilyStr + ",a,3");
    list.add("2," + columnFamilyStr + ",b,3");

    JavaRDD<String> rdd = JSC.parallelize(list);

    Configuration conf = TEST_UTIL.getConfiguration();

    HBASE_CONTEXT.bulkLoad(rdd, TableName.valueOf(tableName), new BulkLoadFunction(),
            output.toUri().getPath(), new HashMap<byte[], FamilyHFileWriteOptions>(), false,
            HConstants.DEFAULT_MAX_FILE_SIZE);

    try (Connection conn = ConnectionFactory.createConnection(conf);
         Admin admin = conn.getAdmin()) {
      Table table = conn.getTable(TableName.valueOf(tableName));
      // Do bulk load
      LoadIncrementalHFiles load = new LoadIncrementalHFiles(conf);
      load.doBulkLoad(output, admin, table, conn.getRegionLocator(TableName.valueOf(tableName)));



      // Check row1
      List<Cell> cell1 = table.get(new Get(Bytes.toBytes("1"))).listCells();
      Assert.assertEquals(cell1.size(), 1);
      Assert.assertEquals(Bytes.toString(CellUtil.cloneFamily(cell1.get(0))), columnFamilyStr);
      Assert.assertEquals(Bytes.toString(CellUtil.cloneQualifier(cell1.get(0))), "b");
      Assert.assertEquals(Bytes.toString(CellUtil.cloneValue(cell1.get(0))), "1");

      // Check row3
      List<Cell> cell3 = table.get(new Get(Bytes.toBytes("3"))).listCells();
      Assert.assertEquals(cell3.size(), 3);
      Assert.assertEquals(Bytes.toString(CellUtil.cloneFamily(cell3.get(0))), columnFamilyStr);
      Assert.assertEquals(Bytes.toString(CellUtil.cloneQualifier(cell3.get(0))), "a");
      Assert.assertEquals(Bytes.toString(CellUtil.cloneValue(cell3.get(0))), "2");
      Assert.assertEquals(Bytes.toString(CellUtil.cloneFamily(cell3.get(1))), columnFamilyStr);
      Assert.assertEquals(Bytes.toString(CellUtil.cloneQualifier(cell3.get(1))), "b");
      Assert.assertEquals(Bytes.toString(CellUtil.cloneValue(cell3.get(1))), "1");
      Assert.assertEquals(Bytes.toString(CellUtil.cloneFamily(cell3.get(2))), columnFamilyStr1);
      Assert.assertEquals(Bytes.toString(CellUtil.cloneQualifier(cell3.get(2))), "a");
      Assert.assertEquals(Bytes.toString(CellUtil.cloneValue(cell3.get(2))), "1");

      // Check row2
      List<Cell> cell2 = table.get(new Get(Bytes.toBytes("2"))).listCells();
      Assert.assertEquals(cell2.size(), 2);
      Assert.assertEquals(Bytes.toString(CellUtil.cloneFamily(cell2.get(0))), columnFamilyStr);
      Assert.assertEquals(Bytes.toString(CellUtil.cloneQualifier(cell2.get(0))), "a");
      Assert.assertEquals(Bytes.toString(CellUtil.cloneValue(cell2.get(0))), "3");
      Assert.assertEquals(Bytes.toString(CellUtil.cloneFamily(cell2.get(1))), columnFamilyStr);
      Assert.assertEquals(Bytes.toString(CellUtil.cloneQualifier(cell2.get(1))), "b");
      Assert.assertEquals(Bytes.toString(CellUtil.cloneValue(cell2.get(1))), "3");
    }
  }

  @Test
  public void testBulkLoadThinRows() throws Exception {
    Path output = TEST_UTIL.getDataTestDir("testBulkLoadThinRows");
    // because of the limitation of scala bulkLoadThinRows API
    // we need to provide data as <row, all cells in that row>
    List<List<String>> list= new ArrayList<List<String>>();
    // row1
    List<String> list1 = new ArrayList<String>();
    list1.add("1," + columnFamilyStr + ",b,1");
    list.add(list1);
    // row3
    List<String> list3 = new ArrayList<String>();
    list3.add("3," + columnFamilyStr + ",a,2");
    list3.add("3," + columnFamilyStr + ",b,1");
    list3.add("3," + columnFamilyStr1 + ",a,1");
    list.add(list3);
    //row2
    List<String> list2 = new ArrayList<String>();
    list2.add("2," + columnFamilyStr + ",a,3");
    list2.add("2," + columnFamilyStr + ",b,3");
    list.add(list2);

    JavaRDD<List<String>> rdd = JSC.parallelize(list);

    Configuration conf = TEST_UTIL.getConfiguration();

    HBASE_CONTEXT.bulkLoadThinRows(rdd, TableName.valueOf(tableName),
            new BulkLoadThinRowsFunction(), output.toString(), new HashMap<>(), false,
            HConstants.DEFAULT_MAX_FILE_SIZE);


    try (Connection conn = ConnectionFactory.createConnection(conf);
         Admin admin = conn.getAdmin()) {
      Table table = conn.getTable(TableName.valueOf(tableName));
      // Do bulk load
      LoadIncrementalHFiles load = new LoadIncrementalHFiles(conf);
      load.doBulkLoad(output, admin, table, conn.getRegionLocator(TableName.valueOf(tableName)));

      // Check row1
      List<Cell> cell1 = table.get(new Get(Bytes.toBytes("1"))).listCells();
      Assert.assertEquals(cell1.size(), 1);
      Assert.assertEquals(Bytes.toString(CellUtil.cloneFamily(cell1.get(0))), columnFamilyStr);
      Assert.assertEquals(Bytes.toString(CellUtil.cloneQualifier(cell1.get(0))), "b");
      Assert.assertEquals(Bytes.toString(CellUtil.cloneValue(cell1.get(0))), "1");

      // Check row3
      List<Cell> cell3 = table.get(new Get(Bytes.toBytes("3"))).listCells();
      Assert.assertEquals(cell3.size(), 3);
      Assert.assertEquals(Bytes.toString(CellUtil.cloneFamily(cell3.get(0))), columnFamilyStr);
      Assert.assertEquals(Bytes.toString(CellUtil.cloneQualifier(cell3.get(0))), "a");
      Assert.assertEquals(Bytes.toString(CellUtil.cloneValue(cell3.get(0))), "2");
      Assert.assertEquals(Bytes.toString(CellUtil.cloneFamily(cell3.get(1))), columnFamilyStr);
      Assert.assertEquals(Bytes.toString(CellUtil.cloneQualifier(cell3.get(1))), "b");
      Assert.assertEquals(Bytes.toString(CellUtil.cloneValue(cell3.get(1))), "1");
      Assert.assertEquals(Bytes.toString(CellUtil.cloneFamily(cell3.get(2))), columnFamilyStr1);
      Assert.assertEquals(Bytes.toString(CellUtil.cloneQualifier(cell3.get(2))), "a");
      Assert.assertEquals(Bytes.toString(CellUtil.cloneValue(cell3.get(2))), "1");

      // Check row2
      List<Cell> cell2 = table.get(new Get(Bytes.toBytes("2"))).listCells();
      Assert.assertEquals(cell2.size(), 2);
      Assert.assertEquals(Bytes.toString(CellUtil.cloneFamily(cell2.get(0))), columnFamilyStr);
      Assert.assertEquals(Bytes.toString(CellUtil.cloneQualifier(cell2.get(0))), "a");
      Assert.assertEquals(Bytes.toString(CellUtil.cloneValue(cell2.get(0))), "3");
      Assert.assertEquals(Bytes.toString(CellUtil.cloneFamily(cell2.get(1))), columnFamilyStr);
      Assert.assertEquals(Bytes.toString(CellUtil.cloneQualifier(cell2.get(1))), "b");
      Assert.assertEquals(Bytes.toString(CellUtil.cloneValue(cell2.get(1))), "3");
    }

  }
  public static class BulkLoadFunction
          implements Function<String, Pair<KeyFamilyQualifier, byte[]>> {
    @Override public Pair<KeyFamilyQualifier, byte[]> call(String v1) throws Exception {
      if (v1 == null) {
        return null;
      }

      String[] strs = v1.split(",");
      if(strs.length != 4) {
        return null;
      }

      KeyFamilyQualifier kfq = new KeyFamilyQualifier(Bytes.toBytes(strs[0]),
              Bytes.toBytes(strs[1]), Bytes.toBytes(strs[2]));
      return new Pair(kfq, Bytes.toBytes(strs[3]));
    }
  }

  public static class BulkLoadThinRowsFunction
          implements Function<List<String>, Pair<ByteArrayWrapper, FamiliesQualifiersValues>> {
    @Override public Pair<ByteArrayWrapper, FamiliesQualifiersValues> call(List<String> list) {
      if (list == null) {
        return null;
      }

      ByteArrayWrapper rowKey = null;
      FamiliesQualifiersValues fqv = new FamiliesQualifiersValues();
      for (String cell : list) {
        String[] strs = cell.split(",");
        if (rowKey == null) {
          rowKey = new ByteArrayWrapper(Bytes.toBytes(strs[0]));
        }
        fqv.add(Bytes.toBytes(strs[1]), Bytes.toBytes(strs[2]), Bytes.toBytes(strs[3]));
      }
      return new Pair(rowKey, fqv);
    }
  }

  public static class GetFunction implements Function<byte[], Get> {

    private static final long serialVersionUID = 1L;

    @Override
    public Get call(byte[] v) throws Exception {
      return new Get(v);
    }
  }

  public static class ResultFunction implements Function<Result, String> {

    private static final long serialVersionUID = 1L;

    @Override
    public String call(Result result) throws Exception {
      Iterator<Cell> it = result.listCells().iterator();
      StringBuilder b = new StringBuilder();

      b.append(Bytes.toString(result.getRow())).append(":");

      while (it.hasNext()) {
        Cell cell = it.next();
        String q = Bytes.toString(CellUtil.cloneQualifier(cell));
        if ("counter".equals(q)) {
          b.append("(")
                  .append(q)
                  .append(",")
                  .append(Bytes.toLong(CellUtil.cloneValue(cell)))
                  .append(")");
        } else {
          b.append("(")
                  .append(q)
                  .append(",")
                  .append(Bytes.toString(CellUtil.cloneValue(cell)))
                  .append(")");
        }
      }
      return b.toString();
    }
  }

  private void populateTableWithMockData(Configuration conf, TableName tableName)
          throws IOException {
    try (
      Connection conn = ConnectionFactory.createConnection(conf);
      Table table = conn.getTable(tableName)) {

      List<Put> puts = new ArrayList<>(5);

      for (int i = 1; i < 6; i++) {
        Put put = new Put(Bytes.toBytes(Integer.toString(i)));
        put.addColumn(columnFamily, columnFamily, columnFamily);
        puts.add(put);
      }
      table.put(puts);
    }
  }
}
