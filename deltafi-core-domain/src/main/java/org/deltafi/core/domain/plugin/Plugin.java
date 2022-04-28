package org.deltafi.core.domain.plugin;

import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class Plugin extends org.deltafi.core.domain.generated.types.Plugin {
    @Id
    @Override
    public PluginCoordinates getPluginCoordinates() {
        return super.getPluginCoordinates();
    }
}
