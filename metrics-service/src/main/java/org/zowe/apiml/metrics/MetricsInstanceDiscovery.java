package org.zowe.apiml.metrics;

import com.netflix.discovery.EurekaClient;
import org.springframework.cloud.netflix.turbine.EurekaInstanceDiscovery;
import org.springframework.cloud.netflix.turbine.TurbineProperties;

import java.util.Arrays;
import java.util.List;

public class MetricsInstanceDiscovery extends EurekaInstanceDiscovery {

    public MetricsInstanceDiscovery(TurbineProperties turbineProperties, EurekaClient eurekaClient) {
        super(turbineProperties, eurekaClient);
    }

    @Override
    protected List<String> getApplications() {
        return Arrays.asList("discoverableclient", "apicatalog");
    }
}
