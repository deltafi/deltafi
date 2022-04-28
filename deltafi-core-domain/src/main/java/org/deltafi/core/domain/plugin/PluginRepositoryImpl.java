package org.deltafi.core.domain.plugin;

import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

public class PluginRepositoryImpl implements PluginRepositoryCustom {

    private static final String DEPENDENCIES = "dependencies";

    private final MongoTemplate mongoTemplate;

    public PluginRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<Plugin> findPluginsWithDependency(PluginCoordinates pluginCoordinates) {
        return mongoTemplate.find(Query.query(
                Criteria.where(DEPENDENCIES).in(pluginCoordinates)), Plugin.class);
    }
}
