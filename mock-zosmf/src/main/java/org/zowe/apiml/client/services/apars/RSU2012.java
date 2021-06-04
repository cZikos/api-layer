/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.client.services.apars;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.zowe.apiml.client.api.ZosmfAuthentication;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

@SuppressWarnings("squid:S1452")
public class RSU2012 extends FunctionalApar {
    private final String keystorePath;

    public RSU2012(List<String> usernames, List<String> passwords, String keystorePath) {
        super(usernames, passwords);
        this.keystorePath = keystorePath;
    }

    protected ResponseEntity<?> handleAuthenticationUpdate(Object body, HttpServletResponse response) {
        ZosmfAuthentication zosmfAuthentication = (ZosmfAuthentication) body;
        if (StringUtils.isEmpty(zosmfAuthentication.getNewPwd()) || zosmfAuthentication.getNewPwd().equalsIgnoreCase(zosmfAuthentication.getOldPwd())) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        return validJwtResponse(response, zosmfAuthentication.getUserID(), keystorePath);
    }

    @Override
    protected ResponseEntity<?> handleAuthenticationCreate(Map<String, String> headers, HttpServletResponse response) {
        // JWT token not accepted for create method
        if (containsInvalidUser(headers) && noLtpaCookie(headers)) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String[] credentials = getPiecesOfCredentials(headers);
        return validJwtResponse(response, credentials[0], keystorePath);
    }

    @Override
    protected ResponseEntity<?> handleAuthenticationVerify(Map<String, String> headers, HttpServletResponse response) {
        if (containsInvalidUser(headers) && noLtpaCookie(headers) && noJwtCookie(headers)) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        String[] credentials = getPiecesOfCredentials(headers);
        return validJwtResponse(response, credentials[0], keystorePath);
    }

    @Override
    protected ResponseEntity<?> handleAuthenticationDelete(Map<String, String> headers) {
        if (containsInvalidUser(headers) && noLtpaCookie(headers) && noJwtCookie(headers)) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
