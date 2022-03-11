package org.deltafi.core.parameters;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum DecompressionType {
    @JsonProperty("tar") TAR("tar"),
    @JsonProperty("zip") ZIP("zip"),
    @JsonProperty("gzip") GZIP("gzip"),
    @JsonProperty("tar.gz") TAR_GZIP("tar.gz");

    @Getter
    private final String value;
}