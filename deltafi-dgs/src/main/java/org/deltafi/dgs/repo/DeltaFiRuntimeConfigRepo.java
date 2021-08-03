package org.deltafi.dgs.repo;

import org.deltafi.dgs.configuration.DeltafiRuntimeConfiguration;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeltaFiRuntimeConfigRepo extends MongoRepository<DeltafiRuntimeConfiguration, String> {
}
