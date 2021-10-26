package org.zowe.apiml.metrics;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.turbine.discovery.Instance;
import com.netflix.turbine.handler.PerformanceCriteria;
import com.netflix.turbine.monitor.MonitorConsole;
import com.netflix.turbine.monitor.cluster.AggregateClusterMonitor;
import com.netflix.turbine.monitor.cluster.ObservationCriteria;
import com.netflix.turbine.monitor.instance.InstanceUrlClosure;

public class MetricsClusterMonitor extends AggregateClusterMonitor {
    public MetricsClusterMonitor(String clusterName) {
        super(clusterName + "_agg",
            new ObservationCriteria.ClusterBasedObservationCriteria(clusterName),
            new PerformanceCriteria.AggClusterPerformanceCriteria(clusterName),
            new MonitorConsole<>(),
            InstanceMonitorDispatcher,
            ApimlBasedUrlClosure);
    }

    public static InstanceUrlClosure ApimlBasedUrlClosure = new InstanceUrlClosure() {

        private final DynamicBooleanProperty instanceInsertPort = DynamicPropertyFactory
            .getInstance().getBooleanProperty("turbine.instanceInsertPort", true);

        @Override
        public String getUrlPath(Instance host) {
            if (host.getCluster() == null) {
                throw new RuntimeException(
                    "Host must have cluster name in order to use ClusterConfigBasedUrlClosure");
            }

            String url = host.getCluster().toLowerCase() + "/application/hystrix.stream";
            //String url = "application/hystrix.stream";
            // find port and scheme
            String port;
            String scheme;
            if (host.getAttributes().containsKey("securePort")) {
                port = host.getAttributes().get("securePort");
                scheme = "https";
            } else {
                port = host.getAttributes().get("port");
                scheme = "http";
            }

            if (host.getAttributes().containsKey("fusedHostPort")) {
                return String.format("%s://%s/%s", scheme,
                    host.getAttributes().get("fusedHostPort"), url);
            }

            // determine if to insert port
            String insertPortKey = "turbine.instanceInsertPort." + host.getCluster();
            DynamicStringProperty insertPortProp = DynamicPropertyFactory.getInstance()
                .getStringProperty(insertPortKey, null);
            boolean insertPort;
            if (insertPortProp.get() == null) {
                insertPort = this.instanceInsertPort.get();
            } else {
                insertPort = Boolean.parseBoolean(insertPortProp.get());
            }

            // format url with port
            if (insertPort) {
                if (port == null) {
                    throw new RuntimeException(
                        "Configured to use port, but port or securePort is not in host attributes");
                }

                return String.format("%s://%s:%s/%s", scheme, host.getHostname(), port, url);
            }

            // format url without port
            return scheme + "://" + host.getHostname() + url;
        }
    };
}
