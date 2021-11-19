package org.deltafi.core.domain.api.types;

import org.deltafi.core.domain.generated.types.KeyValue;
import org.deltafi.core.domain.generated.types.SourceInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeltaFileTest {
    @Test
    void testSourceMetadata() {
        SourceInfo sourceInfo = SourceInfo.newBuilder()
                .metadata(List.of(
                        KeyValue.newBuilder().key("key1").value("value1").build(),
                        KeyValue.newBuilder().key("key2").value("value2").build()
                ))
                .build();

        DeltaFile deltaFile = DeltaFile.newBuilder()
                .sourceInfo(sourceInfo)
                .build();

        assertEquals("value1", deltaFile.sourceMetadata("key1"));
        assertEquals("value1", deltaFile.sourceMetadata("key1", "default"));
        assertEquals("value2", deltaFile.sourceMetadata("key2"));
        assertEquals("value2", deltaFile.sourceMetadata("key2", "default"));
        assertNull(deltaFile.sourceMetadata("key3"));
        assertEquals("default", deltaFile.sourceMetadata("key3", "default"));
    }
}
