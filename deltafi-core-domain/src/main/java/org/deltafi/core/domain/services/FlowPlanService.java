package org.deltafi.core.domain.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.deltafi.core.domain.api.types.FlowPlan;
import org.deltafi.core.domain.generated.types.FlowPlanInput;
import org.deltafi.core.domain.repo.FlowPlanRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FlowPlanService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private final FlowPlanRepo flowPlanRepo;

    public boolean delete(String name) {
        return flowPlanRepo.deleteByName(name) > 0;
    }

    public String export(String name) throws JsonProcessingException {
        FlowPlan flowPlan = flowPlanRepo.findByName(name);
        return OBJECT_MAPPER
                .enable(SerializationFeature.INDENT_OUTPUT)
                .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .writeValueAsString(flowPlan);
    }

    public List<FlowPlan> getAll() {
        return flowPlanRepo.findAll();
    }

    public FlowPlan save(FlowPlanInput flowPlanInput) {
        FlowPlan flowPlan = OBJECT_MAPPER.convertValue(flowPlanInput, FlowPlan.class);
        return flowPlanRepo.save(flowPlan);
    }
}
