package org.deltafi.core.domain.api.types;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.deltafi.core.domain.generated.types.*;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;

/*
 * This is the codegen generated class, except the Spring-Mongo @Document annotation is added.
 */

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "__typename"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DeleteActionSchema.class, name = "DeleteActionSchema"),
        @JsonSubTypes.Type(value = EgressActionSchema.class, name = "EgressActionSchema"),
        @JsonSubTypes.Type(value = EnrichActionSchema.class, name = "EnrichActionSchema"),
        @JsonSubTypes.Type(value = FormatActionSchema.class, name = "FormatActionSchema"),
        @JsonSubTypes.Type(value = LoadActionSchema.class, name = "LoadActionSchema"),
        @JsonSubTypes.Type(value = TransformActionSchema.class, name = "TransformActionSchema"),
        @JsonSubTypes.Type(value = ValidateActionSchema.class, name = "ValidateActionSchema")
})
@Document("actionSchema")
public interface ActionSchema {
    String getId();

    String getParamClass();

    String getActionKitVersion();

    JsonMap getSchema();

    OffsetDateTime getLastHeard();
}
