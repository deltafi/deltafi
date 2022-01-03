package org.deltafi.core.domain.api.types;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.common.content.ContentReference;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProtocolLayer {
    private String type;
    private String action;
    private ContentReference contentReference;
    private List<KeyValue> metadata;
}
