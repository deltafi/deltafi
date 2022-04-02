package org.deltafi.core.parameters;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum DecompressionType {
    @JsonProperty("auto") AUTO("auto"),
    @JsonProperty("tar") TAR("tar"),
    @JsonProperty("zip") ZIP("zip"),
    @JsonProperty("gz") GZIP("gz"),
    @JsonProperty("xz") XZ("xz"),
    @JsonProperty("z") Z("z"),
    @JsonProperty("ar") AR("ar"),
    @JsonProperty("tar.z") TAR_Z("tar.z"),
    @JsonProperty("tar.xz") TAR_XZ("tar.xz"),
    @JsonProperty("tar.gz") TAR_GZIP("tar.gz");

    @Getter
    private final String value;
}