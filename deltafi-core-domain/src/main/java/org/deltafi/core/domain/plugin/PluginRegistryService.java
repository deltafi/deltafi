package org.deltafi.core.domain.plugin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PluginRegistryService {
    private final PluginRepository pluginRepository;

    public List<Plugin> getPlugins() {
        return pluginRepository.findAll();
    }

    public void addPlugin(Plugin plugin) {
        pluginRepository.insert(plugin);
    }

    public void removeAllPlugins() {
        pluginRepository.deleteAll();
    }
}
