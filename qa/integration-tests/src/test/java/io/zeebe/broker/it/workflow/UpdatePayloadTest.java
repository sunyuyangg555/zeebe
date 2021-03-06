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
package io.zeebe.broker.it.workflow;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.*;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.events.WorkflowInstanceState;
import io.zeebe.client.cmd.BrokerErrorException;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class UpdatePayloadTest {
  private static final String PAYLOAD = "{\"foo\":\"bar\"}";

  private static final WorkflowDefinition WORKFLOW =
      Bpmn.createExecutableWorkflow("process")
          .startEvent("start")
          .serviceTask("task-1", t -> t.taskType("task-1").output("$.result", "$.result"))
          .endEvent("end")
          .done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientRule clientRule = new ClientRule();
  public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(brokerRule).around(clientRule).around(eventRecorder);

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void init() {
    clientRule
        .getWorkflowClient()
        .newDeployCommand()
        .addWorkflowModel(WORKFLOW, "workflow.bpmn")
        .send()
        .join();

    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .send()
        .join();
  }

  @Test
  public void shouldUpdatePayloadWhenActivityIsActivated() {
    // given
    waitUntil(
        () -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_ACTIVATED));

    final WorkflowInstanceEvent activtyInstance =
        eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_ACTIVATED);

    // when
    final WorkflowInstanceEvent payloadUpdated =
        clientRule
            .getWorkflowClient()
            .newUpdatePayloadCommand(activtyInstance)
            .payload(PAYLOAD)
            .send()
            .join();

    // then
    waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.PAYLOAD_UPDATED));

    assertThat(payloadUpdated.getState()).isEqualTo(WorkflowInstanceState.PAYLOAD_UPDATED);
    assertThat(payloadUpdated.getPayload()).isEqualTo(PAYLOAD);
    assertThat(payloadUpdated.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldUpdateWithNullPayload() {
    // given
    waitUntil(
        () -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_ACTIVATED));

    final WorkflowInstanceEvent activtyInstance =
        eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_ACTIVATED);

    // when
    final WorkflowInstanceEvent payloadUpdated =
        clientRule
            .getWorkflowClient()
            .newUpdatePayloadCommand(activtyInstance)
            .payload("null")
            .send()
            .join();

    // then
    waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.PAYLOAD_UPDATED));

    assertThat(payloadUpdated.getState()).isEqualTo(WorkflowInstanceState.PAYLOAD_UPDATED);
    assertThat(payloadUpdated.getPayload()).isEqualTo("{}");
    assertThat(payloadUpdated.getPayloadAsMap()).isEmpty();
  }

  @Test
  public void shouldThrowExceptionOnUpdateWithInvalidPayload() {
    // given
    waitUntil(
        () -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_ACTIVATED));

    final WorkflowInstanceEvent activtyInstance =
        eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_ACTIVATED);

    // when
    final Throwable throwable =
        catchThrowable(
            () ->
                clientRule
                    .getWorkflowClient()
                    .newUpdatePayloadCommand(activtyInstance)
                    .payload("[]")
                    .send()
                    .join());

    // then
    assertThat(throwable).isInstanceOf(BrokerErrorException.class);
    assertThat(throwable.getMessage()).contains("Could not read property 'payload'.");
    assertThat(throwable.getMessage())
        .contains("Document has invalid format. On root level an object is only allowed.");
  }

  @Test
  public void shouldUpdatePayloadAndCompleteJobAfterwards() {
    // given
    waitUntil(
        () -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_ACTIVATED));
    final WorkflowInstanceEvent activtyInstance =
        eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_ACTIVATED);
    clientRule
        .getWorkflowClient()
        .newUpdatePayloadCommand(activtyInstance)
        .payload(PAYLOAD)
        .send()
        .join();
    waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.PAYLOAD_UPDATED));

    // when
    clientRule
        .getJobClient()
        .newWorker()
        .jobType("task-1")
        .handler(
            (client, job) -> client.newCompleteCommand(job).payload("{\"result\": \"ok\"}").send())
        .open();

    // then
    waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.COMPLETED));
    final WorkflowInstanceEvent wfEvent =
        eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.COMPLETED);
    assertThat(wfEvent.getPayload()).isEqualTo("{\"foo\":\"bar\",\"result\":\"ok\"}");
    assertThat(wfEvent.getPayloadAsMap())
        .hasSize(2)
        .contains(entry("foo", "bar"))
        .contains(entry("result", "ok"));
  }

  @Test
  public void shouldUpdatePayloadWithMap() {
    // given
    waitUntil(
        () -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_ACTIVATED));

    final WorkflowInstanceEvent activtyInstance =
        eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_ACTIVATED);

    // when
    final WorkflowInstanceEvent event =
        clientRule
            .getWorkflowClient()
            .newUpdatePayloadCommand(activtyInstance)
            .payload(Collections.singletonMap("foo", "bar"))
            .send()
            .join();

    // then
    waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.PAYLOAD_UPDATED));

    assertThat(event.getState()).isEqualTo(WorkflowInstanceState.PAYLOAD_UPDATED);
    assertThat(event.getPayload()).isEqualTo(PAYLOAD);
    assertThat(event.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldUpdatePayloadWithObject() {
    // given
    final PayloadObject newPayload = new PayloadObject();
    newPayload.foo = "bar";

    waitUntil(
        () -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_ACTIVATED));

    final WorkflowInstanceEvent activtyInstance =
        eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_ACTIVATED);

    // when
    final WorkflowInstanceEvent event =
        clientRule
            .getWorkflowClient()
            .newUpdatePayloadCommand(activtyInstance)
            .payload(newPayload)
            .send()
            .join();

    // then
    waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.PAYLOAD_UPDATED));

    assertThat(event.getState()).isEqualTo(WorkflowInstanceState.PAYLOAD_UPDATED);
    assertThat(event.getPayload()).isEqualTo(PAYLOAD);
    assertThat(event.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
  }

  @Test
  public void shouldFailUpdatePayloadIfWorkflowInstanceIsCompleted() {
    // given
    clientRule
        .getJobClient()
        .newWorker()
        .jobType("task-1")
        .handler(
            (client, job) ->
                client.newCompleteCommand(job).payload("{\"result\": \"done\"}").send())
        .open();

    waitUntil(
        () -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_COMPLETED));

    final WorkflowInstanceEvent activityInstance =
        eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_ACTIVATED);

    // then
    thrown.expect(ClientCommandRejectedException.class);
    thrown.expectMessage(
        "Command (UPDATE_PAYLOAD) for event with key "
            + activityInstance.getMetadata().getKey()
            + " was rejected");

    // when
    clientRule
        .getWorkflowClient()
        .newUpdatePayloadCommand(activityInstance)
        .payload(PAYLOAD)
        .send()
        .join();
  }

  public static class PayloadObject {
    public String foo;
  }
}
