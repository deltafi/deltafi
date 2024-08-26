package org.deltafi.core.services;

import lombok.RequiredArgsConstructor;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.repo.FlowRepo;
import org.deltafi.core.types.Flow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlowCacheService {
    private final FlowRepo flowRepo;
    private volatile Map<FlowKey, Flow> flowCache;

    private record FlowKey(String name, FlowType type) {}

    public void refreshCache() {
        flowCache = flowRepo.findAll().stream()
                .collect(Collectors.toMap(
                        flow -> new FlowKey(flow.getName(), flow.getType()),
                        flow -> flow
                ));
    }

    public List<Flow> flowsOfType(FlowType flowType) {
        return flowCache.entrySet().stream()
                .filter(entry -> entry.getKey().type() == flowType)
                .map(Map.Entry::getValue)
                .toList();
    }

    public List<String> getNamesOfInvalidFlows(FlowType flowType) {
        return flowsOfType(flowType).stream()
                .filter(Flow::isInvalid)
                .map(Flow::getName)
                .toList();
    }

    public Flow getFlow(FlowType flowType, String flowName) {
        return flowCache.getOrDefault(new FlowKey(flowName, flowType), null);
    }

    public Flow getRunningFlow(FlowType flowType, String flowName) {
        Flow flow = getFlow(flowType, flowName);
        if (flow == null || !flow.isRunning()) {
            return null;
        }

        return flow;
    }

    public Flow getFlowOrThrow(FlowType flowType, String flowName) {
        Flow flow = getFlow(flowType, flowName);
        if (flow == null) {
            throw new IllegalArgumentException("No " + flowType + " flow exists with the name: " + flowName);
        }

        return flow;
    }
}
