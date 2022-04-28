package org.deltafi.core.domain.plugin;

import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class PluginValidatorTest {
    @Mock
    PluginRepository pluginRepository;

    @InjectMocks
    PluginValidator pluginValidator;

    @Test
    public void validate() {
        Plugin plugin1 = new Plugin();
        PluginCoordinates pluginCoordinates1 = new PluginCoordinates("group", "plugin-1", "1.0.0");
        plugin1.setPluginCoordinates(pluginCoordinates1);
        Plugin plugin2 = new Plugin();
        PluginCoordinates pluginCoordinates2 = new PluginCoordinates("group", "plugin-2", "1.0.0");
        plugin2.setPluginCoordinates(pluginCoordinates2);

        Mockito.when(pluginRepository.findAll()).thenReturn(List.of(plugin1, plugin2));

        Plugin plugin = new Plugin();
        plugin.setPluginCoordinates(new PluginCoordinates("group", "plugin", "1.0.0"));
        plugin.setDependencies(List.of(new PluginCoordinates("group", "unregistered", "1.0.0"),
                new PluginCoordinates("group", "plugin-1", "1.0.1")));
        List<String> dependencyErrors = pluginValidator.validate(plugin);

        assertEquals(2, dependencyErrors.size());
        assertEquals("Plugin dependency not registered: group:unregistered:1.0.0.", dependencyErrors.get(0));
        assertEquals("Plugin dependency for group:plugin-1 not satisfied. Required version 1.0.1 but installed version is 1.0.0.",
                dependencyErrors.get(1));
    }
}
