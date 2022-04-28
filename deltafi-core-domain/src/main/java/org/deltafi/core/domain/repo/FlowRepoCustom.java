package org.deltafi.core.domain.repo;

import org.deltafi.core.domain.api.types.PluginCoordinates;
import org.deltafi.core.domain.generated.types.FlowState;
import org.deltafi.core.domain.types.Flow;

import java.util.List;

public interface FlowRepoCustom<T extends Flow> {

    List<T> findRunning();

    List<String> findRunningBySourcePlugin(PluginCoordinates sourcePlugin);

    boolean updateFlowState(String flowName, FlowState flowState);
}
