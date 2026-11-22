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
package org.apache.hadoop.hbase.spark;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.hbase.HBaseClassTestRule;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.BufferedMutator;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RegionLocator;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.io.encoding.DataBlockEncoding;
import org.apache.hadoop.hbase.io.hfile.CacheConfig;
import org.apache.hadoop.hbase.io.hfile.HFile;
import org.apache.hadoop.hbase.regionserver.HStoreFile;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.MiscTests;
import org.apache.hadoop.hbase.tool.LoadIncrementalHFiles;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.VoidFunction;
import org.apache.spark.util.LongAccumulator;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.function.ThrowingRunnable;
import org.junit.rules.TestName;
import scala.Tuple2;

@Category({ MiscTests.class, MediumTests.class })
public class TestJavaHBaseContext {
  @ClassRule
  public static final HBaseClassTestRule TIMEOUT =
    HBaseClassTestRule.forClass(TestJavaHBaseContext.class);

  private static final HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
  private static final byte[] FAMILY = Bytes.toBytes("c");
  private static final byte[] OTHER_FAMILY = Bytes.toBytes("d");
  private static final byte[] QUALIFIER = Bytes.toBytes("a");
  private static JavaSparkContext jsc;
  private static JavaHBaseContext context;

  @Rule
  public final TestName testName = new TestName();

  private TableName tableName;

  @BeforeClass
  public static void beforeClass() throws Exception {
    TEST_UTIL.startMiniCluster();
    SparkConf sparkConf = new SparkConf().setMaster("local[2]").setAppName("TestJavaHBaseContext")
      .set("spark.ui.enabled", "false").set("spark.hadoopRDD.ignoreEmptySplits", "false");
    jsc = new JavaSparkContext(sparkConf);
    context = new JavaHBaseContext(jsc, TEST_UTIL.getConfiguration());
  }

  @AfterClass
  public static void afterClass() throws Exception {
    try {
      if (jsc != null) {
        jsc.close();
      }
    } finally {
      TEST_UTIL.shutdownMiniCluster();
      TEST_UTIL.cleanupTestDir();
    }
  }

  @Before
  public void before() throws Exception {
    tableName = TableName.valueOf(testName.getMethodName());
    TEST_UTIL.createTable(tableName, new byte[][] { FAMILY, OTHER_FAMILY }).close();
  }

  @After
  public void after() throws Exception {
    TEST_UTIL.deleteTable(tableName);
  }

  @Test
  public void testBulkPut() throws Exception {
    context.bulkPut(keys(), tableName, key -> put(key, "value" + key));
    for (String key : Arrays.asList("1", "2", "3", "4", "5")) {
      assertValue(key, FAMILY, QUALIFIER, "value" + key);
    }
  }

  @Test
  public void testBulkDeleteBatchesAndTail() throws Exception {
    seedRows();
    JavaRDD<String> keys = jsc.parallelize(Arrays.asList("1", "2", "3"), 1);
    context.bulkDelete(keys, tableName, key -> new Delete(Bytes.toBytes(key)), 2);
    try (Table table = TEST_UTIL.getConnection().getTable(tableName)) {
      for (String key : Arrays.asList("1", "2", "3")) {
        Assert.assertTrue(table.get(new Get(Bytes.toBytes(key))).isEmpty());
      }
    }
    assertValue("4", FAMILY, QUALIFIER, "value4");
    assertValue("5", FAMILY, QUALIFIER, "value5");
  }

  @Test
  public void testBulkGetBatchesMissingAndDuplicateKeys() throws Exception {
    seedRows();
    JavaRDD<String> keys = jsc.parallelize(Arrays.asList("1", "2", "missing", "3", "1"), 1);
    JavaRDD<String> results = context.bulkGet(tableName, 2, keys,
      key -> new Get(Bytes.toBytes(key)),
      result -> result.isEmpty() ? "missing" : Bytes.toString(result.getValue(FAMILY, QUALIFIER)));
    Assert.assertEquals(Arrays.asList("value1", "value2", "missing", "value3", "value1"),
      results.collect());
  }

  @Test
  public void testBulkGetBoxedAndCustomResults() throws Exception {
    seedRows();
    JavaRDD<Integer> numbers =
      context.bulkGet(tableName, 2, keys(), key -> new Get(Bytes.toBytes(key)),
        result -> Integer.valueOf(Bytes.toString(result.getRow())));
    Assert.assertEquals(Arrays.asList(1, 2, 3, 4, 5), numbers.collect());

    JavaRDD<RowValue> rows = context.bulkGet(tableName, 2, keys(),
      key -> new Get(Bytes.toBytes(key)), TestJavaHBaseContext::rowValue);
    List<RowValue> values = rows.collect();
    Assert.assertEquals(5, values.size());
    Assert.assertEquals("3", values.get(2).key);
    Assert.assertEquals("value3", values.get(2).value);
  }

  @Test
  public void testRawScan() throws Exception {
    seedRows();
    Scan scan = new Scan().withStartRow(Bytes.toBytes("2")).withStopRow(Bytes.toBytes("5"));
    JavaRDD<String> rows =
      context.hbaseRDD(tableName, scan).map(pair -> Bytes.toString(pair._1().copyBytes()) + "="
        + Bytes.toString(pair._2().getValue(FAMILY, QUALIFIER)));
    List<String> values = new ArrayList<>(rows.collect());
    Collections.sort(values);
    Assert.assertEquals(Arrays.asList("2=value2", "3=value3", "4=value4"), values);
  }

  @Test
  public void testMappedScan() throws Exception {
    seedRows();
    Scan scan = new Scan().withStartRow(Bytes.toBytes("2")).withStopRow(Bytes.toBytes("5"));
    JavaRDD<Integer> rows = context.hbaseRDD(tableName, scan,
      pair -> Integer.valueOf(Bytes.toString(pair._1().copyBytes())));
    List<Integer> values = new ArrayList<>(rows.collect());
    Collections.sort(values);
    Assert.assertEquals(Arrays.asList(2, 3, 4), values);
  }

  @Test
  public void testForeachPartition() throws Exception {
    context.foreachPartition(jsc.parallelize(Arrays.asList("1", "2", "3"), 2),
      new WritePartition(tableName.getNameAsString()));
    for (String key : Arrays.asList("1", "2", "3")) {
      assertValue(key, FAMILY, QUALIFIER, "value" + key);
    }
  }

  @Test
  public void testMapPartitionsWithLazyJavaIterator() throws Exception {
    seedRows();
    String name = tableName.getNameAsString();
    JavaRDD<String> keys = jsc.parallelize(Arrays.asList("1", "2"), 4);
    JavaRDD<RowValue> rows =
      context.mapPartitions(keys, pair -> readRows(pair._1(), pair._2(), name));
    List<RowValue> values = rows.collect();
    Assert.assertEquals(2, values.size());
    Assert.assertEquals("1", values.get(0).key);
    Assert.assertEquals("value1", values.get(0).value);
    Assert.assertEquals("2", values.get(1).key);
    Assert.assertEquals("value2", values.get(1).value);

    JavaRDD<Integer> sizes = context.mapPartitions(keys, pair -> {
      int size = 0;
      while (pair._1().hasNext()) {
        pair._1().next();
        size++;
      }
      return Collections.singletonList(size).iterator();
    });
    List<Integer> partitionSizes = new ArrayList<>(sizes.collect());
    Collections.sort(partitionSizes);
    Assert.assertEquals(Arrays.asList(0, 0, 1, 1), partitionSizes);
  }

  @Test
  public void testEmptyInputs() throws Exception {
    JavaRDD<String> empty = jsc.parallelize(Collections.emptyList(), 3);
    context.bulkPut(empty, tableName, key -> put(key, key));
    context.bulkDelete(empty, tableName, key -> new Delete(Bytes.toBytes(key)), 2);
    Assert.assertTrue(context.bulkGet(tableName, 2, empty, key -> new Get(Bytes.toBytes(key)),
      TestJavaHBaseContext::rowValue).collect().isEmpty());
    Assert.assertEquals(0L, context.hbaseRDD(tableName, new Scan()).count());

    LongAccumulator partitions = jsc.sc().longAccumulator();
    context.foreachPartition(empty, pair -> {
      Assert.assertFalse(pair._1().hasNext());
      Assert.assertFalse(pair._2().isClosed());
      partitions.add(1L);
    });
    Assert.assertEquals(3L, partitions.value().longValue());
    Assert.assertTrue(context.mapPartitions(empty, pair -> pair._1()).collect().isEmpty());
  }

  @Test
  public void testCallbackFailures() throws Exception {
    JavaRDD<String> keys = jsc.parallelize(Collections.singletonList("1"), 1);
    assertCallbackFailure(() -> context.foreachPartition(keys, pair -> {
      throw new IOException("java callback failure");
    }));
    assertCallbackFailure(() -> context.mapPartitions(keys, pair -> {
      throw new IOException("java callback failure");
    }).collect());
    seedRows();
    assertCallbackFailure(
      () -> context.bulkGet(tableName, 2, keys, key -> new Get(Bytes.toBytes(key)), result -> {
        throw new IOException("java callback failure");
      }).collect());
    assertCallbackFailure(() -> context.hbaseRDD(tableName, new Scan(), pair -> {
      throw new IOException("java callback failure");
    }).collect());
  }

  @Test
  public void testBulkLoad() throws Exception {
    JavaRDD<String> cells =
      jsc.parallelize(Arrays.asList("3,d,b,three-b", "1,c,a,one", "3,c,a,three-a", "2,c,a,two"), 2);
    Path output = new Path(TEST_UTIL.getDataTestDirOnTestFS(), testName.getMethodName());
    try {
      context.bulkLoad(cells, tableName, TestJavaHBaseContext::toCell, output.toString(),
        writeOptions(), true, 1L);
      checkHFilesAndLoad(output);
      assertLoadedCells();
    } finally {
      output.getFileSystem(TEST_UTIL.getConfiguration()).delete(output, true);
    }
  }

  @Test
  public void testBulkLoadThinRows() throws Exception {
    JavaRDD<List<String>> rows =
      jsc.parallelize(Arrays.asList(Arrays.asList("3,d,b,three-b", "3,c,a,three-a"),
        Collections.singletonList("1,c,a,one"), Collections.singletonList("2,c,a,two")), 2);
    Path output = new Path(TEST_UTIL.getDataTestDirOnTestFS(), testName.getMethodName());
    try {
      context.bulkLoadThinRows(rows, tableName, TestJavaHBaseContext::toThinRow, output.toString(),
        writeOptions(), true, 1L);
      checkHFilesAndLoad(output);
      assertLoadedCells();
    } finally {
      output.getFileSystem(TEST_UTIL.getConfiguration()).delete(output, true);
    }
  }

  @Test
  public void testLargeValue() throws Exception {
    byte[] value = new byte[2 * 1024 * 1024];
    Arrays.fill(value, (byte) 'v');
    try (Table table = TEST_UTIL.getConnection().getTable(tableName)) {
      table.put(new Put(Bytes.toBytes("large")).addColumn(FAMILY, QUALIFIER, value));
    }
    JavaRDD<byte[]> results =
      context.bulkGet(tableName, 2, jsc.parallelize(Collections.singletonList("large"), 1),
        key -> new Get(Bytes.toBytes(key)), result -> result.getValue(FAMILY, QUALIFIER));
    List<byte[]> values = results.collect();
    Assert.assertEquals(1, values.size());
    Assert.assertArrayEquals(value, values.get(0));
  }

  private JavaRDD<String> keys() {
    return jsc.parallelize(Arrays.asList("1", "2", "3", "4", "5"), 1);
  }

  private void seedRows() throws IOException {
    try (Table table = TEST_UTIL.getConnection().getTable(tableName)) {
      for (String key : Arrays.asList("1", "2", "3", "4", "5")) {
        table.put(put(key, "value" + key));
      }
    }
  }

  private static Put put(String key, String value) {
    return new Put(Bytes.toBytes(key)).addColumn(FAMILY, QUALIFIER, Bytes.toBytes(value));
  }

  private void assertValue(String key, byte[] family, byte[] qualifier, String value)
    throws IOException {
    try (Table table = TEST_UTIL.getConnection().getTable(tableName)) {
      Assert.assertArrayEquals(Bytes.toBytes(value),
        table.get(new Get(Bytes.toBytes(key))).getValue(family, qualifier));
    }
  }

  private static RowValue rowValue(Result result) {
    return new RowValue(Bytes.toString(result.getRow()),
      Bytes.toString(result.getValue(FAMILY, QUALIFIER)));
  }

  private static Iterator<RowValue> readRows(Iterator<String> input, Connection connection,
    String tableName) {
    return new Iterator<RowValue>() {
      @Override
      public boolean hasNext() {
        return input.hasNext();
      }

      @Override
      public RowValue next() {
        Assert.assertFalse(connection.isClosed());
        try (Table table = connection.getTable(TableName.valueOf(tableName))) {
          return rowValue(table.get(new Get(Bytes.toBytes(input.next()))));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  private static void assertCallbackFailure(ThrowingRunnable action) {
    Exception error = Assert.assertThrows(Exception.class, action);
    while (error.getCause() instanceof Exception) {
      error = (Exception) error.getCause();
    }
    Assert.assertEquals("java callback failure", error.getMessage());
  }

  private static Pair<KeyFamilyQualifier, byte[]> toCell(String value) {
    String[] parts = value.split(",", 4);
    return new Pair<>(new KeyFamilyQualifier(Bytes.toBytes(parts[0]), Bytes.toBytes(parts[1]),
      Bytes.toBytes(parts[2])), Bytes.toBytes(parts[3]));
  }

  private static Pair<ByteArrayWrapper, FamiliesQualifiersValues> toThinRow(List<String> cells) {
    FamiliesQualifiersValues values = new FamiliesQualifiersValues();
    ByteArrayWrapper key = null;
    for (String cell : cells) {
      Pair<KeyFamilyQualifier, byte[]> pair = toCell(cell);
      KeyFamilyQualifier kfq = pair.getFirst();
      key = new ByteArrayWrapper(kfq.rowKey());
      values.add(kfq.family(), kfq.qualifier(), pair.getSecond());
    }
    return new Pair<>(key, values);
  }

  private static Map<byte[], FamilyHFileWriteOptions> writeOptions() {
    Map<byte[], FamilyHFileWriteOptions> options = new HashMap<>();
    options.put(FAMILY, new FamilyHFileWriteOptions("NONE", "ROW", 16 * 1024, "PREFIX"));
    return options;
  }

  private void checkHFilesAndLoad(Path output) throws Exception {
    FileSystem fs = output.getFileSystem(TEST_UTIL.getConfiguration());
    RemoteIterator<LocatedFileStatus> files = fs.listFiles(new Path(output, "c"), false);
    Assert.assertTrue(files.hasNext());
    int fileCount = 0;
    while (files.hasNext()) {
      fileCount++;
      try (HFile.Reader reader = HFile.createReader(fs, files.next().getPath(),
        new CacheConfig(TEST_UTIL.getConfiguration()), true, TEST_UTIL.getConfiguration())) {
        Assert.assertEquals(DataBlockEncoding.PREFIX, reader.getDataBlockEncoding());
        Assert.assertTrue(
          Bytes.toBoolean(reader.getHFileInfo().get(HStoreFile.EXCLUDE_FROM_MINOR_COMPACTION_KEY)));
      }
    }
    Assert.assertTrue("Small maxSize should roll HFiles", fileCount > 1);
    try (Admin admin = TEST_UTIL.getConnection().getAdmin();
      Table table = TEST_UTIL.getConnection().getTable(tableName);
      RegionLocator locator = TEST_UTIL.getConnection().getRegionLocator(tableName)) {
      new LoadIncrementalHFiles(TEST_UTIL.getConfiguration()).doBulkLoad(output, admin, table,
        locator);
    }
  }

  private void assertLoadedCells() throws IOException {
    assertValue("1", FAMILY, QUALIFIER, "one");
    assertValue("2", FAMILY, QUALIFIER, "two");
    assertValue("3", FAMILY, QUALIFIER, "three-a");
    assertValue("3", OTHER_FAMILY, Bytes.toBytes("b"), "three-b");
  }

  private static class RowValue implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String key;
    private final String value;

    RowValue(String key, String value) {
      this.key = key;
      this.value = value;
    }
  }

  private static class WritePartition
    implements VoidFunction<Tuple2<Iterator<String>, Connection>> {
    private static final long serialVersionUID = 1L;
    private final String tableName;

    WritePartition(String tableName) {
      this.tableName = tableName;
    }

    @Override
    public void call(Tuple2<Iterator<String>, Connection> pair) throws IOException {
      try (BufferedMutator mutator = pair._2().getBufferedMutator(TableName.valueOf(tableName))) {
        while (pair._1().hasNext()) {
          String key = pair._1().next();
          mutator.mutate(put(key, "value" + key));
        }
        mutator.flush();
      }
    }
  }
}
