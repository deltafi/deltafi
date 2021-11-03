package org.deltafi.core.domain.api.types;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.lang.String;
import java.time.OffsetDateTime;
import org.springframework.data.mongodb.core.mapping.Document;
import org.deltafi.core.domain.api.types.JsonMap;
import org.deltafi.core.domain.generated.types.TransformActionSchema;
import org.deltafi.core.domain.generated.types.LoadActionSchema;
import org.deltafi.core.domain.generated.types.FormatActionSchema;
import org.deltafi.core.domain.generated.types.GenericActionSchema;
import org.deltafi.core.domain.generated.types.EnrichActionSchema;

/*
 * This is the codegen generated class, except the Spring-Mongo @Document annotation is added.
 */

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "__typename"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TransformActionSchema.class, name = "TransformActionSchema"),
    @JsonSubTypes.Type(value = LoadActionSchema.class, name = "LoadActionSchema"),
    @JsonSubTypes.Type(value = EnrichActionSchema.class, name = "EnrichActionSchema"),
    @JsonSubTypes.Type(value = FormatActionSchema.class, name = "FormatActionSchema"),
    @JsonSubTypes.Type(value = GenericActionSchema.class, name = "GenericActionSchema")
})
@Document("actionSchema")
public interface ActionSchema {
  String getId();

  String getParamClass();

  String getActionKitVersion();

  JsonMap getSchema();

  OffsetDateTime getLastHeard();
}
