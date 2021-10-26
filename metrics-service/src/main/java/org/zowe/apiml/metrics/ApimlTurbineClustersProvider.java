package org.zowe.apiml.metrics;

import org.springframework.cloud.netflix.turbine.TurbineClustersProvider;

import java.util.Arrays;
import java.util.List;

public class ApimlTurbineClustersProvider implements TurbineClustersProvider {
    @Override
    public List<String> getClusterNames() {
        return Arrays.asList("DISCOVERABLECLIENT","APICATALOG");
    }
}
