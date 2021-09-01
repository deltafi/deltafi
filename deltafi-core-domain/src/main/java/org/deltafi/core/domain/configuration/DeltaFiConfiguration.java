package org.deltafi.core.domain.configuration;

import java.time.OffsetDateTime;


public interface DeltaFiConfiguration extends org.deltafi.core.domain.generated.types.DeltaFiConfiguration {

    String getName();
    OffsetDateTime getCreated();
    OffsetDateTime getModified();
    void setName(String name);
    void setModified(OffsetDateTime modified);
    void setCreated(OffsetDateTime created);
}