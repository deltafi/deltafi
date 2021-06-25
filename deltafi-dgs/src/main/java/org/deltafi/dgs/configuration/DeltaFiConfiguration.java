package org.deltafi.dgs.configuration;

import org.bson.types.ObjectId;
import org.deltafi.dgs.api.types.ConfigType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;

@Document("deltaFiConfig")
@CompoundIndex(name = "name_type", unique = true, def = "{name: 1, configType: 1}")
public interface DeltaFiConfiguration extends org.deltafi.dgs.generated.types.DeltaFiConfiguration {

    @Id
    ObjectId getId();
    String getName();

    OffsetDateTime getModified();
    ConfigType getConfigType();

    void setName(String name);
    void setModified(OffsetDateTime modified);
}