package org.deltafi.core.domain.plugin;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PluginRepository extends MongoRepository<Plugin, String> {
}
