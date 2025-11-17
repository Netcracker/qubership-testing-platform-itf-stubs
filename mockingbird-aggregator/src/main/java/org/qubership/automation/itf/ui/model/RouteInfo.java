package org.qubership.automation.itf.ui.model;

import java.util.List;

import org.qubership.automation.itf.communication.RoutesInformation;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RouteInfo {

    private String podName;
    private List<RoutesInformation> routes;
}
