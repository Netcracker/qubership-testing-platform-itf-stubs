package org.qubership.automation.itf.communication;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RouteMetaInfo {

    PROJECT_UUID("projectUuid"),
    TRANSPORT_TYPE("transportType"),
    ENV_NAME("envName"),
    ENV_ID("envId"),
    TRIGGER_NAME("triggerName"),
    TRIGGER_ID("triggerId"),
    START_TIME("startTime"),
    UP_TIME("upTime");
    private final String value;

}
