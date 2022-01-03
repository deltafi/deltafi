package org.deltafi.core.domain.api.types;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SourceInfo {
    private String filename;
    private String flow;
    private List<KeyValue> metadata;
}
