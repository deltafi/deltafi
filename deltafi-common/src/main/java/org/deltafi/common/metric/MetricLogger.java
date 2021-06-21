package org.deltafi.common.metric;

import io.quarkiverse.loggingjson.providers.KeyValueStructuredArgument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MetricLogger {

    private static final Logger logger = LoggerFactory.getLogger("METRIC_LOGGER");
    private static final String METRIC_KEY = "metric";

    public void logMetric(String source, MetricType metricType, String name, long value, Tag... tags) {
        Metric metric = new MetricBuilder()
                .setType(metricType)
                .setName(name)
                .setValue(value)
                .setSource(source)
                .addTags(tags)
                .createMetric();
        logger.info("{}", KeyValueStructuredArgument.kv(METRIC_KEY, metric));
    }

}
