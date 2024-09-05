package org.deltafi.core.types;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.common.types.Content;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
public class DeltaFileDeleteDTO {
    private UUID did;
    private OffsetDateTime contentDeleted;
    private long totalBytes;
    private List<Content> content;

    public DeltaFileDeleteDTO(UUID did, OffsetDateTime contentDeleted, long totalBytes, List<Content> content) {
        this.did = did;
        this.contentDeleted = contentDeleted;
        this.totalBytes = totalBytes;
        setContent(content);
    }

    public void setContent(List<Content> content) {
        this.content = content == null ? Collections.emptyList() : content;
        for (Content c : this.content) {
            c.setSegments(c.getSegments().stream()
                    .filter(s -> s.getDid().equals(this.did))
                    .toList());
        }
        this.content = this.content.stream()
                .filter(c -> !c.getSegments().isEmpty())
                .toList();
    }
}
