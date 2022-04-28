package org.deltafi.core.domain.repo;

import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.types.PluginVariables;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PluginVariableRepo extends MongoRepository<PluginVariables, PluginCoordinates> {
    @Query("{ 'sourcePlugin.groupId': ?0, 'sourcePlugin.artifactId' : ?1 }")
    Optional<PluginVariables> findIgnoringVersion(String groupId, String artifactId);
}
