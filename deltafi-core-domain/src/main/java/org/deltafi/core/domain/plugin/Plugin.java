package org.deltafi.core.domain.plugin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class Plugin extends org.deltafi.core.domain.generated.types.Plugin {
    @Id
    @JsonIgnore
    public String getId() {
        return getPluginCoordinates().getGroupId() + ":" + getPluginCoordinates().getArtifactId() + ":" +
                getPluginCoordinates().getVersion();
    }
}
