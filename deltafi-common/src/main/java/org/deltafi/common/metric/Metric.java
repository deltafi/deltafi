package org.deltafi.common.metric;

import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.Map;

@Data
@Builder
public class Metric {
    private String source;
    private String name;
    private long value;
    private MetricType type;
    private Date timestamp;
    private Map<String, String> tags;
}
