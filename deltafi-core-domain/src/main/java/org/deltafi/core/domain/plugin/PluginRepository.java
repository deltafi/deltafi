package org.deltafi.core.domain.plugin;

import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.springframework.data.mongodb.repository.DeleteQuery;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PluginRepository extends MongoRepository<Plugin, PluginCoordinates>, PluginRepositoryCustom {

    @DeleteQuery("{ 'pluginCoordinates.groupId': ?0, 'pluginCoordinates.artifactId' : ?1 }")
    void deleteOlderVersions(String groupId, String artifactId);

}
