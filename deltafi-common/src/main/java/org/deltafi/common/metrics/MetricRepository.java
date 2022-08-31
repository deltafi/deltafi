/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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

package org.deltafi.common.metrics;

import com.codahale.metrics.MetricRegistry;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.common.metrics.statsd.StatsdDeltaReporter;
import org.deltafi.common.properties.MetricsProperties;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MetricRepository {

    final private MetricRegistry metrics;

    public MetricRepository(MetricsProperties metricsProperties) {
        if (metricsProperties.isEnabled()) {
            log.info("Creating metric service");
            metrics = new MetricRegistry();

            log.info("Starting statsd reporter");
            StatsdDeltaReporter
                    .builder(metricsProperties.getStatsd().getHostname(),
                            metricsProperties.getStatsd().getPort(),
                            metrics)
                    .build()
                    .start(10, TimeUnit.SECONDS);
            log.info("MetricService initialized.");
        } else {
            log.warn("Metrics are disabled");
            metrics = null;
        }
    }

    public void increment(@NotNull Metric metric) {
        if (metrics != null) { metrics.counter(metric.metricName()).inc(metric.getValue()); }
        log.debug("{}", metric);
    }

    public void increment(@NotNull String name, @NotNull Map<String, String> tags, long value) {
        Metric metric = new Metric(name, value).addTags(tags);
        if (metrics != null) { metrics.counter(metric.metricName()).inc(value); }
        log.debug("{}", metric);
    }
}
