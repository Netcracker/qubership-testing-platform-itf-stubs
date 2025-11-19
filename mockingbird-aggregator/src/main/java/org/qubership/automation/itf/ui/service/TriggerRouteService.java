package org.qubership.automation.itf.ui.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.impl.EventDrivenConsumerRoute;
import org.qubership.automation.itf.activation.impl.OnStartupTriggersActivationService;
import org.qubership.automation.itf.communication.RoutesInformation;
import org.qubership.automation.itf.communication.StubsIntegrationMessageSender;
import org.qubership.automation.itf.core.model.communication.TransportType;
import org.qubership.automation.itf.core.util.config.Config;
import org.qubership.automation.itf.trigger.camel.CamelContextProvider;
import org.qubership.automation.itf.ui.model.RouteEvent;
import org.qubership.automation.itf.ui.model.RouteEventType;
import org.qubership.automation.itf.ui.model.RouteInfo;
import org.qubership.automation.itf.ui.model.RouteInfoDto;
import org.qubership.automation.itf.ui.model.RouteInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TriggerRouteService {

    private static final Cache<UUID, List<RouteInfoDto>> routeInfoCache = CacheBuilder.newBuilder()
            .expireAfterAccess(3, TimeUnit.MINUTES).build();

    private static final Cache<UUID, List<String>> routeStopCache = CacheBuilder.newBuilder()
            .expireAfterAccess(3, TimeUnit.MINUTES).build();

    private final OnStartupTriggersActivationService onStartupTriggersActivationService;
    private final StubsIntegrationMessageSender sender;

    @Value("${collect.routes.info.timeout}")
    private int collectRoutesMaxTime;

    public TriggerRouteService(OnStartupTriggersActivationService onStartupTriggersActivationService,
                               StubsIntegrationMessageSender sender) {
        this.sender = sender;
        this.onStartupTriggersActivationService = onStartupTriggersActivationService;
    }

    public boolean ping() {
        return onStartupTriggersActivationService.isInitialActivationCompleted();
    }

    public RouteInfoResponse collectRoutes(UUID projectUuid, TransportType transportType, int podCount) {
        UUID requestId = UUID.randomUUID();
        RouteEvent event = new RouteEvent();
        event.setRequestId(requestId);
        event.setEventType(RouteEventType.COLLECT);
        event.setProjectUuid(projectUuid);
        event.setTransportType(transportType);
        event.setPodName(Config.getConfig().getRunningHostname());
        event.setPodCount(podCount);
        routeInfoCache.put(requestId, new ArrayList<>());

        sender.sendToRouteInfoRequestTopic(event, projectUuid);
        waitForCompletion(requestId, podCount, routeInfoCache.getIfPresent(requestId), collectRoutesMaxTime);
        return getRouteEventInfoResponse(requestId, projectUuid, transportType);
    }

    public String stopRoute(UUID projectUuid, String routeId, String podName) {
        UUID requestId = UUID.randomUUID();
        RouteEvent event = new RouteEvent();
        event.setRequestId(requestId);
        event.setPodName(Config.getConfig().getRunningHostname());
        event.setEventType(RouteEventType.STOP);
        event.setPodNameRouteToStop(podName);
        event.setRouteId(routeId);
        routeStopCache.put(requestId, new ArrayList<>(1));

        sender.sendToRouteInfoRequestTopic(event, projectUuid);
        waitForCompletion(requestId, 1, routeStopCache.getIfPresent(requestId), 120000);
        List<String> message = routeStopCache.getIfPresent(requestId);
        return message.get(0);
    }

    public void stopRoute(RouteEvent event) {
        String message = "";
        try {
            CamelContext camelContext = CamelContextProvider.CAMEL_CONTEXT;
            camelContext.stopRoute(event.getRouteId());
            camelContext.removeRoute(event.getRouteId());
            camelContext.removeComponent(event.getRouteId());
            message = String.format("Route deactivated id[%s].", event.getRouteId());
        } catch (Exception e) {
            log.error("Error while stopping route by id[{}].", event.getRouteId(), e);
            message = e.getMessage();
        } finally {
            RouteInfoDto response = new RouteInfoDto();
            response.setRequestId(event.getRequestId());
            response.setEventType(RouteEventType.STOP);
            response.setRequestPodName(event.getPodName());
            response.setMessage(message);
            sender.sendToRouteInfoResponseTopic(response, event.getProjectUuid());
        }
    }

    public void collectRouteInfo(RouteEvent event) {
        List<Route> routes = CamelContextProvider.CAMEL_CONTEXT.getRoutes();

        List<RoutesInformation> routesInformation = routes.stream().parallel()
                .map(route -> new RoutesInformation((EventDrivenConsumerRoute)route))
                .filter(route -> Objects.nonNull(route.getProjectUuid())
                        && route.getProjectUuid().equals(event.getProjectUuid().toString()))
                .filter(route -> route.getTransportType().equals(event.getTransportType().toString()))
                .collect(Collectors.toList());

        RouteInfoDto response = new RouteInfoDto();
        response.setRequestId(event.getRequestId());
        response.setEventType(RouteEventType.COLLECT);
        response.setProjectUuid(event.getProjectUuid());
        response.setTransportType(event.getTransportType());
        response.setRequestPodName(event.getPodName());
        response.setResponsePodName(Config.getConfig().getRunningHostname());
        response.setRoutesInformation(routesInformation);

        sender.sendToRouteInfoResponseTopic(response, event.getProjectUuid());
    }

    public void putToCache(RouteInfoDto event) {
        switch (event.getEventType()) {
            case COLLECT: {
                List<RouteInfoDto> routeInfosDto = routeInfoCache.getIfPresent(event.getRequestId());
                routeInfosDto.add(event);
                break;
            }
            case STOP: {
                List<String> routeInfosDto = routeStopCache.getIfPresent(event.getRequestId());
                routeInfosDto.add(event.getMessage());
                break;
            }
            default: {
                throw new RuntimeException("Unknown route event type: " + event.getEventType());
            }
        }
    }

    private RouteInfoResponse getRouteEventInfoResponse(UUID requestId, UUID projectUuid,
                                                        TransportType transportType) {
        RouteInfoResponse response = new RouteInfoResponse();
        response.setRequestId(requestId);
        response.setProjectUuid(projectUuid);
        response.setTransportType(transportType);

        List<RouteInfoDto> routeInfoDtoList = routeInfoCache.getIfPresent(requestId);
        List<RouteInfo> routesInformation = routeInfoDtoList.stream().parallel().map(dto -> {
            RouteInfo routeInfo = new RouteInfo();
            routeInfo.setPodName(dto.getResponsePodName());
            routeInfo.setTotal(dto.getRoutesInformation().size());
            routeInfo.setRoutes(dto.getRoutesInformation());
            return routeInfo;
        }).collect(Collectors.toList());
        response.setRoutesInformation(routesInformation);
        return response;
    }

    private void waitForCompletion(UUID requestId, int totalCount, List list, int timeout) {
        long startingTimestamp = System.currentTimeMillis();
        while (list.size() < totalCount) {
            try {
                if (System.currentTimeMillis() - startingTimestamp > timeout) {
                    log.warn("RequestId {}: processing is interrupted due to max time limit {} ms.",
                            requestId, timeout);
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
