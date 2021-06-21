package org.deltafi.common.trace;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;

public class DeltafiSpan implements Serializable {

    String id;
    String traceId;
    String parentId;
    String name;
    long timestamp;
    long duration;
    String kind;
    Map<String, String> tags;
    Endpoint localEndpoint;
    Endpoint remoteEndpoint;

    public DeltafiSpan(String id, String traceId, String parentId, String name, long timestamp, long duration, String kind, Map<String, String> tags, Endpoint localEndpoint, Endpoint remoteEndpoint) {
        this.id = id;
        this.traceId = traceId;
        this.parentId = parentId;
        this.name = name;
        this.timestamp = timestamp;
        this.duration = duration;
        this.kind = kind;
        this.tags = tags;
        this.localEndpoint = localEndpoint;
        this.remoteEndpoint = remoteEndpoint;
    }

    public static Builder newSpanBuilder() {
        Builder builder = new Builder();
        builder.timestamp = micros();
        return builder;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = micros(timestamp);
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public Endpoint getLocalEndpoint() {
        return localEndpoint;
    }

    public void setLocalEndpoint(Endpoint localEndpoint) {
        this.localEndpoint = localEndpoint;
    }

    public Endpoint getRemoteEndpoint() {
        return remoteEndpoint;
    }

    public void setRemoteEndpoint(Endpoint remoteEndpoint) {
        this.remoteEndpoint = remoteEndpoint;
    }

    public void endSpan() {
        this.duration = micros() - timestamp;
    }

    public static long micros() {
        return micros(Instant.now());
    }

    public static long micros(Instant time) {
        return ChronoUnit.MICROS.between(Instant.EPOCH, time);
    }

    @Override
    public String toString() {
        return "DeltafiSpan{" +
                "id='" + id + '\'' +
                ", traceId='" + traceId + '\'' +
                ", parentId='" + parentId + '\'' +
                ", name='" + name + '\'' +
                ", timestamp=" + timestamp +
                ", duration=" + duration +
                ", kind='" + kind + '\'' +
                ", tags=" + tags +
                ", localEndpoint=" + localEndpoint +
                ", remoteEndpoint=" + remoteEndpoint +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeltafiSpan that = (DeltafiSpan) o;
        return timestamp == that.timestamp && duration == that.duration && Objects.equals(id, that.id) && Objects.equals(traceId, that.traceId) && Objects.equals(parentId, that.parentId) && Objects.equals(name, that.name) && Objects.equals(kind, that.kind) && Objects.equals(tags, that.tags) && Objects.equals(localEndpoint, that.localEndpoint) && Objects.equals(remoteEndpoint, that.remoteEndpoint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, traceId, parentId, name, timestamp, duration, kind, tags, localEndpoint, remoteEndpoint);
    }

    public static class Endpoint implements Serializable {
        String serviceName;

        public Endpoint(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }
    }

    public static class Builder {
        String id;
        String traceId;
        String parentId;
        String name;
        long timestamp;
        long duration;
        String kind;
        Map<String, String> tags;
        Endpoint localEndpoint;
        Endpoint remoteEndpoint;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder parentId(String parentId) {
            this.parentId = parentId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = micros(timestamp);
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder duration(long duration) {
            this.duration = duration;
            return this;
        }

        public Builder kind(String kind) {
            this.kind = kind;
            return this;
        }

        public Builder tags(Map<String, String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder localEndpoint(String serviceName) {
            this.localEndpoint = new Endpoint(serviceName);
            return this;
        }

        public Builder remoteEndpoint(String serviceName) {
            this.remoteEndpoint = new Endpoint(serviceName);
            return this;
        }

        public DeltafiSpan build() {
            return new DeltafiSpan(id, traceId, parentId, name, timestamp, duration, kind, tags, localEndpoint, remoteEndpoint);
        }

    }
}
