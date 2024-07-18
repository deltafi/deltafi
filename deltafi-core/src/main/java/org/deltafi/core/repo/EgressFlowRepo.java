package org.deltafi.core.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
public interface EgressFlowRepo extends FlowRepo {
    @Modifying
    @Transactional
    @Query(value = "UPDATE flows SET expected_annotations = cast(:expectedAnnotations AS jsonb) WHERE name = :flowName AND type = 'EGRESS'",
            nativeQuery = true)
    int updateExpectedAnnotations(String flowName, String expectedAnnotations);

    @Transactional
    default int updateExpectedAnnotations(String flowName, Set<String> expectedAnnotations) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String jsonExpectedAnnotations = objectMapper.writeValueAsString(expectedAnnotations);
            return updateExpectedAnnotations(flowName, jsonExpectedAnnotations);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting expectedAnnotations to JSON", e);
        }
    }
}
