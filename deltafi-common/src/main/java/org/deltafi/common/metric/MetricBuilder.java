package org.deltafi.common.metric;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MetricBuilder {
    private String source;
    private String name;
    private long value;
    private MetricType type;
    private Date timestamp;
    private Map<String, String> tags;

    public MetricBuilder() {
        this.timestamp = new Date();
    }

    public MetricBuilder setSource(String source) {
        this.source = source;
        return this;
    }

    public MetricBuilder setName(String name) {
        this.name = name;
        return this;
    }

    public MetricBuilder setValue(long value) {
        this.value = value;
        return this;
    }

    public MetricBuilder setType(MetricType type) {
        this.type = type;
        return this;
    }

    public MetricBuilder setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public MetricBuilder addTags(Tag ... tags) {
        if (Objects.isNull(this.tags)) {
            this.tags = new HashMap<>();
        }
        for (Tag tag : tags) {
            this.tags.put(tag.getName(), tag.getValue());
        }
        return this;
    }

    public MetricBuilder setTags(Map<String, String> tags) {
        this.tags = tags;
        return this;
    }

    public Metric createMetric() {
        return new Metric(source, name, value, type, timestamp, tags);
    }
}