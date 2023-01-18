/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.core.metrics;

import com.codahale.metrics.MetricRegistry;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.types.Metric;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.metrics.statsd.StatsdDeltaReporter;
import org.deltafi.core.services.DeltaFiPropertiesService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MetricRepository {

    private final MetricRegistry metrics;
    @SuppressWarnings("FieldCanBeLocal")
    private final StatsdDeltaReporter reporter;

    public MetricRepository(@Value("${STATSD_HOSTNAME:deltafi-graphite}") String statsdHostname,
                            @Value("${STATSD_PORT:8125}") int statsdPort,
                            @Value("${METRICS_PERIOD_SECONDS:10}") int periodSeconds,
                            DeltaFiPropertiesService deltaFiPropertiesService) {
        DeltaFiProperties deltaFiProperties = deltaFiPropertiesService.getDeltaFiProperties();
        if (deltaFiProperties.getMetrics().isEnabled()) {
            log.info("Creating metric service");
            metrics = new MetricRegistry();

            log.info("Starting statsd reporter connecting to {}:{}", statsdHostname, statsdPort);
            reporter = StatsdDeltaReporter
                    .builder(statsdHostname, statsdPort, metrics)
                    .build();
            reporter.start(periodSeconds, TimeUnit.SECONDS);
            log.info("MetricService initialized.");
        } else {
            log.warn("Metrics are disabled");
            metrics = null;
            reporter = null;
        }
    }

    public void increment(@NotNull Metric metric) {
        if (metrics != null) { metrics.counter(metric.metricName()).inc(metric.getValue()); }
        log.debug("{}", metric);
    }

    public void increment(@NotNull String name, @NotNull Map<String, String> tags, long value) {
        org.deltafi.common.types.Metric metric = new org.deltafi.common.types.Metric(name, value, tags);
        if (metrics != null) { metrics.counter(metric.metricName()).inc(value); }
        log.debug("{}", metric);
    }
}
