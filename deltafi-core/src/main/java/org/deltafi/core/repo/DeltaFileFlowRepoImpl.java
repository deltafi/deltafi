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
import org.deltafi.core.generated.types.*;
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
    public SummaryByFlow getErrorSummaryByFlow(Integer offset, int limit, ErrorSummaryFilter filter, DeltaFileDirection direction, SummaryByFlowSort sortField) {
        return getSummaryByFlow(offset, limit, filter, direction, sortField, DeltaFileFlowState.ERROR);
    }

    @Override
    public SummaryByFlow getFilteredSummaryByFlow(Integer offset, int limit, FilteredSummaryFilter filter, DeltaFileDirection direction, SummaryByFlowSort sortField) {
        return getSummaryByFlow(offset, limit, filter, direction, sortField, DeltaFileFlowState.FILTERED);
    }

    @Override
    public SummaryByFlowAndMessage getErrorSummaryByMessage(Integer offset, int limit, ErrorSummaryFilter filter, DeltaFileDirection direction, SummaryByMessageSort sortField) {
        return getSummaryByFlowAndMessage(offset, limit, filter, direction, sortField, DeltaFileFlowState.ERROR);
    }

    @Override
    public SummaryByFlowAndMessage getFilteredSummaryByMessage(Integer offset, int limit, FilteredSummaryFilter filter, DeltaFileDirection direction, SummaryByMessageSort sortField) {
        return getSummaryByFlowAndMessage(offset, limit, filter, direction, sortField, DeltaFileFlowState.FILTERED);
    }

    private SummaryByFlow getSummaryByFlow(Integer offset, int limit, SummaryFilter filter, DeltaFileDirection direction, SummaryByFlowSort sortField, DeltaFileFlowState deltaFileFlowState) {
        StringBuilder sql = new StringBuilder("""
                SELECT fd.name, fd.type, COUNT(dff.id) AS count, ARRAY_AGG(dff.delta_file_id) AS dids
                FROM delta_file_flows dff
                LEFT JOIN flow_definitions fd
                ON dff.flow_definition_id = fd.id
                WHERE state = CAST(:state AS dff_state_enum)\s""");

        addFilterClauses(sql, filter);

        sql.append("GROUP BY fd.name, fd.type ");

        addFooterClauses(sql, sortField.toString().toLowerCase(), direction, false);

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

    private SummaryByFlowAndMessage getSummaryByFlowAndMessage(Integer offset, int limit, SummaryFilter filter, DeltaFileDirection direction, SummaryByMessageSort sortField, DeltaFileFlowState flowState) {
        StringBuilder sql = new StringBuilder("""
            SELECT dff.error_or_filter_cause, fd.name, fd.type, COUNT(dff.id) AS count, ARRAY_AGG(dff.delta_file_id) AS dids
            FROM delta_file_flows dff
            JOIN flow_definitions fd
            ON dff.flow_definition_id = fd.id
            WHERE dff.state = CAST(:state AS dff_state_enum)\s""");

        addFilterClauses(sql, filter);

        sql.append("GROUP BY dff.error_or_filter_cause, fd.name, fd.type ");

        addFooterClauses(sql, sortField == SummaryByMessageSort.MESSAGE ? "error_or_filter_cause" : sortField.toString().toLowerCase(), direction, true);

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
                sql.append("AND dff.modified > :modifiedAfter ");
            }
            if (filter.getModifiedBefore() != null) {
                sql.append("AND dff.modified < :modifiedBefore ");
            }
            if (filter.getFlow() != null) {
                sql.append("AND fd.name = :flow ");
            }

            if (filter instanceof ErrorSummaryFilter errorFilter && errorFilter.getErrorAcknowledged() != null) {
                if (errorFilter.getErrorAcknowledged()) {
                    sql.append("AND dff.error_acknowledged IS NOT NULL ");
                } else {
                    sql.append("AND dff.error_acknowledged IS NULL ");
                }
            }
        }
    }

    private void addFooterClauses(StringBuilder sql, String orderBy, DeltaFileDirection direction, boolean includeCause) {
        sql.append("ORDER BY ");
        sql.append(orderBy);
        if (direction == null || direction == DeltaFileDirection.ASC) {
            sql.append(" ASC ");
        } else {
            sql.append(" DESC ");
        }

        if (includeCause) {
            String secondary = orderBy.equals("name") ? "error_or_filter_cause" : "name";
            sql.append(", ");
            sql.append(secondary);
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
              SELECT DISTINCT fd.name
              FROM delta_file_flows dff
              LEFT JOIN flow_definitions fd
              ON dff.flow_definition_id = fd.id
              WHERE dff.state = CAST(:state AS dff_state_enum)\s""");

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
              SELECT DISTINCT dff.error_or_filter_cause, fd.name
              FROM delta_file_flows dff
              JOIN flow_definitions fd
              ON dff.flow_definition_id = fd.id
              WHERE dff.error_or_filter_cause IS NOT NULL
              AND dff.state = CAST(:state AS dff_state_enum)\s""");

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
    public List<String> distinctColdQueuedActions() {
        String queryStr = """
            SELECT DISTINCT dff.cold_queued_action
            FROM delta_file_flows dff
            WHERE dff.state = 'IN_FLIGHT'
            AND dff.cold_queued_action IS NOT NULL
        """;

        @SuppressWarnings("unchecked")
        List<String> results = entityManager.createNativeQuery(queryStr).getResultList();
        return results.stream().filter(Objects::nonNull).toList();
    }

    @Override
    public boolean isColdQueued(String actionClass) {
        String queryStr = """
            SELECT 1 FROM delta_file_flows dff
            WHERE dff.state = 'IN_FLIGHT'
            AND dff.cold_queued_action = :actionClass
            LIMIT 1
        """;

        Query query = entityManager.createNativeQuery(queryStr).setParameter("actionClass", actionClass);
        @SuppressWarnings("unchecked")
        List<Object[]> results = query.getResultList();

        return !results.isEmpty();
    }

    @Override
    public Map<String, Integer> coldQueuedActionsCount() {
        String sql = """
                SELECT cold_queued_action , COUNT(*) AS count
                FROM delta_file_flows
                WHERE state  = 'IN_FLIGHT'
                AND cold_queued_action IS NOT NULL
                GROUP BY cold_queued_action""";

        Query query = entityManager.createNativeQuery(sql);

        @SuppressWarnings("unchecked")
        List<Object[]> resultList = query.getResultList();
        return resultList.stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> ((Number) row[1]).intValue()));
    }

    @Override
    public Long coldQueuedCount(int limit) {
        String sql = """
                SELECT COUNT(*) AS count
                FROM (SELECT 1 FROM delta_file_flows
                WHERE state  = 'IN_FLIGHT'
                AND cold_queued_action IS NOT NULL
                LIMIT :limit) AS a""";

        Query query = entityManager.createNativeQuery(sql)
                .setParameter("limit", limit);

        return ((Number) query.getSingleResult()).longValue();
    }

}
