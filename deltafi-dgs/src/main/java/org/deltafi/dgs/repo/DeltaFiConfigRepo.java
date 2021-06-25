package org.deltafi.dgs.repo;

import org.deltafi.dgs.api.types.ConfigType;
import org.deltafi.dgs.configuration.DeltaFiConfiguration;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeltaFiConfigRepo extends MongoRepository<DeltaFiConfiguration, String>, DeltaFiConfigRepoCustom {

    List<DeltaFiConfiguration> findAllByConfigType(ConfigType configType);

    Optional<DeltaFiConfiguration> findByNameAndConfigType(String name, ConfigType configType);

    long deleteByNameAndConfigType(String name, ConfigType configType);

    long deleteAllByConfigType(ConfigType configType);

}
