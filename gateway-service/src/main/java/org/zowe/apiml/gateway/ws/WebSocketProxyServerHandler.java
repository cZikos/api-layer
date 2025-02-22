/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.gateway.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.zowe.apiml.product.routing.RoutedService;
import org.zowe.apiml.product.routing.RoutedServices;
import org.zowe.apiml.product.routing.RoutedServicesUser;

import javax.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handle initialization and management of routed WebSocket sessions. Copies
 * data from the current session (from client to the gateway) to the server that
 * provides the real WebSocket service.
 */
@Component
@Singleton
@Slf4j
public class WebSocketProxyServerHandler extends AbstractWebSocketHandler implements RoutedServicesUser, SubProtocolCapable {

    @Value("${server.webSocket.supportedProtocols:-}")
    private List<String> subProtocols;

    @Override
    public List<String> getSubProtocols() {
        return subProtocols;
    }

    private final Map<String, WebSocketRoutedSession> routedSessions;
    private final Map<String, RoutedServices> routedServicesMap = new ConcurrentHashMap<>();
    private final DiscoveryClient discovery;
    private final WebSocketRoutedSessionFactory webSocketRoutedSessionFactory;
    private final WebSocketClientFactory webSocketClientFactory;
    private static final String SEPARATOR = "/";

    @Autowired
    public WebSocketProxyServerHandler(DiscoveryClient discovery, WebSocketClientFactory webSocketClientFactory) {
        this.discovery = discovery;
        this.webSocketClientFactory = webSocketClientFactory;
        this.routedSessions = new ConcurrentHashMap<>();  // Default
        this.webSocketRoutedSessionFactory = new WebSocketRoutedSessionFactoryImpl();
        log.debug("Creating WebSocketProxyServerHandler {} ", this);
    }

    public WebSocketProxyServerHandler(DiscoveryClient discovery, WebSocketClientFactory webSocketClientFactory,
                                       Map<String, WebSocketRoutedSession> routedSessions, WebSocketRoutedSessionFactory webSocketRoutedSessionFactory) {
        this.discovery = discovery;
        this.webSocketClientFactory = webSocketClientFactory;
        this.routedSessions = routedSessions;
        this.webSocketRoutedSessionFactory = webSocketRoutedSessionFactory;
        log.debug("Creating WebSocketProxyServerHandler {}", this);
    }

    public void addRoutedServices(String serviceId, RoutedServices routedServices) {
        routedServicesMap.put(serviceId, routedServices);
    }

    private String getTargetUrl(String serviceUrl, ServiceInstance serviceInstance, String path) {
        String servicePath = serviceUrl.charAt(serviceUrl.length() - 1) == '/' ? serviceUrl : serviceUrl + SEPARATOR;
        return (serviceInstance.isSecure() ? "wss" : "ws") + "://" + serviceInstance.getHost() + ":"
            + serviceInstance.getPort() + servicePath + path;
    }

    public Map<String, WebSocketRoutedSession> getRoutedSessions() {
        return routedSessions;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) throws IOException {
        String[] uriParts = getUriParts(webSocketSession);
        if (uriParts == null || uriParts.length != 5) {
            closeWebSocket(webSocketSession, CloseStatus.NOT_ACCEPTABLE, "Invalid URL format");
            return;
        }

        String majorVersion;
        String serviceId;
        String path = uriParts[4];

        if (uriParts[1].equals("ws")) {
            majorVersion = uriParts[2];
            serviceId = uriParts[3];
        } else {
            majorVersion = uriParts[3];
            serviceId = uriParts[1];
        }

        routeToService(webSocketSession, serviceId, majorVersion, path);
    }

    private void routeToService(WebSocketSession webSocketSession, String serviceId, String majorVersion, String path) throws IOException {
        RoutedServices routedServices = routedServicesMap.get(serviceId);

        if (routedServices == null) {
            closeWebSocket(webSocketSession, CloseStatus.NOT_ACCEPTABLE,
                String.format("Requested service %s is not known by the gateway", serviceId));
            return;
        }

        RoutedService service = routedServices.findServiceByGatewayUrl("ws/" + majorVersion);
        if (service == null) {
            closeWebSocket(webSocketSession, CloseStatus.NOT_ACCEPTABLE,
                String.format("Requested ws/%s url is not known by the gateway", majorVersion));
            return;
        }

        ServiceInstance serviceInstance = findServiceInstance(serviceId);
        if (serviceInstance != null) {
            openWebSocketConnection(service, serviceInstance, serviceInstance, path, webSocketSession);
        } else {
            closeWebSocket(webSocketSession, CloseStatus.SERVICE_RESTARTED,
                String.format("Requested service %s does not have available instance", serviceId));
        }
    }

    private void closeWebSocket(WebSocketSession webSocketSession, CloseStatus closeStatus, String reason) throws IOException {
        if (webSocketSession.isOpen()) {
            webSocketSession.close(closeStatus.withReason(reason));
        }
    }

    private String[] getUriParts(WebSocketSession webSocketSession) {
        URI uri = webSocketSession.getUri();
        String[] uriParts = null;
        if (uri != null && uri.getPath() != null) {
            uriParts = uri.getPath().split(SEPARATOR, 5);
        }
        return uriParts;
    }

    private void openWebSocketConnection(RoutedService service, ServiceInstance serviceInstance, Object uri,
                                         String path, WebSocketSession webSocketSession) throws IOException {
        String serviceUrl = service.getServiceUrl();
        String targetUrl = getTargetUrl(serviceUrl, serviceInstance, path);

        log.debug(String.format("Opening routed WebSocket session from %s to %s with %s by %s", uri.toString(), targetUrl, webSocketClientFactory, this));
        try {
            WebSocketRoutedSession session = webSocketRoutedSessionFactory.session(webSocketSession, targetUrl, webSocketClientFactory);
            routedSessions.put(webSocketSession.getId(), session);

        } catch (WebSocketProxyError e) {
            log.debug("Error opening WebSocket connection to {}: {}", targetUrl, e.getMessage());
            webSocketSession.close(CloseStatus.NOT_ACCEPTABLE.withReason(e.getMessage()));
        }
    }

    private ServiceInstance findServiceInstance(String serviceId) {
        List<ServiceInstance> serviceInstances = this.discovery.getInstances(serviceId);
        if (!serviceInstances.isEmpty()) {
            // TODO: Is this implementation apropriate?
            return serviceInstances.get(0);
        } else {
            return null;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.debug("afterConnectionClosed(session={},status={})", session, status);
        try {
            session.close(status);

            WebSocketRoutedSession webSocketRoutedSession = getRoutedSession(session);
            if (webSocketRoutedSession != null) {
                webSocketRoutedSession.close(status);
            }

            routedSessions.remove(session.getId());
        } catch (NullPointerException | IOException e) {
            log.debug("Error closing WebSocket connection: {}", e.getMessage(), e);
        }
    }

    @Override
    public void handleMessage(WebSocketSession webSocketSession, WebSocketMessage<?> webSocketMessage)
        throws Exception {
        log.debug("handleMessage(session={},message={})", webSocketSession, webSocketMessage);
        WebSocketRoutedSession session = getRoutedSession(webSocketSession);
        if (session != null) {
            session.sendMessageToServer(webSocketMessage);
        }
    }

    private WebSocketRoutedSession getRoutedSession(WebSocketSession webSocketSession) {
        return routedSessions.get(webSocketSession.getId());
    }
}
