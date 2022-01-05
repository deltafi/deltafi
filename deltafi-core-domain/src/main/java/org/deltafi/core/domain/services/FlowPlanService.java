package org.deltafi.core.domain.services;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final FlowPlanRepo flowPlanRepo;

    public boolean delete(String name) {
        return flowPlanRepo.deleteByName(name) > 0;
    }

    public List<FlowPlan> getAll() {
        return flowPlanRepo.findAll();
    }

    public FlowPlan save(FlowPlanInput flowPlanInput) {
        FlowPlan flowPlan = objectMapper.convertValue(flowPlanInput, FlowPlan.class);
        return flowPlanRepo.save(flowPlan);
    }
}
