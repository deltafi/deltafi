package org.deltafi.common.trace;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ZipkinConfig {
    private boolean enabled = true;
    private long initialDelayInMilli = 500L;
    private long sendFrequencyInMilli = 500L;
    private int maxBatchSize = 10_000;
    private String url;
}
