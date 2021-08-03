package org.deltafi.dgs.configuration;

import java.time.OffsetDateTime;


public interface DeltaFiConfiguration extends org.deltafi.dgs.generated.types.DeltaFiConfiguration {

    String getName();
    OffsetDateTime getCreated();
    OffsetDateTime getModified();
    void setName(String name);
    void setModified(OffsetDateTime modified);
    void setCreated(OffsetDateTime created);
}