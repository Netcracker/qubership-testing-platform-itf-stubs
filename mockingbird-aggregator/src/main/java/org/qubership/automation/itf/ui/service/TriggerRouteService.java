package org.qubership.automation.itf.ui.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.camel.Route;
import org.apache.camel.impl.EventDrivenConsumerRoute;
import org.qubership.automation.itf.communication.RoutesInformation;
import org.qubership.automation.itf.communication.StubsIntegrationMessageSender;
import org.qubership.automation.itf.core.model.communication.TransportType;
import org.qubership.automation.itf.core.util.config.Config;
import org.qubership.automation.itf.trigger.camel.CamelContextProvider;
import org.qubership.automation.itf.ui.model.RouteInfo;
import org.qubership.automation.itf.ui.model.RouteInfoDto;
import org.qubership.automation.itf.ui.model.RouteInfoRequest;
import org.qubership.automation.itf.ui.model.RouteInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TriggerRouteService {

    private static final Cache<UUID, List<RouteInfoDto>> CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(3, TimeUnit.MINUTES).build();

    private final StubsIntegrationMessageSender sender;

    @Value("${collect.routes.info.timeout}")
    private int collectRoutesMaxTime;

    public TriggerRouteService(StubsIntegrationMessageSender sender) {
        this.sender = sender;
    }

    public void putRouteInfo(RouteInfoDto routeInfoDto) {
        List<RouteInfoDto> routeInfosDto = CACHE.getIfPresent(routeInfoDto.getRequestId());
        routeInfosDto.add(routeInfoDto);
    }

    public RouteInfoResponse collectRoutes(UUID requestId, UUID projectUuid, TransportType transportType,
                                           int podCount) {
        RouteInfoRequest request = new RouteInfoRequest();
        request.setRequestId(requestId);
        request.setProjectUuid(projectUuid);
        request.setTransportType(transportType);
        request.setPodName(Config.getConfig().getRunningHostname());
        request.setPodCount(podCount);
        CACHE.put(requestId, new ArrayList<>());

        sender.sendToRouteInfoRequestTopic(request, projectUuid);
        waitForCompletion(requestId, podCount, CACHE.getIfPresent(requestId));
        return getRouteEventInfoResponse(requestId, projectUuid, transportType);
    }

    public void collectRouteInfo(RouteInfoRequest request) {
        List<Route> routes = CamelContextProvider.CAMEL_CONTEXT.getRoutes();

        List<RoutesInformation> routesInformationResponses = routes.stream()
                .map(route -> new RoutesInformation((EventDrivenConsumerRoute)route))
                .filter(route -> Objects.nonNull(route.getProjectUuid())
                        && route.getProjectUuid().equals(request.getProjectUuid().toString()))
                .filter(route -> Objects.nonNull(route.getTransportType())
                        && route.getTransportType().equals(request.getTransportType().toString()))
                .collect(Collectors.toList());

        RouteInfoDto response = new RouteInfoDto();
        response.setRequestId(request.getRequestId());
        response.setProjectUuid(request.getProjectUuid());
        response.setTransportType(request.getTransportType());
        response.setRequestPodName(request.getPodName());
        response.setResponsePodName(Config.getConfig().getRunningHostname());
        response.setRoutesInformationResponses(routesInformationResponses);

        sender.sendToRouteInfoResponseTopic(response, request.getProjectUuid());
    }

    private RouteInfoResponse getRouteEventInfoResponse(UUID requestId, UUID projectUuid,
                                                        TransportType transportType) {
        RouteInfoResponse routeEventInfoResponse = new RouteInfoResponse();
        routeEventInfoResponse.setRequestId(requestId);
        routeEventInfoResponse.setProjectUuid(projectUuid);
        routeEventInfoResponse.setTransportType(transportType);

        List<RouteInfoDto> routeInfoDtoList = CACHE.getIfPresent(requestId);
        List<RouteInfo> routesInformationResponse = routeInfoDtoList.stream().map(dto -> {
            RouteInfo routeInfo = new RouteInfo();
            routeInfo.setPodName(dto.getResponsePodName());
            routeInfo.setRoutes(dto.getRoutesInformationResponses());
            return routeInfo;
        }).collect(Collectors.toList());
        routeEventInfoResponse.setRoutesInformation(routesInformationResponse);
        return routeEventInfoResponse;
    }

    private void waitForCompletion(UUID requestId, int totalCount, List<RouteInfoDto> list) {
        long startingTimestamp = System.currentTimeMillis();

        while (list.size() < totalCount) {
            try {
                if (System.currentTimeMillis() - startingTimestamp > collectRoutesMaxTime) {
                    log.warn("RequestId {}: processing is interrupted due to max time limit {} ms.", requestId,
                            collectRoutesMaxTime);
                    break;
                }
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                log.warn("RequestId {}: processing is interrupted after {} ms.", requestId,
                        System.currentTimeMillis() - startingTimestamp);
                break;
            }
        }
        log.info("RequestId {}: All ({}) pods with route are processed, elapsed {} ms {}",
                requestId, totalCount, System.currentTimeMillis() - startingTimestamp,
                (totalCount - list.size() > 0 ? ", pending count: " + (totalCount - list.size()) : ""));
    }
}
