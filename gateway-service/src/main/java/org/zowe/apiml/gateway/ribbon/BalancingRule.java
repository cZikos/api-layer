/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.ribbon;

import com.netflix.loadbalancer.*;
import com.netflix.zuul.context.RequestContext;
import org.thymeleaf.util.StringUtils;

public class BalancingRule extends PredicateBasedRule {

    private AbstractServerPredicate predicate;
    private final AvailabilityPredicate availabilityPredicate;

    @Override
    public AbstractServerPredicate getPredicate() {
        return predicate;
    }

    public BalancingRule() {
        super();
        availabilityPredicate = new AvailabilityPredicate(this, null);
        predicate = CompositePredicate.withPredicates(availabilityPredicate).build();

    }

    @Override
    public Server choose(Object key) {

        if (StringUtils.equals(RequestContext.getCurrentContext().getRequest().getHeader("X-Route"), "SERVICEID")) {
            ServerFilterPredicate serverFilterPredicate = new ServerFilterPredicate();
            predicate = CompositePredicate.withPredicates(availabilityPredicate, serverFilterPredicate)
                .build();
        } else {
            predicate = CompositePredicate.withPredicates(availabilityPredicate).build();
        }

        return super.choose(key);

    }
}
