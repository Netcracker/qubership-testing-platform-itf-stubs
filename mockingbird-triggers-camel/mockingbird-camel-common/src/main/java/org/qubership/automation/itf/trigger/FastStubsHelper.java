/*
 * # Copyright 2024-2025 NetCracker Technology Corporation
 * #
 * # Licensed under the Apache License, Version 2.0 (the "License");
 * # you may not use this file except in compliance with the License.
 * # You may obtain a copy of the License at
 * #
 * #      http://www.apache.org/licenses/LICENSE-2.0
 * #
 * # Unless required by applicable law or agreed to in writing, software
 * # distributed under the License is distributed on an "AS IS" BASIS,
 * # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * # See the License for the specific language governing permissions and
 * # limitations under the License.
 *
 */

package org.qubership.automation.itf.trigger;

import static org.qubership.automation.itf.trigger.camel.Helper.isTrue;

import java.util.Date;

import javax.jms.JMSException;
import javax.jms.TextMessage;

import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.qubership.atp.multitenancy.core.header.CustomHeader;
import org.qubership.automation.itf.core.model.condition.ConditionsHelper;
import org.qubership.automation.itf.core.model.jpa.context.InstanceContext;
import org.qubership.automation.itf.core.model.jpa.context.JsonContext;
import org.qubership.automation.itf.core.model.jpa.context.TcContext;
import org.qubership.automation.itf.core.model.jpa.instance.SituationInstance;
import org.qubership.automation.itf.core.model.jpa.instance.step.StepInstance;
import org.qubership.automation.itf.core.model.jpa.message.Message;
import org.qubership.automation.itf.core.stub.fast.ResponseDescription;
import org.qubership.automation.itf.core.stub.fast.StubEndpointConfig;
import org.qubership.automation.itf.core.util.config.Config;
import org.qubership.automation.itf.core.util.constants.Status;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.core.util.engine.TemplateEngineFactory;
import org.qubership.automation.itf.trigger.camel.ToReportingMessageSender;
import org.qubership.automation.itf.trigger.template.velocity.VelocityTemplateEngine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

public class FastStubsHelper {
    private static final ObjectMapper reportingObjectMapper;

    static {
        reportingObjectMapper = new ObjectMapper();
        reportingObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        reportingObjectMapper.disable(SerializationFeature.INDENT_OUTPUT);
        reportingObjectMapper.setFilterProvider(configureFilterProvider());
    }

    /**
     * Check responses' conditions.
     *
     * @param context - JsonContext of variables,
     * @param cfg - Stubs configuration,
     * @return ResponseDescription if conditions are evaluated to true, null otherwise.
     */
    public static ResponseDescription checkConditions(JsonContext context, StubEndpointConfig cfg) {

        if (cfg.getConditionalResponses() != null) {
            for (ResponseDescription responseDescription : cfg.getConditionalResponses()) {
                if (isTrue(responseDescription.getDisabled())) {
                    continue;
                }
                if (StringUtils.isNotEmpty(cfg.getOperationDefinitionKey())
                        && !cfg.getOperationDefinitionKey()
                                .equals(responseDescription.getResponseCondition().getOperationDefinitionKey())) {
                    continue;
                }
                if (ConditionsHelper.isApplicable(context,
                        responseDescription.getResponseCondition().getConditionParameters())) {
                    return responseDescription;
                }
            }
        }
        return null;
    }

    /**
     * Calculate skipReporting flag based on skipResponseReporting and skipEndpointReporting in priority.
     *
     * @param skipResponseReporting - skipReporting property on the Response level,
     * @param skipEndpointReporting - skipReporting property on the Endpoint level,
     * @return - true if reporting should be skipped, otherwise false.
     */
    public static boolean isReportingSkipped(Boolean skipResponseReporting, Boolean skipEndpointReporting) {
        return skipResponseReporting != null
                ? skipResponseReporting : BooleanUtils.toBooleanDefaultIfNull(skipEndpointReporting, false);
    }

    /**
     * Create text message to reporting queue.
     *
     * @param text - message body,
     * @param time - timestamp,
     * @param id - id of message object,
     * @param type - type of message,
     * @param tenantId - project Uuid,
     * @return created and configured ActiveMQTextMessage,
     * @throws JMSException - in case exceptions are faced while message composing.
     */
    public static TextMessage createTextMessage(String text, long time, String id, String type, String tenantId)
            throws JMSException {
        ActiveMQTextMessage message = new ActiveMQTextMessage();
        message.setText(text);
        message.setLongProperty("Time", time);
        message.setStringProperty("ObjectID", id);
        message.setStringProperty("ObjectType", type);
        message.setStringProperty(CustomHeader.X_PROJECT_ID, tenantId);
        return message;
    }

    /**
     * Compose combined message and send it to reporting.
     *
     * @param incoming - incoming message,
     * @param outgoing - outgoing message,
     * @param triggerDescriptor - trigger configuration descriptor,
     * @param startTime - start time of processing,
     * @param endTime - end time of processing,
     * @param parsedContext - context of variables,
     * @param endPoint - configured endpoint,
     * @throws JsonProcessingException - in case JSON serialization exceptions,
     * @throws JMSException - in case exceptions while message composing and sending.
     */
    public static void sendMessageToReport(Message incoming,
                                            Message outgoing,
                                            StorableDescriptor triggerDescriptor,
                                            Date startTime,
                                            Date endTime,
                                            JsonContext parsedContext,
                                            String endPoint,
                                           ResponseDescription responseDescription)
            throws JsonProcessingException, JMSException {
        TcContext tcContext = new TcContext();
        InstanceContext instanceContext = new InstanceContext();

        String from = "Unknown";
        if (incoming.getHeaders() != null
                && incoming.getHeaders().get("remoteHost") != null
                && StringUtils.isNotEmpty(incoming.getHeaders().get("remoteHost").toString())) {
            from = incoming.getHeaders().get("remoteHost").toString();
        }

        SituationInstance situationInstance = new SituationInstance();
        situationInstance.setContext(instanceContext);
        situationInstance.setParentContext(tcContext);
        situationInstance.setName(String.format("From %s to %s", from, endPoint));
        situationInstance.setStartTime(startTime);
        situationInstance.setEndTime(endTime);
        situationInstance.setStatus(Status.PASSED);

        instanceContext.setTC(tcContext);
        String situationName = ((VelocityTemplateEngine) TemplateEngineFactory.get())
                .process(responseDescription.getName(), parsedContext);
        StepInstance stepInstance = new StepInstance();
        stepInstance.setIncomingMessage(incoming);
        stepInstance.setOutgoingMessage(outgoing);
        stepInstance.setParent(situationInstance);
        stepInstance.setName(situationName);
        stepInstance.setStartTime(startTime);
        stepInstance.setEndTime(endTime);
        stepInstance.setStatus(Status.PASSED);

        tcContext.setName(situationName);
        tcContext.setID(IdGenerator.nextId());
        tcContext.setProjectId(triggerDescriptor.getProjectId());
        tcContext.setProjectUuid(triggerDescriptor.getProjectUuid());
        tcContext.setStatus(Status.PASSED);
        tcContext.setStartTime(startTime);
        tcContext.setEndTime(endTime);
        tcContext.putAll(parsedContext);

        String combinedMessage = "{\"TcContext\":" + reportingObjectMapper.writeValueAsString(tcContext)
                + ", \"SituationInstance\":" + reportingObjectMapper.writeValueAsString(situationInstance)
                + ", \"StepInstance\":" + reportingObjectMapper.writeValueAsString(stepInstance)
                + "}";

        TextMessage preparedMessage = FastStubsHelper.createTextMessage(
                combinedMessage,
                System.currentTimeMillis(),
                Config.getConfig().getRunningHostname() + "/" + tcContext.getID(),
                "combinedFastStubMessage",
                triggerDescriptor.getProjectUuid().toString());
        ToReportingMessageSender.sendMessageToReportingQueueStatic(preparedMessage);
    }

    private static SimpleFilterProvider configureFilterProvider() {
        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider.addFilter("reportWorkerFilter_InstanceContext",
                SimpleBeanPropertyFilter.serializeAllExcept("transport", "version", "history", "collectHistory",
                        "prefix", "description", "empty"));
        filterProvider.addFilter("reportWorkerFilter_TCContext",
                SimpleBeanPropertyFilter.serializeAllExcept("version", "history", "collectHistory", "prefix",
                        "description", "empty", "lastAccess", "needToReportToATP", "runStepByStep", "running",
                        "finished", "runnable", "parent"));
        filterProvider.addFilter("reportWorkerFilter_SPContext",
                SimpleBeanPropertyFilter.serializeAllExcept("version", "history", "collectHistory", "prefix",
                        "description", "empty"));
        filterProvider.addFilter("reportWorkerFilter_MessageParameter",
                SimpleBeanPropertyFilter.serializeAllExcept("prefix", "description", "name", "autosave", "version"));
        filterProvider.addFilter("reportWorkerFilter_CallChainInstance",
                SimpleBeanPropertyFilter.serializeAllExcept("prefix", "description", "datasetDefault", "running",
                        "finished", "transportConfiguration", "version"));
        filterProvider.addFilter("reportWorkerFilter_SituationInstance",
                SimpleBeanPropertyFilter.serializeAllExcept("prefix", "description", "running", "finished",
                        "transportConfiguration", "version"));
        filterProvider.addFilter("reportWorkerFilter_StepInstance",
                SimpleBeanPropertyFilter.serializeAllExcept("prefix", "description", "running", "finished", "version",
                        "step"));
        filterProvider.addFilter("reportWorkerFilter_Message",
                SimpleBeanPropertyFilter.serializeAllExcept("name", "parent", "prefix", "description", "file",
                        "transportProperties", "failedMessage", "version"));
        return filterProvider;
    }

    /**
     * Evaluate OperationDefinitionKey against context.
     *
     * @param cfg - Stub Endpoint Config,
     * @param context - context object.
     */
    public static void recalculateOperationDefinitionKey(StubEndpointConfig cfg, JsonContext context) {
        cfg.setOperationDefinitionKey(null);
        if (StringUtils.isNotEmpty(cfg.getOperationDefinitionScript())) {
            cfg.setOperationDefinitionKey(
                ((VelocityTemplateEngine) TemplateEngineFactory.get())
                        .process(cfg.getOperationDefinitionScript(), context).trim()
            );
        }
    }

}
