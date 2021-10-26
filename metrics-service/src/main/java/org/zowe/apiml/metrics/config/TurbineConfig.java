package org.zowe.apiml.metrics.config;

import com.netflix.discovery.EurekaClient;
import com.netflix.turbine.discovery.InstanceDiscovery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.netflix.turbine.SpringAggregatorFactory;
import org.springframework.cloud.netflix.turbine.TurbineClustersProvider;
import org.springframework.cloud.netflix.turbine.TurbineProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zowe.apiml.metrics.ApimlTurbineClustersProvider;
import org.zowe.apiml.metrics.MetricsAggregatorFactory;
import org.zowe.apiml.metrics.MetricsInstanceDiscovery;

@Configuration
public class TurbineConfig {

    @Autowired
    private TurbineProperties turbineProperties;
    @Autowired
    private TurbineClustersProvider turbineClustersProvider;
    @Autowired
    private EurekaClient eurekaClient;

//    @Bean
//    public InstanceDiscovery instanceDiscovery() {
//        return new MetricsInstanceDiscovery(turbineProperties, eurekaClient);
//    }
//
//    @Bean
//    public TurbineClustersProvider turbineClustersProvider() {
//        return new ApimlTurbineClustersProvider();
//    }
//
//    @Bean
//    public SpringAggregatorFactory springAggregatorFactory(){
//        return new MetricsAggregatorFactory(turbineClustersProvider);
//    }
}
