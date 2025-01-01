/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.core.repo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.deltafi.common.types.DeltaFileFlowState;
import org.deltafi.common.types.FlowType;
import org.deltafi.core.generated.types.CountPerFlow;
import org.deltafi.core.generated.types.CountPerMessage;
import org.deltafi.core.generated.types.DeltaFileDirection;
import org.deltafi.core.types.*;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
public class DeltaFileFlowRepoImpl implements DeltaFileFlowRepoCustom {
    @PersistenceContext
    EntityManager entityManager;

    @Override
    public Map<String, Integer> errorCountsByDataSource(Set<String> dataSources) {
        String sql = """
            SELECT df.data_source, COUNT(*) AS count
            FROM delta_file_flows dff
            JOIN delta_files df ON df.did = dff.delta_file_id
            WHERE dff.state = 'ERROR'
            AND dff.error_acknowledged IS NULL
            AND df.data_source IN (:dataSources)
            GROUP BY df.data_source""";

        Query query = entityManager.createNativeQuery(sql)
                .setParameter("dataSources", dataSources);

        @SuppressWarnings("unchecked")
        List<Object[]> resultList = query.getResultList();
        return resultList.stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> ((Number) row[1]).intValue()));
    }

    @Override
    public SummaryByFlow getErrorSummaryByFlow(Integer offset, int limit, ErrorSummaryFilter filter, DeltaFileDirection direction) {
        return getSummaryByFlow(offset, limit, filter, direction, DeltaFileFlowState.ERROR);
    }

    @Override
    public SummaryByFlow getFilteredSummaryByFlow(Integer offset, int limit, FilteredSummaryFilter filter, DeltaFileDirection direction) {
        return getSummaryByFlow(offset, limit, filter, direction, DeltaFileFlowState.FILTERED);
    }

    @Override
    public SummaryByFlowAndMessage getErrorSummaryByMessage(Integer offset, int limit, ErrorSummaryFilter filter, DeltaFileDirection direction) {
        return getSummaryByFlowAndMessage(offset, limit, filter, direction, DeltaFileFlowState.ERROR);
    }

    @Override
    public SummaryByFlowAndMessage getFilteredSummaryByMessage(Integer offset, int limit, FilteredSummaryFilter filter, DeltaFileDirection direction) {
        return getSummaryByFlowAndMessage(offset, limit, filter, direction, DeltaFileFlowState.FILTERED);
    }

    private SummaryByFlow getSummaryByFlow(Integer offset, int limit, SummaryFilter filter, DeltaFileDirection direction, DeltaFileFlowState deltaFileFlowState) {
        StringBuilder sql = new StringBuilder("""
                SELECT name, type, COUNT(id) AS count, ARRAY_AGG(delta_file_id) AS dids
                FROM delta_file_flows
                WHERE state = CAST(:state AS dff_state_enum)\s""");

        addFilterClauses(sql, filter);

        sql.append("GROUP BY name, type ");

        addFooterClauses(sql, direction, false);

        Query query = entityManager.createNativeQuery(sql.toString())
                .setParameter("state", deltaFileFlowState.name())
                .setParameter("limit", limit)
                .setParameter("offset", offset != null ? offset : 0);

        addSummaryFilterParameters(query, filter);

        @SuppressWarnings("unchecked")
        List<Object[]> resultList = query.getResultList();
        List<CountPerFlow> countPerFlow = resultList.stream()
                .map(row -> new CountPerFlow((String) row[0], FlowType.valueOf((String) row[1]), ((Number) row[2]).intValue(), Arrays.stream((UUID[]) row[3]).sorted().distinct().toList()))
                .collect(Collectors.toList());

        Long totalCount = totalCountByFlow(filter, deltaFileFlowState);

        return new SummaryByFlow(offset != null ? offset : 0, countPerFlow.size(), totalCount.intValue(), countPerFlow);
    }

    private SummaryByFlowAndMessage getSummaryByFlowAndMessage(Integer offset, int limit, SummaryFilter filter, DeltaFileDirection direction, DeltaFileFlowState flowState) {
        StringBuilder sql = new StringBuilder("""
            SELECT error_or_filter_cause, name, type, COUNT(id) AS count, ARRAY_AGG(delta_file_id) AS dids
            FROM delta_file_flows
            WHERE state = CAST(:state AS dff_state_enum)\s""");

        addFilterClauses(sql, filter);

        sql.append("GROUP BY error_or_filter_cause, name, type ");

        addFooterClauses(sql, direction, true);

        Query query = entityManager.createNativeQuery(sql.toString())
                .setParameter("state", flowState.toString())
                .setParameter("limit", limit)
                .setParameter("offset", offset != null ? offset : 0);

        addSummaryFilterParameters(query, filter);

        @SuppressWarnings("unchecked")
        List<Object[]> resultList = query.getResultList();
        List<CountPerMessage> countPerMessage = resultList.stream()
                .map(row -> new CountPerMessage((String) row[0], (String) row[1], FlowType.valueOf((String) row[2]),((Number) row[3]).intValue(), Arrays.stream((UUID[]) row[4]).sorted().distinct().toList()))
                .collect(Collectors.toList());

        Long totalCount = totalCountByFlowAndMessage(filter, flowState);

        return new SummaryByFlowAndMessage(offset != null ? offset : 0, countPerMessage.size(), totalCount.intValue(), countPerMessage);
    }

    private void addFilterClauses(StringBuilder sql, SummaryFilter filter) {
        if (filter != null) {
            if (filter.getModifiedAfter() != null) {
                sql.append("AND modified > :modifiedAfter ");
            }
            if (filter.getModifiedBefore() != null) {
                sql.append("AND modified < :modifiedBefore ");
            }
            if (filter.getFlow() != null) {
                sql.append("AND name = :flow ");
            }

            if (filter instanceof ErrorSummaryFilter errorFilter && errorFilter.getErrorAcknowledged() != null) {
                if (errorFilter.getErrorAcknowledged()) {
                    sql.append("AND error_acknowledged IS NOT NULL ");
                } else {
                    sql.append("AND error_acknowledged IS NULL ");
                }
            }
        }
    }

    private void addFooterClauses(StringBuilder sql, DeltaFileDirection direction, boolean includeCause) {
        sql.append("ORDER BY name");
        if (direction == null || direction == DeltaFileDirection.ASC) {
            sql.append(" ASC ");
        } else {
            sql.append(" DESC ");
        }

        if (includeCause) {
            sql.append(", error_or_filter_cause");
            if (direction == null || direction == DeltaFileDirection.ASC) {
                sql.append(" ASC ");
            } else {
                sql.append(" DESC ");
            }
        }

        sql.append("LIMIT :limit OFFSET :offset");
    }

    private Long totalCountByFlow(SummaryFilter filter, DeltaFileFlowState flowState) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM (
              SELECT DISTINCT name
              FROM delta_file_flows
              WHERE state = CAST(:state AS dff_state_enum)\s""");

        addFilterClauses(sql, filter);

        sql.append(")");

        Query query = entityManager.createNativeQuery(sql.toString())
                .setParameter("state", flowState.toString());

        addSummaryFilterParameters(query, filter);

        return ((Number) query.getSingleResult()).longValue();
    }

    private Long totalCountByFlowAndMessage(SummaryFilter filter, DeltaFileFlowState flowState) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM (
              SELECT DISTINCT error_or_filter_cause, name
              FROM delta_file_flows
              WHERE error_or_filter_cause IS NOT NULL
              AND state = CAST(:state AS dff_state_enum)\s""");

        addFilterClauses(sql, filter);

        sql.append(")");

        Query query = entityManager.createNativeQuery(sql.toString())
                .setParameter("state", flowState.toString());

        addSummaryFilterParameters(query, filter);

        return ((Number) query.getSingleResult()).longValue();
    }

    void addSummaryFilterParameters(Query query, SummaryFilter filter) {
        if (filter != null) {
            if (filter.getModifiedAfter() != null) {
                query.setParameter("modifiedAfter", filter.getModifiedAfter());
            }
            if (filter.getModifiedBefore() != null) {
                query.setParameter("modifiedBefore", filter.getModifiedBefore());
            }
            if (filter.getFlow() != null) {
                query.setParameter("flow", filter.getFlow());
            }
        }
    }

    @Override
    public List<ColdQueuedActionSummary> coldQueuedActionsSummary() {
        String queryStr = """
            SELECT
                (actions->(jsonb_array_length(actions) - 1))->>'n' as actionName,
                type as type,
                COUNT(*) as count
            FROM delta_file_flows
            WHERE state = 'IN_FLIGHT'
            AND cold_queued = TRUE
            GROUP BY (actions->(jsonb_array_length(actions) - 1))->>'n', type
        """;

        Query query = entityManager.createNativeQuery(queryStr);
        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        return results.stream()
                .map(result -> new ColdQueuedActionSummary(
                        (String) result[0],
                        FlowType.valueOf((String) result[1]),
                        ((Number) result[2]).longValue()
                ))
                .collect(Collectors.toList());
    }
}
