package org.deltafi.dgs.delete;

import org.deltafi.dgs.configuration.DeletePolicyConfiguration;
import org.deltafi.dgs.configuration.DeltaFiProperties;
import org.deltafi.dgs.services.DeltaFilesService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

@Service
@EnableScheduling
public class DeleteScheduler {
    static private final Map<String, Class<? extends DeletePolicy>> DELETE_POLICY_TYPES;
    static {
        Map<String, Class<? extends DeletePolicy>> deletePolicyTypes = new HashMap<>();
        deletePolicyTypes.put("ageOff", TimedDelete.class);
        DELETE_POLICY_TYPES = deletePolicyTypes;
    }

    final private List<DeletePolicy> deletePolicies = new ArrayList<>();

    public List<DeletePolicy> getDeletePolicies() {
        return deletePolicies;
    }

    @SuppressWarnings("unused")
    public DeleteScheduler(DeltaFilesService deltaFilesService, DeltaFiProperties deltaFiProperties) {
        deltaFiProperties.getDelete().getPolicies().keySet().forEach(name -> {
            DeletePolicyConfiguration config = deltaFiProperties.getDelete().getPolicies().get(name);
            String type = config.getType();
            if (DELETE_POLICY_TYPES.containsKey(type)) {
                try {
                    Constructor<? extends DeletePolicy> c = DELETE_POLICY_TYPES.get(type).getDeclaredConstructor(DeltaFilesService.class, String.class, Map.class);
                    c.setAccessible(true);
                    DeletePolicy deletePolicy = c.newInstance(deltaFilesService, name, config.getParameters());
                    deletePolicies.add(deletePolicy);
                } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException("Something has gone terribly wrong: " + e.getMessage());
                }
            } else {
                throw new IllegalArgumentException("Unknown delete policy type " + type + " configured in policy " + name);
            }
        });
    }

    @Scheduled(fixedDelayString = "#{deltaFiProperties.getDelete().getFrequency()}")
    public void runDeletes() {
        deletePolicies.forEach(DeletePolicy::run);
    }
}
