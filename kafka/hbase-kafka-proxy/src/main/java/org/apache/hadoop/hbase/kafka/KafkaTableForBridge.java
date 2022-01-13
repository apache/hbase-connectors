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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.client.RegionLocator;
import org.apache.hadoop.hbase.client.Row;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hbase.thirdparty.org.apache.commons.collections4.CollectionUtils;


@InterfaceAudience.Private
public class KafkaTableForBridge implements Table {
  private Logger LOG = LoggerFactory.getLogger(KafkaTableForBridge.class);

  private final Configuration conf;
  private final TableName tableName;
  private byte[] tableAsBytes;

  private Producer<byte[],byte[]> producer;
  private TopicRoutingRules routingRules;

  private DatumWriter<HBaseKafkaEvent> avroWriter;

  private static final class CheckMutation {
    byte[]qualifier;
    byte[]family;
    Cell cell;
    List<String> topics = new ArrayList<>();
  }

  @Override
  public RegionLocator getRegionLocator() throws IOException {
    throw new UnsupportedOperationException();
  }

  public KafkaTableForBridge(TableName tableName,
                 Configuration conf,
                 TopicRoutingRules routingRules,
                 Producer<byte[],byte[]> producer,
                 DatumWriter<HBaseKafkaEvent> avroWriter){
    this.conf=conf;
    this.tableName=tableName;
    this.tableAsBytes=this.tableName.toBytes();
    this.routingRules=routingRules;
    this.producer=producer;
    this.avroWriter=avroWriter;
  }

  private List<Pair<String,HBaseKafkaEvent>> processMutation(CheckMutation check, boolean isDelete){
    HBaseKafkaEvent event = new HBaseKafkaEvent();
    event.setKey(ByteBuffer.wrap(check.cell.getRowArray(),
            check.cell.getRowOffset(),
            check.cell.getRowLength()));
    event.setTable(ByteBuffer.wrap(tableAsBytes));
    event.setDelete(isDelete);
    event.setTimestamp(check.cell.getTimestamp());
    event.setFamily(ByteBuffer.wrap(check.family));
    event.setQualifier(ByteBuffer.wrap(check.qualifier));
    event.setValue(ByteBuffer.wrap(check.cell.getValueArray(),
            check.cell.getValueOffset(),
            check.cell.getValueLength()));

    return check.topics.stream()
        .map((topic)->new Pair<String,HBaseKafkaEvent>(topic,event))
        .collect(Collectors.toList());
  }

  private boolean keep(CheckMutation ret){
    if (!routingRules.isExclude(this.tableName,ret.family, ret.qualifier)){
      return true;
    }
    return false;
  }

  private CheckMutation addTopics(CheckMutation ret){
    ret.topics= routingRules.getTopics(this.tableName,ret.family,ret.qualifier);
    return ret;
  }

  private ProducerRecord<byte[],byte[]> toByteArray(ByteArrayOutputStream bout,
                                                    Pair<String,HBaseKafkaEvent> event,
                                                    BinaryEncoder encoder) {
    try {
      bout.reset();
      BinaryEncoder encoderUse = EncoderFactory.get().binaryEncoder(bout, encoder);
      avroWriter.write(event.getSecond(), encoderUse);
      encoder.flush();
      return new ProducerRecord<byte[],byte[]>(event.getFirst(),
              event.getSecond().getKey().array(),
              bout.toByteArray());
    } catch (Exception e){
      throw new RuntimeException(e);
    }
  }

  @Override
  public void batch(final List<? extends Row> actions, Object[] results)
      throws IOException, InterruptedException {

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    BinaryEncoder encoderUse = EncoderFactory.get().binaryEncoder(bout, null);

    LOG.debug("got {} inputs ",actions.size());

    List<Future<RecordMetadata>> sends = new ArrayList<>();

    actions.stream()
      .filter((row)->row instanceof Mutation)
      .map((row)->(Mutation)row)
      .flatMap((row)->{
        Mutation mut = (Mutation) row;
        boolean isDelete = mut instanceof Delete;
        return mut.getFamilyCellMap().keySet().stream()
          .flatMap((family)->mut.getFamilyCellMap().get(family).stream())
            .map((cell)->{
              CheckMutation ret = new CheckMutation();
              ret.family=CellUtil.cloneFamily(cell);
              ret.qualifier=CellUtil.cloneQualifier(cell);
              ret.cell=cell;
              return ret;
            })
          .filter((check)->keep(check))
          .map((check)->addTopics(check))
          .filter((check)->!CollectionUtils.isEmpty(check.topics))
          .flatMap((check)->processMutation(check,isDelete).stream());
      })
      .map((event)->toByteArray(bout,event,encoderUse))
      .forEach((item)->sends.add(producer.send(item)));

    // make sure the sends are done before returning
    sends.stream().forEach((sendResult)->{
      try {
        sendResult.get();
      } catch (Exception e){
        LOG.error("Exception caught when getting result",e);
        throw new RuntimeException(e);
      }
    });

    this.producer.flush();
  }

  @Override
  public void close() {
    this.producer.flush();
  }

  @Override
  public TableName getName() {
    return this.tableName;
  }

  @Override
  public Configuration getConfiguration() {
    return this.conf;
  }

  @Override
  public HTableDescriptor getTableDescriptor() throws IOException {
    return null;
  }

  @Override
  public TableDescriptor getDescriptor() throws IOException {
    return null;
  }

}
