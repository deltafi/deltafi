package org.deltafi.common.metric;

import java.util.Date;
import java.util.Map;
import java.util.Objects;

public class Metric {

    private String source;
    private String name;
    private long value;
    private MetricType type;
    private Date timestamp;
    private Map<String, String> tags;


    public Metric(String source, String name, long value, MetricType type, Date timestamp, Map<String, String> tags) {
        this.source = source;
        this.name = name;
        this.value = value;
        this.type = type;
        this.timestamp = timestamp;
        this.tags = tags;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }

    public MetricType getType() {
        return type;
    }

    public void setType(MetricType type) {
        this.type = type;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    @Override
    public String toString() {
        return "Metric{" +
                "source='" + source + '\'' +
                ", name='" + name + '\'' +
                ", value=" + value +
                ", type=" + type +
                ", timestamp=" + timestamp +
                ", tags=" + tags +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Metric metric = (Metric) o;
        return value == metric.value && Objects.equals(source, metric.source) && Objects.equals(name, metric.name) && type == metric.type && Objects.equals(timestamp, metric.timestamp) && Objects.equals(tags, metric.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, name, value, type, timestamp, tags);
    }
}
