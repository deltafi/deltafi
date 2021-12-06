package org.deltafi.config.server.repo;

import org.deltafi.config.server.api.domain.PropertySet;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PropertyRepository extends MongoRepository<PropertySet, String>, PropertyCustomRepository {
}
