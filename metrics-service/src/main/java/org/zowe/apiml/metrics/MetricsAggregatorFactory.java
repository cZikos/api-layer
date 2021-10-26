package org.zowe.apiml.metrics;

import com.netflix.turbine.data.AggDataFromCluster;
import com.netflix.turbine.discovery.Instance;
import com.netflix.turbine.handler.PerformanceCriteria;
import com.netflix.turbine.handler.TurbineDataHandler;
import com.netflix.turbine.monitor.TurbineDataMonitor;
import com.netflix.turbine.monitor.cluster.ClusterMonitor;
import org.springframework.cloud.netflix.turbine.SpringAggregatorFactory;
import org.springframework.cloud.netflix.turbine.TurbineClustersProvider;

import java.util.Collection;

import static com.netflix.turbine.monitor.cluster.AggregateClusterMonitor.AggregatorClusterMonitorConsole;

public class MetricsAggregatorFactory extends SpringAggregatorFactory {
    private final TurbineClustersProvider clustersProvider;

    public MetricsAggregatorFactory(TurbineClustersProvider clustersProvider) {
        super(clustersProvider);
        this.clustersProvider = clustersProvider;
    }

    @Override
    public void initClusterMonitors() {
        for (String clusterName : clustersProvider.getClusterNames()) {

            ClusterMonitor<AggDataFromCluster> clusterMonitor = (ClusterMonitor<AggDataFromCluster>) MetricsAggregatorFactory.findOrRegisterAggregateMonitor(
                clusterName);
            clusterMonitor.registerListenertoClusterMonitor(this.StaticListener);
            try {
                clusterMonitor.startMonitor();
            }
            catch (Exception ex) {
                clusterMonitor.stopMonitor();
                clusterMonitor.getDispatcher().stopDispatcher();
            }
        }
    }

    public static TurbineDataMonitor<AggDataFromCluster> findOrRegisterAggregateMonitor(
        String clusterName) {
        TurbineDataMonitor<AggDataFromCluster> clusterMonitor = AggregatorClusterMonitorConsole
            .findMonitor(clusterName + "_agg");
        if (clusterMonitor == null) {
            clusterMonitor = new MetricsClusterMonitor(clusterName);
            clusterMonitor = AggregatorClusterMonitorConsole
                .findOrRegisterMonitor(clusterMonitor);
        }
        return clusterMonitor;
    }

    private final TurbineDataHandler<AggDataFromCluster> StaticListener = new TurbineDataHandler<AggDataFromCluster>() {

        @Override
        public String getName() {
            return "StaticListener_For_Aggregator";
        }

        @Override
        public void handleData(Collection<AggDataFromCluster> stats) {
        }

        @Override
        public void handleHostLost(Instance host) {
        }

        @Override
        public PerformanceCriteria getCriteria() {
            return NonCriticalCriteria;
        }

    };

    private final PerformanceCriteria NonCriticalCriteria = new PerformanceCriteria() {

        @Override
        public boolean isCritical() {
            return false;
        }

        @Override
        public int getMaxQueueSize() {
            return 0;
        }

        @Override
        public int numThreads() {
            return 0;
        }

    };
}
