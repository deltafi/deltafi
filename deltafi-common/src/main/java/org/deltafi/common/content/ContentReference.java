package org.deltafi.common.content;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.deltafi.common.storage.s3.ObjectReference;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentReference {
    private String uuid;
    private long offset;
    private long size;
    private String did;
    private String mediaType;

    public ContentReference(String uuid, String did, String mediaType) {
        this(uuid, 0, ObjectReference.UNKNOWN_SIZE, did, mediaType);
    }
}
