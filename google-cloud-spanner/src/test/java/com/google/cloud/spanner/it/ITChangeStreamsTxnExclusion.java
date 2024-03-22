/*
 * Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.cloud.spanner.it;

import com.google.api.core.ApiFutures;
import static com.google.cloud.spanner.SpannerMatchers.isSpannerException;
import static com.google.cloud.spanner.Type.array;
import static com.google.cloud.spanner.Type.json;
import static com.google.cloud.spanner.Type.struct;
import static com.google.cloud.spanner.Type.timestamp;
import static com.google.cloud.spanner.testing.EmulatorSpannerHelper.isUsingEmulator;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.google.api.gax.rpc.ServerStream;
import com.google.cloud.ByteArray;
import com.google.cloud.Date;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.CommitResponse;
import com.google.cloud.spanner.Database;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Dialect;
import com.google.cloud.spanner.ErrorCode;
import com.google.cloud.spanner.IntegrationTestEnv;
import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.TransactionRunner;
import com.google.cloud.spanner.TransactionManager;
import com.google.cloud.spanner.AsyncTransactionManager;
import com.google.cloud.spanner.TransactionContext;
import com.google.cloud.spanner.AsyncTransactionManager.TransactionContextFuture;
import com.google.cloud.spanner.MutationGroup;
import com.google.cloud.spanner.Options;
import com.google.cloud.spanner.ParallelIntegrationTest;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.SpannerException;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.Struct;
import com.google.cloud.spanner.TimestampBound;
import com.google.cloud.spanner.Type;
import com.google.cloud.spanner.Value;
import com.google.cloud.spanner.connection.ConnectionOptions;
import static com.google.cloud.spanner.SpannerApiFutures.get;
import com.google.cloud.spanner.it.ITChangeStreamsTxnExclusion;
import com.google.cloud.spanner.testing.EmulatorSpannerHelper;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.NullValue;
import com.google.rpc.Code;
import com.google.rpc.Status;
import com.google.spanner.executor.v1.ChangeStreamRecord;
import com.google.spanner.executor.v1.ChildPartitionsRecord;
import com.google.spanner.executor.v1.DataChangeRecord;
import com.google.spanner.executor.v1.HeartbeatRecord;
import com.google.spanner.v1.BatchWriteResponse;
import io.grpc.Context;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.hamcrest.MatcherAssert;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.threeten.bp.Instant;

/** Integration test for writing data to Cloud Spanner. */
@Category(ParallelIntegrationTest.class)
@RunWith(Parameterized.class)
public class ITChangeStreamsTxnExclusion {
  @ClassRule
  public static IntegrationTestEnv env = new IntegrationTestEnv();

  @Parameterized.Parameters(name = "Dialect = {0}")
  public static List<DialectTestParameter> data() {
    List<DialectTestParameter> params = new ArrayList<>();
    params.add(new DialectTestParameter(Dialect.GOOGLE_STANDARD_SQL));
    return params;
  }

  @Parameterized.Parameter()
  public DialectTestParameter dialect;

  private static DatabaseClient googleStandardSQLClient;
  private static DatabaseClient postgreSQLClient;

  private static String ExcludedChangeStream = "TestCS1";

  private static String IncludedChangeStream = "TestCS2";

  private static final String[] GOOGLE_STANDARD_SQL_SCHEMA = new String[] {
      "CREATE TABLE T1 ("
          + "K   STRING(MAX) NOT NULL,"
          + "T   TIMESTAMP OPTIONS (allow_commit_timestamp = true),"
          + "V   INT64,"
          + ") PRIMARY KEY (K)",
      "CREATE CHANGE STREAM TestCS1 FOR ALL OPTIONS(allow_txn_exclusion=true)",
      "CREATE CHANGE STREAM TestCS2 FOR ALL"
  };

  /** Sequence used to generate unique keys. */
  private static int seq;

  private static DatabaseClient client;

  private static Timestamp startTs;

  private static ExecutorService executor;
  @BeforeClass
  public static void setUpDatabase()
      throws ExecutionException, InterruptedException, TimeoutException {
    Database googleStandardSQLDatabase = env.getTestHelper().createTestDatabase(GOOGLE_STANDARD_SQL_SCHEMA);
    startTs = Timestamp.now();
    executor = Executors.newSingleThreadExecutor();
    googleStandardSQLClient = env.getTestHelper().getDatabaseClient(googleStandardSQLDatabase);
  }

  @Before
  public void before() {
    client = googleStandardSQLClient;
  }

  @AfterClass
  public static void teardown() {
    ConnectionOptions.closeSpanner();
  }

  private static String uniqueString() {
    return String.format("k%04d", seq++);
  }

  private String lastKey;

  private boolean isNonNullDataChangeRecord(Struct row) {
    return !row.isNull("commit_timestamp");
  }

  private boolean isNonNullChildPartitionsRecord(Struct row) {
    return !row.isNull("start_timestamp");
  }

  public List<String> getInitialTokens(String changeStreamName, Timestamp start, Timestamp end) {
    List<String> tokens = new ArrayList<>();
    final String query = String.format("SELECT * FROM READ_%s ("
        + "start_timestamp => @startTimestamp,"
        + "end_timestamp => @endTimestamp,"
        + "partition_token => null,"
        + "heartbeat_milliseconds => 100000"
        + ")", changeStreamName);
    final ResultSet resultSet = client
        .singleUse()
        .executeQuery(
            Statement.newBuilder(query)
                .bind("startTimestamp").to(start)
                .bind("endTimestamp").to(end)
                .build());
    while (resultSet.next()) {
      // Parses result set into change stream result format.
      final Struct row = resultSet.getCurrentRowAsStruct().getStructList(0).get(0);
      final List<Struct> child_partitions_record = row.getStructList("child_partitions_record");
      for(Struct record:child_partitions_record){
        if (isNonNullChildPartitionsRecord(record)) {
          List<Struct> child_partitions = record.getStructList("child_partitions").stream()
              .collect(Collectors.toList());
          for (Struct child_partition : child_partitions) {
            tokens.add(child_partition.getString("token"));
          }
        }
      }
    }
    return tokens;
  }

  public int numOfDataRecordsInRange(String changeStreamName, Timestamp start,
      Timestamp end) {
    int res = 0;
    List<String> tokens = getInitialTokens(changeStreamName, start, end);
    for(String token:tokens){
      final String query =
          String.format("SELECT * FROM READ_%s ("
              + "start_timestamp => @startTimestamp,"
              + "end_timestamp => @endTimestamp,"
              + "partition_token => @partitionToken,"
              + "heartbeat_milliseconds => 100000"
              + ")", changeStreamName);

      final ResultSet resultSet =
          client
              .singleUse()
              .executeQuery(
                  Statement.newBuilder(query)
                      .bind("startTimestamp").to(start)
                      .bind("endTimestamp").to(end)
                      .bind("partitionToken").to(token)
                      .build());
      while (resultSet.next()) {
        // Parses result set into change stream result format.
        final Struct row = resultSet.getCurrentRowAsStruct().getStructList(0).get(0);
        final List<Struct> data_change_records = row.getStructList("data_change_record");
        for(Struct record:data_change_records){
          if (isNonNullDataChangeRecord(record)) {
            res++;
          }
        }
      }
    }
    return res;
  }

  @Test
  public void writeWithOptions() {
    CommitResponse response = client.writeWithOptions(Collections.singletonList(
        Mutation.newInsertOrUpdateBuilder("T1")
            .set("K")
            .to(uniqueString())
            .set("V")
            .to(1)
            .build()),Options.excludeTxnFromChangeStreams());
    assertEquals(numOfDataRecordsInRange(ExcludedChangeStream,response.getCommitTimestamp(),response.getCommitTimestamp()),0);
    assertEquals(numOfDataRecordsInRange(IncludedChangeStream,response.getCommitTimestamp(),response.getCommitTimestamp()),1);
  }

  @Test
  public void writeAtLeastOnceWithOptions() {
    CommitResponse response = client.writeAtLeastOnceWithOptions(Collections.singletonList(
        Mutation.newInsertOrUpdateBuilder("T1")
            .set("K")
            .to(uniqueString())
            .set("V")
            .to(1)
            .build()),Options.excludeTxnFromChangeStreams());
    assertEquals(numOfDataRecordsInRange(ExcludedChangeStream,response.getCommitTimestamp(),response.getCommitTimestamp()),0);
    assertEquals(numOfDataRecordsInRange(IncludedChangeStream,response.getCommitTimestamp(),response.getCommitTimestamp()),1);
  }

  @Test
  public void batchWriteAtLeastOnceWithOptions() {
    ServerStream<BatchWriteResponse> responses  = client.batchWriteAtLeastOnce(ImmutableList.of(MutationGroup.of(
        Mutation.newInsertOrUpdateBuilder("T1")
            .set("K")
            .to(uniqueString())
            .set("V")
            .to(1)
            .build())),Options.excludeTxnFromChangeStreams());
    int numOfDataRecords1 = 0;
    int numOfDataRecords2 = 0;
    for (BatchWriteResponse response : responses) {
      assertThat(response.getStatus().getCode() == Code.OK_VALUE);
      numOfDataRecords1 += numOfDataRecordsInRange(ExcludedChangeStream,Timestamp.fromProto(response.getCommitTimestamp()),Timestamp.fromProto(response.getCommitTimestamp()));
      numOfDataRecords2 += numOfDataRecordsInRange(IncludedChangeStream,Timestamp.fromProto(response.getCommitTimestamp()),Timestamp.fromProto(response.getCommitTimestamp()));

    }
    assertEquals(numOfDataRecords1,0);
    assertEquals(numOfDataRecords2,1);
  }

  @Test
  public void executeDMLInRwTxn() {
    Timestamp t1 = Timestamp.now();
    client
        .readWriteTransaction(Options.excludeTxnFromChangeStreams())
        .run(transaction -> {
          transaction.executeUpdate(
              Statement.of("INSERT INTO T1 (K,V) VALUES ('executeUpdate',1)"));
          transaction.batchUpdate(Collections.singletonList(Statement.of("INSERT INTO T1 (K,V) VALUES ('batchUpdate',2)")));
          transaction.executeUpdateAsync(
              Statement.of("INSERT INTO T1 (K,V) VALUES ('executeUpdateAsync',1)"));
          transaction.batchUpdateAsync(Collections.singletonList(Statement.of("INSERT INTO T1 (K,V) VALUES ('batchUpdateAsync',4)")));
          return null;
        });
    Timestamp t2 = Timestamp.now();
    assertEquals(numOfDataRecordsInRange(ExcludedChangeStream,t1,t2),0);
    assertEquals(numOfDataRecordsInRange(IncludedChangeStream,t1,t2),1);
  }

  @Test
  public void executePartitionedDML() {
    client.writeWithOptions(Collections.singletonList(
        Mutation.newInsertOrUpdateBuilder("T1")
            .set("K")
            .to(uniqueString())
            .set("V")
            .to(1)
            .build()),Options.excludeTxnFromChangeStreams());
    Timestamp t1 = Timestamp.now();
    client.executePartitionedUpdate(Statement.of("DELETE FROM T1 WHERE TRUE"),Options.excludeTxnFromChangeStreams());
    Timestamp t2 = Timestamp.now();
    assertEquals(numOfDataRecordsInRange(ExcludedChangeStream,t1,t2),0);
    assertEquals(numOfDataRecordsInRange(IncludedChangeStream,t1,t2),1);
  }

  @Test
  public void transactionRunnerCommit() {
    Timestamp t1 = Timestamp.now();
    TransactionRunner runner = client.readWriteTransaction(Options.excludeTxnFromChangeStreams());
    runner.run(
        transaction -> {
          transaction.buffer(Mutation.newInsertOrUpdateBuilder("T1")
              .set("K")
              .to("transactionRunnerCommit")
              .set("V")
              .to(1)
              .build());
          return null;
        });
    assertEquals(numOfDataRecordsInRange(ExcludedChangeStream,runner.getCommitTimestamp(),runner.getCommitTimestamp()),0);
    assertEquals(numOfDataRecordsInRange(IncludedChangeStream,runner.getCommitTimestamp(),runner.getCommitTimestamp()),1);
  }

  @Test
  public void transactionManagerCommit() {
    Timestamp t1 = Timestamp.now();
    try (TransactionManager manager =
        client.transactionManager(Options.excludeTxnFromChangeStreams())) {
      TransactionContext transaction = manager.begin();
      transaction.buffer(Mutation.newInsertOrUpdateBuilder("T1")
          .set("K")
          .to("transactionManagerCommit")
          .set("V")
          .to(1)
          .build());
      manager.commit();
      assertEquals(numOfDataRecordsInRange(ExcludedChangeStream,manager.getCommitTimestamp(),manager.getCommitTimestamp()),0);
      assertEquals(numOfDataRecordsInRange(IncludedChangeStream,manager.getCommitTimestamp(),manager.getCommitTimestamp()),1);
    }
  }

  @Test
  public void asyncTransactionRunnerCommit() {
    Timestamp t1 = Timestamp.now();
    try (AsyncTransactionManager manager =
        client.transactionManagerAsync(Options.excludeTxnFromChangeStreams())) {
      TransactionContextFuture transaction = manager.beginAsync();
      get(
          transaction
              .then(
                  (txn, input) -> {
                    txn.buffer(Mutation.newInsertOrUpdateBuilder("T1")
                        .set("K")
                        .to("asyncTransactionRunnerCommit")
                        .set("V")
                        .to(1)
                        .build());
                    return ApiFutures.immediateFuture(null);
                  },
                  executor)
              .commitAsync());

      Timestamp commitTs = manager.getCommitResponse().get().getCommitTimestamp();
      assertEquals(numOfDataRecordsInRange(ExcludedChangeStream,commitTs, commitTs),0);
      assertEquals(numOfDataRecordsInRange(IncludedChangeStream,commitTs,commitTs),1);
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void asyncTransactionManagerCommit() {
    Timestamp t1 = Timestamp.now();
    try (AsyncTransactionManager manager =
        client.transactionManagerAsync(Options.excludeTxnFromChangeStreams())) {
      TransactionContextFuture transaction = manager.beginAsync();
      get(
          transaction
              .then(
                  (txn, input) -> {
                    txn.buffer(Mutation.newInsertOrUpdateBuilder("T1")
                        .set("K")
                        .to("asyncTransactionManagerCommit")
                        .set("V")
                        .to(1)
                        .build());
                    return ApiFutures.immediateFuture(null);
                  },
                  executor)
              .commitAsync());
    }
  }
}


