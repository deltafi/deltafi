package org.deltafi.dgs.repo;

import org.deltafi.dgs.api.types.ActionSchemaImpl;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActionSchemaRepo extends MongoRepository<ActionSchemaImpl, String> {
}
