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
package io.zeebe.logstreams.util;

import io.zeebe.logstreams.log.*;
import io.zeebe.test.util.TestUtil;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.junit.rules.ExternalResource;

public class LogStreamWriterRule extends ExternalResource {
  private LogStreamRule logStreamRule;

  private LogStream logStream;
  private LogStreamWriter logStreamWriter;

  public LogStreamWriterRule(final LogStreamRule logStreamRule) {
    this.logStreamRule = logStreamRule;
  }

  @Override
  protected void before() {
    this.logStream = logStreamRule.getLogStream();
    this.logStreamWriter = new LogStreamWriterImpl(logStream);
  }

  @Override
  protected void after() {
    logStreamWriter = null;
    logStream = null;
  }

  public void wrap(LogStreamRule rule) {
    this.logStream = rule.getLogStream();
    this.logStreamWriter.wrap(logStream);
  }

  public long writeEvents(final int count, final DirectBuffer event) {
    return writeEvents(count, event, false);
  }

  public long writeEvents(final int count, final DirectBuffer event, final boolean commit) {
    long lastPosition = -1;
    for (int i = 1; i <= count; i++) {
      final long key = i;
      lastPosition = writeEventInternal(w -> w.key(key).value(event));
    }

    waitForPositionToBeAppended(lastPosition);

    if (commit) {
      logStream.setCommitPosition(lastPosition);
    }

    return lastPosition;
  }

  public long writeEvent(final DirectBuffer event) {
    return writeEvent(event, false);
  }

  public long writeEvent(final DirectBuffer event, final boolean commit) {
    return writeEvent(w -> w.positionAsKey().value(event), commit);
  }

  public long writeEvent(final Consumer<LogStreamWriter> writer, final boolean commit) {
    final long position = writeEventInternal(writer);

    waitForPositionToBeAppended(position);

    if (commit) {
      logStream.setCommitPosition(position);
    }

    return position;
  }

  private long writeEventInternal(final Consumer<LogStreamWriter> writer) {
    long position;
    do {
      position = tryWrite(writer);
    } while (position == -1);

    return position;
  }

  public long tryWrite(final DirectBuffer value) {
    return tryWrite(w -> w.positionAsKey().value(value));
  }

  public long tryWrite(final long key, final DirectBuffer value) {
    return tryWrite(w -> w.key(key).value(value));
  }

  public long tryWrite(final Consumer<LogStreamWriter> writer) {
    writer.accept(logStreamWriter);

    return logStreamWriter.tryWrite();
  }

  public void waitForPositionToBeAppended(final long position) {
    TestUtil.waitUntil(
        () -> logStream.getLogStorageAppender().getCurrentAppenderPosition() > position,
        "Failed to wait for position {} to be appended",
        position);
  }
}
