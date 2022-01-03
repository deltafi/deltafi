package org.deltafi.common.storage.s3;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ObjectReference {
    public static final long UNKNOWN_SIZE = -1;

    private String bucket;
    private String name;
    private long offset;
    private long size;

    public ObjectReference(String bucket, String name) {
        this(bucket, name, 0, UNKNOWN_SIZE);
    }
}
