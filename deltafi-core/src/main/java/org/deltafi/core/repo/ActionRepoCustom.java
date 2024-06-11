package org.deltafi.core.repo;

import org.deltafi.core.generated.types.DeltaFileDirection;
import org.deltafi.core.types.ErrorSummaryFilter;
import org.deltafi.core.types.FilteredSummaryFilter;
import org.deltafi.core.types.SummaryByFlow;
import org.deltafi.core.types.SummaryByFlowAndMessage;

import java.util.Map;
import java.util.Set;

public interface ActionRepoCustom {
    /**
     * Count the number of errors per flow using the optional filter parameters, and return the requested
     * page of data based on offset and limit. All associated DeltaFile dids within each flow are included.
     *
     * @param offset  Offset to use for pagination (defaults to 0)
     * @param limit   Maximum number of flows to return
     * @param filter  Filters are used to constrain which DeltaFiles are used in computation
     * @param direction Determines what order the returned records will be sorted by
     * @return the SummaryByFlow
     */
    SummaryByFlow getErrorSummaryByFlow(Integer offset, int limit, ErrorSummaryFilter filter, DeltaFileDirection direction);

    /**
     * Count the number of filtered DeltaFiles per flow using the optional filter parameters, and return the requested
     * page of data based on offset and limit. All associated DeltaFile dids within each flow are included.
     *
     * @param offset  Offset to use for pagination (defaults to 0)
     * @param limit   Maximum number of flows to return
     * @param filter  Filters are used to constrain which DeltaFiles are used in computation
     * @param direction Determines what order the returned records will be sorted by
     * @return the SummaryByFlow
     */
    SummaryByFlow getFilteredSummaryByFlow(Integer offset, int limit, FilteredSummaryFilter filter, DeltaFileDirection direction);

    /**
     * Count the number of errors per errorMessage + flow using the optional filter parameters, and return the requested
     * page of data based on offset and limit. All associated DeltaFile dids within each errorMessage + flow grouping are included.
     *
     * @param offset  Offset to use for pagination (defaults to 0)
     * @param limit   Maximum number of errorMessage/flows to return
     * @param filter  Filters are used to constrain which DeltaFiles are used in computation
     * @param direction Determines what order the returned records will be sorted by
     * @return the SummaryByFlowAndMessage
     */
    SummaryByFlowAndMessage getErrorSummaryByMessage(Integer offset, int limit, ErrorSummaryFilter filter, DeltaFileDirection direction);

    /**
     * Count the number of filtered DeltaFiles per flow and filterCause using the optional filter parameters, and return the requested
     * page of data based on offset and limit. All associated DeltaFile dids within each filterCause + flow grouping are included.
     *
     * @param offset  Offset to use for pagination (defaults to 0)
     * @param limit   Maximum number of errorMessage/flows to return
     * @param filter  Filters are used to constrain which DeltaFiles are used in computation
     * @param direction Determines what order the returned records will be sorted by
     * @return the SummaryByFlowAndMessage
     */
    SummaryByFlowAndMessage getFilteredSummaryByMessage(Integer offset, int limit, FilteredSummaryFilter filter, DeltaFileDirection direction);

    /**
     * Retrieves the error counts for the specified set of flows.  Only unacknowledged errors are considered.
     *
     * @param flows A set of {@code String} values representing the flow names for which to retrieve error counts.
     * @return A {@code Map<String, Integer>} where the key is the flow name and the value is the error count for that flow.
     */
    Map<String, Integer> errorCountsByFlow(Set<String> flows);
}
