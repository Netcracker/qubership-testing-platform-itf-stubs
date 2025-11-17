package org.qubership.automation.itf.ui.model;

import java.util.List;
import java.util.UUID;

import org.qubership.automation.itf.core.model.communication.TransportType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RouteInfoResponse {

    private UUID requestId;
    private UUID projectUuid;
    private TransportType transportType;
    private List<RouteInfo> routesInformation;
}
