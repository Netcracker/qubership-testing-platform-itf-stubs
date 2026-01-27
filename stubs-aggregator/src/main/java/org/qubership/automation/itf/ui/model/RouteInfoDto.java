package org.qubership.automation.itf.ui.model;

import java.util.List;
import java.util.UUID;

import org.qubership.automation.itf.communication.RoutesInformation;
import org.qubership.automation.itf.core.model.communication.TransportType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RouteInfoDto {

    private UUID requestId;
    private RouteEventType eventType;
    private UUID projectUuid;
    private TransportType transportType;
    private String requestPodName;
    private String responsePodName;
    private String message;
    private List<RoutesInformation> routesInformation;
}
