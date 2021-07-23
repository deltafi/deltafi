package org.deltafi.dgs.repo;

import org.deltafi.dgs.configuration.ActionConfiguration;
import org.deltafi.dgs.generated.types.ActionType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActionConfigRepo extends MongoRepository<ActionConfiguration, String>, ActionConfigRepoCustom {

    List<ActionConfiguration> findAllByActionType(ActionType actionType);

    List<ActionConfiguration> findByName(String name);

    Optional<ActionConfiguration> findByNameAndActionType(String name, ActionType actionType);

    long deleteByNameAndActionType(String name, ActionType actionType);

    long deleteAllByActionType(ActionType actionType);

}
