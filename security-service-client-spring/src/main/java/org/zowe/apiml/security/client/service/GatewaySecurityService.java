/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.security.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.zowe.apiml.product.gateway.GatewayClient;
import org.zowe.apiml.product.gateway.GatewayConfigProperties;
import org.zowe.apiml.security.client.handler.RestResponseHandler;
import org.zowe.apiml.security.common.config.AuthConfigurationProperties;
import org.zowe.apiml.security.common.error.ErrorType;
import org.zowe.apiml.security.common.token.QueryResponse;

import java.util.Optional;

/**
 * Core class of security client
 * provides facility for performing login and validating JWT token
 */
@Service
@RequiredArgsConstructor
public class GatewaySecurityService {
    private static final String MESSAGE_KEY_STRING = "messageKey\":\"";

    private final GatewayClient gatewayClient;
    private final AuthConfigurationProperties authConfigurationProperties;
    private final RestTemplate restTemplate;
    private final RestResponseHandler responseHandler;

    /**
     * Logs into the gateway with username and password, and retrieves valid JWT token
     *
     * @param username Username
     * @param password Password
     * @return Valid JWT token for the supplied credentials
     */
    public Optional<String> login(String username, String password) {
        GatewayConfigProperties gatewayConfigProperties = gatewayClient.getGatewayConfigProperties();
        String uri = String.format("%s://%s%s", gatewayConfigProperties.getScheme(),
            gatewayConfigProperties.getHostname(), authConfigurationProperties.getGatewayLoginEndpoint());

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode loginRequest = mapper.createObjectNode();
        loginRequest.put("username", username);
        loginRequest.put("password", password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                uri,
                HttpMethod.POST,
                new HttpEntity<>(loginRequest, headers),
                String.class);

            return extractToken(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        } catch (HttpClientErrorException | ResourceAccessException | HttpServerErrorException e) {
            ErrorType errorType = getErrorType(e);
            responseHandler.handleBadResponse(e, errorType,
                "Cannot access Gateway service. Uri '{}' returned: {}", uri, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Verifies JWT token validity and returns JWT token data
     *
     * @param token JWT token to be validated
     * @return JWT token data as {@link QueryResponse}
     */
    public QueryResponse query(String token) {
        GatewayConfigProperties gatewayConfigProperties = gatewayClient.getGatewayConfigProperties();
        String uri = String.format("%s://%s%s", gatewayConfigProperties.getScheme(),
            gatewayConfigProperties.getHostname(), authConfigurationProperties.getGatewayQueryEndpoint());
        String cookie = String.format("%s=%s", authConfigurationProperties.getCookieProperties().getCookieName(), token);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);

        try {
            ResponseEntity<QueryResponse> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                QueryResponse.class);

            return response.getBody();
        } catch (HttpClientErrorException | ResourceAccessException | HttpServerErrorException e) {
            responseHandler.handleBadResponse(e, ErrorType.TOKEN_NOT_VALID,
                "Can not access Gateway service. Uri '{}' returned: {}", uri, e.getMessage());
        }
        return null;
    }

    private ErrorType getErrorType(RestClientException ex) {
        String detailMessage = ex.getMessage();
        if (detailMessage == null) {
            return ErrorType.AUTH_GENERAL;
        }

        int indexOfMessageKey = detailMessage.indexOf(MESSAGE_KEY_STRING);
        if (indexOfMessageKey < 0) {
            return ErrorType.AUTH_GENERAL;
        }

        // substring from `messageKey":"` to next `"` - this is the messageKey value
        String messageKeyToEndOfExceptionMessage = detailMessage.substring(indexOfMessageKey + MESSAGE_KEY_STRING.length());
        String messageKey = messageKeyToEndOfExceptionMessage.substring(0, messageKeyToEndOfExceptionMessage.indexOf("\""));

        try {
            return ErrorType.fromMessageKey(messageKey);
        } catch (IllegalArgumentException e) {
            return ErrorType.AUTH_GENERAL;
        }
    }

    private Optional<String> extractToken(String cookies) {
        String cookieName = authConfigurationProperties.getCookieProperties().getCookieName();

        if (cookies == null || cookies.isEmpty() || !cookies.contains(cookieName)) {
            return Optional.empty();
        } else {
            int end = cookies.indexOf(';');
            String cookie = (end > 0) ? cookies.substring(0, end) : cookies;
            return Optional.of(cookie.replace(cookieName + "=", ""));
        }
    }
}
