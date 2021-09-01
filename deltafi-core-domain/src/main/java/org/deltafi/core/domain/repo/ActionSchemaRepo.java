package org.deltafi.core.domain.repo;

import org.deltafi.core.domain.api.types.ActionSchemaImpl;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActionSchemaRepo extends MongoRepository<ActionSchemaImpl, String> {
}