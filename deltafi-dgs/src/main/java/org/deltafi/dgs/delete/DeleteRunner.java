package org.deltafi.dgs.delete;

import lombok.Getter;
import org.deltafi.dgs.configuration.DeltaFiProperties;
import org.deltafi.dgs.services.DeltaFilesService;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeleteRunner {
    private static final Map<String, Class<? extends DeletePolicy>> DELETE_POLICY_TYPES = new HashMap<>(Map.of("ageOff", TimedDelete.class));

    @Getter
    private final List<DeletePolicy> deletePolicies = new ArrayList<>();

    public DeleteRunner(DeltaFilesService deltaFilesService, DeltaFiProperties deltaFiProperties) {
        deltaFiProperties.getDelete().getPolicies().forEach((name, config) -> {
            if (!DELETE_POLICY_TYPES.containsKey(config.getType())) {
                throw new IllegalArgumentException("Unknown delete policy type " + config.getType() + " configured in policy " + name);
            }
            try {
                Constructor<? extends DeletePolicy> c = DELETE_POLICY_TYPES.get(config.getType()).getDeclaredConstructor(DeltaFilesService.class, String.class, Map.class);
                c.setAccessible(true);
                DeletePolicy deletePolicy = c.newInstance(deltaFilesService, name, config.getParameters());
                deletePolicies.add(deletePolicy);
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException("Unable to create delete policy of type " + config.getType() + " configured in policy " + name + ":  " + e.getMessage());
            }
        });
    }

    public void runDeletes() {
        deletePolicies.forEach(DeletePolicy::run);
    }
}
