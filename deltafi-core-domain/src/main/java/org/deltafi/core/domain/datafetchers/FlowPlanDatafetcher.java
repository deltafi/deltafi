package org.deltafi.core.domain.datafetchers;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import lombok.RequiredArgsConstructor;
import org.deltafi.core.domain.api.types.FlowPlan;
import org.deltafi.core.domain.generated.types.FlowPlanInput;
import org.deltafi.core.domain.services.FlowPlanService;

import java.util.Collection;

@DgsComponent
@RequiredArgsConstructor
public class FlowPlanDatafetcher {

    private final FlowPlanService flowPlanService;

    @DgsQuery
    public Collection<FlowPlan> flowPlans() {
        return flowPlanService.getAll();
    }

    @DgsMutation
    public FlowPlan saveFlowPlan(FlowPlanInput flowPlan) {
        return flowPlanService.save(flowPlan);
    }

    @DgsMutation
    public boolean removeFlowPlan(String name) {
        return flowPlanService.delete(name);
    }
}
