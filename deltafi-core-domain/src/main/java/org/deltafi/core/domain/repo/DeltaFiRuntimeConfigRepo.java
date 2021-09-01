package org.deltafi.core.domain.repo;

import org.deltafi.core.domain.configuration.DeltafiRuntimeConfiguration;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeltaFiRuntimeConfigRepo extends MongoRepository<DeltafiRuntimeConfiguration, String> {
}