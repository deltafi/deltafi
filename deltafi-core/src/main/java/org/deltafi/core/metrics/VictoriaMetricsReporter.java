/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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

import com.codahale.metrics.*;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class VictoriaMetricsReporter extends ScheduledReporter {

    private final String victoriametricsHost;
    private final int victoriametricsPort;

    protected VictoriaMetricsReporter(MetricRegistry registry, String victoriametricsHost, int victoriametricsPort, TimeUnit rateUnit, TimeUnit durationUnit) {
        super(registry, "victoriametrics-reporter", MetricFilter.ALL, rateUnit, durationUnit);
        this.victoriametricsHost = victoriametricsHost;
        this.victoriametricsPort = victoriametricsPort;
    }

    public static Builder builder(String victoriametricsHost, int victoriametricsPort, MetricRegistry registry) {
        return new Builder(victoriametricsHost, victoriametricsPort, registry);
    }

    @Override
    public void report(SortedMap<String, Gauge> gauges,
                       SortedMap<String, Counter> counters,
                       SortedMap<String, Histogram> histograms,
                       SortedMap<String, Meter> meters,
                       SortedMap<String, Timer> timers) {

        final long timestamp = System.currentTimeMillis() / 1000; // Unix epoch in seconds
        StringBuilder metrics = new StringBuilder();

        for (Map.Entry<String, Counter> entry : counters.entrySet()) {
            long value = entry.getValue().getCount();
            String metricName = "stats_counts." + entry.getKey();
            metrics.append(formatMetric(metricName, value, timestamp));

            // Reset counter after reporting (delta counter behavior)
            entry.getValue().dec(value);
        }

        if (!metrics.isEmpty()) {
            sendMetrics(metrics.toString());
        }
    }

    public void sendGauges(Map<String, Long> gauges) {
        if (gauges.isEmpty()) {
            return;
        }

        final long timestamp = System.currentTimeMillis() / 1000;
        StringBuilder metrics = new StringBuilder();

        for (Map.Entry<String, Long> entry : gauges.entrySet()) {
            metrics.append(formatMetric(entry.getKey(), entry.getValue(), timestamp));
        }

        sendMetrics(metrics.toString());
    }

    private String formatMetric(String metricName, long value, long timestamp) {
        return String.format("%s %d %d\n", metricName, value, timestamp);
    }

    private void sendMetrics(String metrics) {
        try (Socket socket = new Socket(victoriametricsHost, victoriametricsPort);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            writer.write(metrics);
            writer.flush();
            socket.shutdownOutput();

            log.debug("Successfully sent {} bytes of metrics to VictoriaMetrics at {}:{}",
                    metrics.length(), victoriametricsHost, victoriametricsPort);
        } catch (IOException e) {
            log.error("Could not send metrics to VictoriaMetrics at {}:{}", victoriametricsHost, victoriametricsPort, e);
        }
    }

    public static class Builder {
        private final String victoriametricsHost;
        private final int victoriametricsPort;
        private final MetricRegistry registry;
        private final TimeUnit rateUnit = TimeUnit.SECONDS;
        private final TimeUnit durationUnit = TimeUnit.MILLISECONDS;

        public Builder(String victoriametricsHost, int victoriametricsPort, MetricRegistry registry) {
            this.victoriametricsHost = victoriametricsHost;
            this.victoriametricsPort = victoriametricsPort;
            this.registry = registry;
        }

        public VictoriaMetricsReporter build() {
            return new VictoriaMetricsReporter(registry, victoriametricsHost, victoriametricsPort, rateUnit, durationUnit);
        }
    }
}
