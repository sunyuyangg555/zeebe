/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.snapshot.benchmarks;

import static io.zeebe.map.ZbMap.DEFAULT_BLOCK_COUNT;

import io.zeebe.logstreams.snapshot.ComposedSnapshot;
import io.zeebe.logstreams.snapshot.ZbMapSnapshotSupport;
import io.zeebe.map.Long2LongZbMap;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;
import org.openjdk.jmh.annotations.*;

/** */
@State(Scope.Benchmark)
public class WrittenMapSnapshotSupplier {
  private Long2LongZbMap map;

  File tmpFile;
  ComposedSnapshot composedZbMapSnapshot;

  @Setup(Level.Iteration)
  public void writeMapSnapshot() throws Exception {
    tmpFile = new File("recoverIdxSnapshot-benchmark.txt");
    map = new Long2LongZbMap(Benchmarks.DATA_SET_SIZE / DEFAULT_BLOCK_COUNT, DEFAULT_BLOCK_COUNT);

    final Random random = new Random();
    for (int idx = 0; idx < Benchmarks.DATA_SET_SIZE; idx++) {
      map.put(idx, Math.min(Math.abs(random.nextLong()), Benchmarks.DATA_SET_SIZE - 1));
    }
    composedZbMapSnapshot = new ComposedSnapshot(new ZbMapSnapshotSupport<>(map));

    composedZbMapSnapshot.writeSnapshot(new FileOutputStream(tmpFile));
  }

  @TearDown(Level.Iteration)
  public void closeMap() {
    map.close();
    tmpFile.delete();
  }
}
