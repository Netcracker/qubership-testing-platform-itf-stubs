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

package org.qubership.automation.itf.trigger.kafka;

import static org.qubership.automation.itf.core.util.constants.PropertyConstants.Kafka.BROKERS;
import static org.qubership.automation.itf.core.util.constants.PropertyConstants.Kafka.GROUP;
import static org.qubership.automation.itf.core.util.constants.PropertyConstants.Kafka.TOPIC;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaComponent;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.commons.lang3.StringUtils;
import org.qubership.automation.itf.JvmSettings;
import org.qubership.automation.itf.communication.TriggerExecutionMessageSender;
import org.qubership.automation.itf.core.model.communication.TransportType;
import org.qubership.automation.itf.core.model.communication.message.CommonTriggerExecutionMessage;
import org.qubership.automation.itf.core.model.jpa.message.Message;
import org.qubership.automation.itf.core.model.transport.ConnectionProperties;
import org.qubership.automation.itf.core.util.constants.PropertyConstants;
import org.qubership.automation.itf.core.util.descriptor.StorableDescriptor;
import org.qubership.automation.itf.core.util.exception.TriggerException;
import org.qubership.automation.itf.monitoring.metrics.MetricsAggregateService;
import org.qubership.automation.itf.trigger.camel.Helper;
import org.qubership.automation.itf.trigger.camel.inbound.AbstractCamelTrigger;
import org.qubership.automation.itf.trigger.camel.route.ItfAbstractRouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaTrigger extends AbstractCamelTrigger {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaTrigger.class);
    private static final String KAFKA_INBOUND_TRANSPORT_CLASS_NAME =
            "org.qubership.automation.itf.transport.kafka.inbound.KafkaInboundTransport";

    private static final Integer RECONNECT_BACKOFF_MS_MAX_DEFAULT = 300000;
    private static final Integer RECONNECT_BACKOFF_MS_MAX_EXTRA_MIN = 10000;
    private static final Integer RECONNECT_BACKOFF_MS_DEFAULT = 500;
    private static final Integer RECONNECT_BACKOFF_MS_EXTRA_MIN = 500;

    private static final List<String> AUTHORIZATION_PARAMS
            = Arrays.asList("securityProtocol", "saslMechanism", "saslModule", "saslUsername", "saslPassword");

    public KafkaTrigger(StorableDescriptor triggerConfigurationDescriptor, ConnectionProperties connectionProperties) {
        super(triggerConfigurationDescriptor, connectionProperties);
    }

    @Override
    protected void activateSpecificTrigger() throws Exception {
        forceDeactivateInCaseOfComponentExistence();
        KafkaComponent kafkaComponent = new KafkaComponent(CAMEL_CONTEXT);
        CAMEL_CONTEXT.addComponent(getId(), kafkaComponent);
        CAMEL_CONTEXT.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                Endpoint kafkaEndpoint = createEndPoint(kafkaComponent);
                UUID projectUuid = getTriggerConfigurationDescriptor().getProjectUuid();
                String brokerMessageSelectorValue = Helper.getBrokerMessageSelectorValue();
                from(kafkaEndpoint).process(exchange -> {
                    if (exchange.getIn() != null) {
                        String sessionId = UUID.randomUUID().toString();
                        MetricsAggregateService.putCommonMetrics(projectUuid, sessionId);
                        LOGGER.info("Project: {}. SessionId: {}. Request is received by endpoint: {}",
                                projectUuid, sessionId, kafkaEndpoint.getEndpointUri());
                        String data = exchange.getIn().getBody(String.class);
                        if (!StringUtils.isEmpty(data)) {
                            Message message = new Message(data);
                            message.convertAndSetHeaders(exchange.getIn().getHeaders());
                            MetricsAggregateService.checkIncomingMessageSize(projectUuid, message.getText());
                            ItfAbstractRouteBuilder.logExtendedInfo(projectUuid, sessionId, brokerMessageSelectorValue,
                                    KAFKA_INBOUND_TRANSPORT_CLASS_NAME,
                                    message.getText().getBytes(JvmSettings.CHARSET).length);
                            TriggerExecutionMessageSender.send(new CommonTriggerExecutionMessage(
                                    KAFKA_INBOUND_TRANSPORT_CLASS_NAME, message,
                                    getTriggerConfigurationDescriptor(), sessionId,
                                    brokerMessageSelectorValue), getTriggerConfigurationDescriptor().getProjectUuid());
                            MetricsAggregateService
                                    .incrementIncomingRequestToProject(projectUuid, TransportType.KAFKA_INBOUND, true);
                            LOGGER.debug("Project: {}, SessionId: {}, transport: '{}'"
                                            + " - message to executor is sent.", projectUuid, sessionId,
                                    KAFKA_INBOUND_TRANSPORT_CLASS_NAME);
                        }
                    }
                }).routeId(getId())
                        .routeDescription(projectUuid.toString())
                        .group(TransportType.KAFKA_INBOUND.name());
            }
        });
        LOGGER.info("CAMEL_CONTEXT [{}] is activated successfully", getId());
    }

    @Override
    protected void deactivateSpecificTrigger() throws Exception {
        CAMEL_CONTEXT.stopRoute(getId());
        CAMEL_CONTEXT.removeRoute(getId());
        CAMEL_CONTEXT.removeComponent(getId());
        LOGGER.info("CAMEL_CONTEXT [{}] is deactivated successfully", getId());
    }

    @Override
    protected void applyTriggerProperties(ConnectionProperties connectionProperties) throws TriggerException {
        setConnectionProperties(connectionProperties);
    }

    private void forceDeactivateInCaseOfComponentExistence() {
        Component prevAdded = CAMEL_CONTEXT.hasComponent(getId());
        if (prevAdded != null) {
            try {
                LOGGER.info("Before activation: {} - CAMEL_CONTEXT already has component with the same ID {}"
                        + ". It will be deactivated.", getTriggerConfigurationDescriptor().getId(), getId());
                deactivateSpecificTrigger();
            } catch (Exception ex) {
                // Do nothing. If there are errors preventing activation let them appear while activation itself
                LOGGER.debug("Trigger {} deactivation before its activation - ignoring the exception: ",
                        getId(), ex);
            }
        }
    }

    /*
     *   Method constructs endpoint URI string (1),
     *   then creates KafkaEndpoint based on the endpoint URI string (2),
     *   then sets endpoint configuration properties based on trigger connection properties (3).
     *
     *   Actions #2 and #3 possibly duplicate each other,
     *   but the 1st successful implementation of Kafka trigger worked on such principles.
     *   So, let's go the same way...
     * */
    private Endpoint createEndPoint(KafkaComponent kafkaComponent) {
        String topic = ((String) getConnectionProperties().get(TOPIC)).trim();
        String brokers = ((String) getConnectionProperties().get(BROKERS)).trim();
        String group = (String) getConnectionProperties().get(GROUP); // 'group' is optional
        Map<String, Object> extraProps = Helper.setExtraPropertiesMap(getConnectionProperties()
                .obtain(PropertyConstants.Commons.ENDPOINT_PROPERTIES));

        boolean isAuthParametersValid = checkAuthParameters(extraProps);
        StringBuilder builder = new StringBuilder("kafka:").append(topic).append("?brokers=").append(brokers);
        if (!StringUtils.isBlank(group)) {
            builder.append("&groupId=").append(group.trim());
        }
        Map<String, String> authProps = isAuthParametersValid ? fillAuthParameters(extraProps) : null;

        String endpointUri = builder.append(Helper.setExtraProperties(extraProps)).toString();
        KafkaEndpoint endpoint = new KafkaEndpoint(endpointUri, kafkaComponent);
        endpoint.getConfiguration().setTopic(topic);
        endpoint.getConfiguration().setBrokers(brokers);
        endpoint.getConfiguration().setClientId(getTriggerConfigurationDescriptor().getProjectUuid().toString());
        endpoint.getConfiguration().setReconnectBackoffMs(RECONNECT_BACKOFF_MS_DEFAULT);
        endpoint.getConfiguration().setReconnectBackoffMaxMs(RECONNECT_BACKOFF_MS_MAX_DEFAULT);
        if (isAuthParametersValid) {
            setAuthParameters(endpoint, authProps);
        }
        if (!StringUtils.isBlank(group)) {
            endpoint.getConfiguration().setGroupId(group.trim());
        }
        setKafkaConfigurationProperties(endpoint.getConfiguration(), extraProps);
        return endpoint;
    }

    /*
     *   Set KafkaConfiguration properties based on 'Extra Endpoint properties' property
     *       All 'common' and 'consumer' properties are processed, except already set in caller method.
     *       Properties are according:
     *           http://kafka.apache.org/documentation.html#consumerconfigs and
     *           https://camel.apache.org/components/3.7.x/kafka-component.html
     *
     *       Properties and decisions are listed below:
     *           - additionalProperties (common) - there is no corresponding method
     *           ++ brokers (common)
     *           + clientId (common)
     *           - headerFilterStrategy (common) - Complex object, don't allow users to configure it
     *           + reconnectBackoffMaxMs (common)
     *           - shutdownTimeout (common) - there is no corresponding method
     *           + allowManualCommit (consumer)
     *           + autoCommitEnable (consumer)
     *           + autoCommitIntervalMs (consumer)
     *           + autoCommitOnStop (consumer)
     *           + autoOffsetReset (consumer)
     *           + breakOnFirstError (consumer)
     *           - bridgeErrorHandler (consumer)
     *       Properties at endpoint level, not at configuration. Do not implement them.
     *           + checkCrcs (consumer)
     *           + consumerRequestTimeoutMs (consumer)
     *           + consumersCount (consumer)
     *           + consumerStreams (consumer)
     *           + fetchMaxBytes (consumer)
     *           + fetchMinBytes (consumer)
     *           + fetchWaitMaxMs (consumer)
     *           ++ groupId (consumer)
     *           - headerDeserializer (consumer)
     *       Not for endpoint, nor for configuration. Do not implement them.
     *           + heartbeatIntervalMs (consumer)
     *           + keyDeserializer (consumer)
     *           + maxPartitionFetchBytes (consumer)
     *           + maxPollIntervalMs (consumer)
     *           + maxPollRecords (consumer)
     *           - offsetRepository (consumer) - Complex object, don't allow users to configure it
     *           + partitionAssignor (consumer)
     *           + pollTimeoutMs (consumer)
     *           + seekTo (consumer)
     *           + sessionTimeoutMs (consumer)
     *           - specificAvroReader (consumer) - there is no corresponding method
     *           + topicIsPattern (consumer)
     *           + valueDeserializer (consumer)
     *           - exceptionHandler (consumer) - not for configuration. Do not implement them.
     *           - exchangePattern (consumer) - not for configuration. Do not implement them.
     * */
    private void setKafkaConfigurationProperties(KafkaConfiguration configuration, Map<String, Object> extraProps) {
        for (Map.Entry<String, Object> prop : extraProps.entrySet()) {
            switch (prop.getKey()) {
                case "autoOffsetReset":
                case "auto.offset.reset":
                    configuration.setAutoOffsetReset(prop.getValue().toString());
                    break;
                case "maxPollRecords":
                case "max.poll.records":
                    configuration.setMaxPollRecords(Integer.parseInt(prop.getValue().toString()));
                    break;
                case "clientId":
                case "client.id":
                    configuration.setClientId(prop.getValue().toString());
                    break;
                case "reconnectBackoffMaxMs":
                case "reconnect.backoff.max.ms":
                    int intMaxMsValue = Integer.parseInt(prop.getValue().toString());
                    if (intMaxMsValue < RECONNECT_BACKOFF_MS_MAX_EXTRA_MIN) {
                        LOGGER.warn("reconnectBackoffMaxMs parameter less than minimum allowed value {}."
                                        + " Default value {} will be used.", RECONNECT_BACKOFF_MS_MAX_EXTRA_MIN,
                                RECONNECT_BACKOFF_MS_MAX_DEFAULT);
                    } else {
                        configuration.setReconnectBackoffMaxMs(intMaxMsValue);
                    }
                    break;
                case "reconnectBackoffMs":
                case "reconnect.backoff.ms":
                    int intMsValue = Integer.parseInt(prop.getValue().toString());
                    if (intMsValue < RECONNECT_BACKOFF_MS_EXTRA_MIN) {
                        LOGGER.warn("reconnectBackoffMs parameter less than minimum allowed value {}."
                                + " Default value {} will be used.", RECONNECT_BACKOFF_MS_EXTRA_MIN,
                                RECONNECT_BACKOFF_MS_DEFAULT);
                    } else {
                        configuration.setReconnectBackoffMs(intMsValue);
                    }
                    break;
                case "autoCommitEnable":
                case "enable.auto.commit":
                    configuration.setAutoCommitEnable(Boolean.parseBoolean(prop.getValue().toString()));
                    break;
                case "allowManualCommit":
                    configuration.setAllowManualCommit(Boolean.parseBoolean(prop.getValue().toString()));
                    break;
                case "autoCommitIntervalMs":
                case "auto.commit.interval.ms":
                    configuration.setAutoCommitIntervalMs(Integer.parseInt(prop.getValue().toString()));
                    break;
                case "autoCommitOnStop":
                    configuration.setAutoCommitOnStop(prop.getValue().toString());
                    break;
                case "breakOnFirstError":
                    configuration.setBreakOnFirstError(Boolean.parseBoolean(prop.getValue().toString()));
                    break;
                case "checkCrcs":
                case "check.crcs":
                    configuration.setCheckCrcs(Boolean.parseBoolean(prop.getValue().toString()));
                    break;
                case "consumerRequestTimeoutMs":
                case "request.timeout.ms":
                    configuration.setRequestTimeoutMs(Integer.parseInt(prop.getValue().toString()));
                    break;
                case "consumersCount":
                    configuration.setConsumersCount(Integer.parseInt(prop.getValue().toString()));
                    break;
                case "consumerStreams":
                    configuration.setConsumerStreams(Integer.parseInt(prop.getValue().toString()));
                    break;
                case "fetchMaxBytes":
                case "fetch.max.bytes":
                    configuration.setFetchMaxBytes(Integer.parseInt(prop.getValue().toString()));
                    break;
                case "fetchMinBytes":
                case "fetch.min.bytes":
                    configuration.setFetchMinBytes(Integer.parseInt(prop.getValue().toString()));
                    break;
                case "fetchWaitMaxMs":
                case "fetch.max.wait.ms":
                    configuration.setFetchWaitMaxMs(Integer.parseInt(prop.getValue().toString()));
                    break;
                case "heartbeatIntervalMs":
                case "heartbeat.interval.ms":
                    configuration.setHeartbeatIntervalMs(Integer.parseInt(prop.getValue().toString()));
                    break;
                case "keyDeserializer":
                case "key.deserializer":
                    configuration.setKeyDeserializer(prop.getValue().toString());
                    break;
                case "maxPartitionFetchBytes":
                case "max.partition.fetch.bytes":
                    configuration.setMaxPartitionFetchBytes(Integer.parseInt(prop.getValue().toString()));
                    break;
                case "partitionAssignor":
                case "partition.assignment.strategy":
                    configuration.setPartitionAssignor(prop.getValue().toString());
                    break;
                case "pollTimeoutMs":
                    configuration.setPollTimeoutMs(Long.parseLong(prop.getValue().toString()));
                    break;
                case "maxPollIntervalMs":
                case "max.poll.interval.ms":
                    configuration.setMaxPollIntervalMs(Long.parseLong(prop.getValue().toString()));
                    break;
                case "seekTo":
                    configuration.setSeekTo(prop.getValue().toString());
                    break;
                case "sessionTimeoutMs":
                case "session.timeout.ms":
                    configuration.setSessionTimeoutMs(Integer.parseInt(prop.getValue().toString()));
                    break;
                case "topicIsPattern":
                    configuration.setTopicIsPattern(Boolean.parseBoolean(prop.getValue().toString()));
                    break;
                case "valueDeserializer":
                case "value.deserializer":
                    configuration.setValueDeserializer(prop.getValue().toString());
                    break;
                default:
            }
        }
    }

    private void setAuthParameters(KafkaEndpoint endpoint, Map<String, String> authProps) {
        if (!authProps.isEmpty()) {
            String saslJaasConfig = String.format("%s required username=\"%s\" password=\"%s\";",
                    authProps.get("saslModule"), authProps.get("saslUsername"), authProps.get("saslPassword"));
            endpoint.getConfiguration().setSaslJaasConfig(saslJaasConfig);
            endpoint.getConfiguration().setSaslMechanism(authProps.get("saslMechanism"));
            endpoint.getConfiguration().setSecurityProtocol(authProps.get("securityProtocol"));
        }
    }

    private Map<String, String> fillAuthParameters(Map<String,Object> extraProps) {
        Map<String, String> authProps = new HashMap<>();
        for (String param : AUTHORIZATION_PARAMS) {
            authProps.put(param, extraProps.get(param).toString());
            extraProps.remove(param);
        }
        return authProps;
    }

    private boolean checkAuthParameters(Map<String, Object> extraProps) {
        for (String param : AUTHORIZATION_PARAMS) {
            if (!extraProps.containsKey(param)) {
                return false;
            }
        }
        return true;
    }
}
