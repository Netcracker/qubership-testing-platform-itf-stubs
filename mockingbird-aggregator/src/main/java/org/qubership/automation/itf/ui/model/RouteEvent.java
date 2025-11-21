package org.qubership.automation.itf.ui.model;

import java.util.UUID;

import org.qubership.automation.itf.core.model.communication.TransportType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RouteEvent {

    private UUID requestId;
    private RouteEventType eventType;
    private UUID projectUuid;
    private TransportType transportType;
    private String podName;
    private String podNameRouteToStop;
    private int podCount;
    private String routeId;
}
