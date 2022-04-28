package org.deltafi.core.domain.configuration;

import java.io.Serializable;

public interface DeltaFiConfiguration extends org.deltafi.core.domain.generated.types.DeltaFiConfiguration, Serializable {

    String getName();
    void setName(String name);
}