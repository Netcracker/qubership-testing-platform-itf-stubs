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

package org.qubership.automation.itf.monitoring.metrics;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import org.qubership.atp.integration.configuration.mdc.MdcUtils;
import org.qubership.automation.itf.JvmSettings;
import org.qubership.automation.itf.core.model.communication.TransportType;
import org.qubership.automation.itf.core.util.config.Config;
import org.qubership.automation.itf.core.util.mdc.MdcField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MetricsAggregateService {

    private static final int MAX_SIZE =
            Config.getConfig().getIntOrDefault("logging.incoming.request.message.max.size",5242880);
    private static int MAX_SIZE_TO_MB = MAX_SIZE / (1024 * 1024);

    private Counter.Builder stubsActiveTriggerCounter;
    private Counter.Builder stubsErrorTriggerCounter;
    private static Counter.Builder stubsIncomingMessageSizeCounter;
    private static Counter.Builder stubsIncomingRequestCounter;
    private static MeterRegistry meterRegistry;

    /**
     * Constructor.
     *
     * @param meterRegistry - registry of metrics.
     */
    @Autowired
    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD",
            justification = "No way due to usage inside Camel .process() method")
    public MetricsAggregateService(MeterRegistry meterRegistry) {
        MetricsAggregateService.meterRegistry = meterRegistry;
        stubsActiveTriggerCounter = Counter.builder(Metric.ATP_ITF_STUBS_ACTIVE_TRIGGER_BY_PROJECT.getValue())
                .description("total number of active trigger");
        stubsErrorTriggerCounter = Counter.builder(Metric.ATP_ITF_STUBS_ERROR_TRIGGER_BY_PROJECT.getValue())
                .description("total number of error trigger");
        stubsIncomingMessageSizeCounter = Counter.builder(Metric.ATP_ITF_STUBS_INCOMING_REQUEST_MESSAGE_SIZE_BY_PROJECT
                        .getValue())
                .description("total number of incoming message size");
        stubsIncomingRequestCounter = Counter.builder(Metric.ATP_ITF_STUBS_INCOMING_REQUEST_BY_PROJECT.getValue())
                .description("total number of incoming request");
    }

    /**
     * Increment requests counter for metric and projectUuid.
     *
     * @param projectUuid - project Uuid,
     * @param metric - metric to increment.
     */
    public void incrementRequestToProject(@NonNull UUID projectUuid, @NonNull Metric metric) {
        requestToProject(projectUuid, metric);
    }

    /**
     * Increment requests counter by projectUuid, transportType and execution result.
     *
     * @param projectUuid - project Uuid,
     * @param transportType - transport Type,
     * @param result - result of request processing, true/false.
     */
    public static void incrementIncomingRequestToProject(@NonNull UUID projectUuid,
                                                         @NonNull TransportType transportType,
                                                         boolean result) {
        stubsIncomingRequestCounter
                .tag(MetricTag.PROJECT.getValue(), projectUuid.toString())
                .tag(MetricTag.TRANSPORT_TYPE.getValue(), transportType.name())
                .tag(MetricTag.RESULT.getValue(), String.valueOf(result))
                .register(meterRegistry)
                .increment();
    }

    /**
     * Increment requests counter by projectUuid, having message size above threshold.
     *
     * @param projectUuid - project Uuid,
     * @param message - incoming message body.
     */
    public static void checkIncomingMessageSize(UUID projectUuid, String message) {
        if (Objects.isNull(message)) {
            return;
        }
        checkIncomingMessageSize(projectUuid, message.getBytes(JvmSettings.CHARSET).length);
    }

    /**
     * Increment requests counter by projectUuid, having message size above threshold.
     *
     * @param projectUuid - project Uuid,
     * @param length - length of incoming message body.
     */
    public static void checkIncomingMessageSize(UUID projectUuid, long length) {
        if (length >= MAX_SIZE) {
            log.warn("Received message is more or equal to {} Mb, project UUID:{}, message size: {} bytes.",
                    MAX_SIZE_TO_MB, projectUuid, length);
            stubsIncomingMessageSizeCounter
                    .tag(MetricTag.PROJECT.getValue(), projectUuid.toString())
                    .register(meterRegistry)
                    .increment();
        }
    }

    /**
     * Record inbound request duration by projectUuid, transportType and endPoint.
     *
     * @param projectUuid - project Uuid,
     * @param transportType - transport Type,
     * @param endPoint - configured endpoint,
     * @param duration - duration of message processing.
     */
    public static void recordIncomingRequestDuration(@NonNull UUID projectUuid, @NonNull TransportType transportType,
                                                     @NonNull String endPoint,  @NonNull Duration duration) {
        meterRegistry.timer(Metric.ATP_ITF_STUBS_INCOMING_REQUEST_SECONDS_BY_PROJECT.getValue(),
                            MetricTag.PROJECT.getValue(), projectUuid.toString(),
                            MetricTag.TRANSPORT_TYPE.getValue(), transportType.name(),
                            MetricTag.ENDPOINT.getValue(), endPoint)
                     .record(duration);
    }

    /**
     * Put PROJECT_ID and SESSION_ID into MDC context.
     *
     * @param projectId - project Uuid,
     * @param sessionId - session id for logging purposes.
     */
    public static void putCommonMetrics(UUID projectId, String sessionId) {
        MdcUtils.put(MdcField.PROJECT_ID.toString(), projectId);
        MdcUtils.put(MdcField.SESSION_ID.toString(), sessionId);
    }

    private void requestToProject(UUID projectUuid, Metric metric) {
        switch (metric) {
            case ATP_ITF_STUBS_ACTIVE_TRIGGER_BY_PROJECT:
                stubsActiveTriggerCounter
                        .tag(MetricTag.PROJECT.getValue(), projectUuid.toString())
                        .register(meterRegistry)
                        .increment();
                break;
            case ATP_ITF_STUBS_ERROR_TRIGGER_BY_PROJECT:
                stubsErrorTriggerCounter
                        .tag(MetricTag.PROJECT.getValue(), projectUuid.toString())
                        .register(meterRegistry)
                        .increment();
                break;
            default:
                break;
        }
    }
}
