package org.deltafi.core.domain.types;

import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("pluginVariable")
public class PluginVariables extends org.deltafi.core.domain.generated.types.PluginVariables {

    @Id
    @Override
    public PluginCoordinates getSourcePlugin() {
        return super.getSourcePlugin();
    }
}
