/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.gateway.filters.post;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.Debug;
import com.netflix.zuul.context.RequestContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.zowe.apiml.gateway.ribbon.RequestContextUtils;

import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.POST_TYPE;
import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.SEND_RESPONSE_FILTER_ORDER;

/**
 * Log Zuul header debug information
 *
 * @author Dave King
 */
@Component
@Slf4j
public class DebugHeaderFilter extends ZuulFilter {

    @Value("${zuul.debug.request.debugHeaderLimit:4096}")
    private int debugHeaderLimit;

    @Override
    public String filterType() {
        return POST_TYPE;
    }

    @Override
    public int filterOrder() {
        return SEND_RESPONSE_FILTER_ORDER - 1;
    }

    @Override
    public boolean shouldFilter() {
        return Debug.debugRouting();
    }

    @Override
    public Object run() {

        String debug = convertToPrettyPrintString(Debug.getRoutingDebug());
        String reqInfo = RequestContext.getCurrentContext().getFilterExecutionSummary().toString();
        log.debug("Filter Execution Summary: " + reqInfo);
        log.debug("RoutingDebug: " + debug);
        log.debug("RibbonRetryDebug: " + RequestContextUtils.getDebugInfo());

        String debugInfo = Debug.getRoutingDebug().stream().collect(Collectors.joining("|"));
        if (debugInfo.length() > debugHeaderLimit) {
            debugInfo = debugInfo.substring(0, debugHeaderLimit);
        }

        RequestContext.getCurrentContext().addZuulResponseHeader(
            "ZuulFilterDebug", debugInfo);
        RequestContext.getCurrentContext().addZuulResponseHeader(
            "RibbonRetryDebug", RequestContextUtils.getDebugInfo());
        return null;
    }

    private String convertToPrettyPrintString(List<String> filterDebugList) {
        return filterDebugList.stream()
            .map(s -> s.startsWith("{") ? "\t" + s : s)
            .collect(Collectors.joining("\n"));
    }

}
