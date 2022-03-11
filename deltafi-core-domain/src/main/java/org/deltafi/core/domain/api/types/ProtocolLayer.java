package org.deltafi.core.domain.api.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.deltafi.common.content.ContentReference;
import org.deltafi.core.domain.generated.types.Content;

import java.util.List;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProtocolLayer {
    private String type;
    private String action;
    private List<Content> content;
    private List<KeyValue> metadata;

    @JsonIgnore
    public ContentReference getContentReference() {
        return content.isEmpty() ? null : content.get(0).getContentReference();
    }
}
