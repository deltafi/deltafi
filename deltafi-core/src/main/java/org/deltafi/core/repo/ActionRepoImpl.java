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
import lombok.RequiredArgsConstructor;
import org.deltafi.common.types.ActionState;
import org.deltafi.core.generated.types.CountPerFlow;
import org.deltafi.core.generated.types.CountPerMessage;
import org.deltafi.core.generated.types.DeltaFileDirection;
import org.deltafi.core.types.*;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ActionRepoImpl implements ActionRepoCustom {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public SummaryByFlow getErrorSummaryByFlow(Integer offset, int limit, ErrorSummaryFilter filter, DeltaFileDirection direction) {
        return getSummaryByFlow(offset, limit, filter, direction, ActionState.ERROR);
    }

    @Override
    public SummaryByFlow getFilteredSummaryByFlow(Integer offset, int limit, FilteredSummaryFilter filter, DeltaFileDirection direction) {
        return getSummaryByFlow(offset, limit, filter, direction, ActionState.FILTERED);
    }

    @Override
    public SummaryByFlowAndMessage getErrorSummaryByMessage(Integer offset, int limit, ErrorSummaryFilter filter, DeltaFileDirection direction) {
        return getSummaryByFlowAndMessage(offset, limit, filter, direction, ActionState.ERROR);
    }

    @Override
    public SummaryByFlowAndMessage getFilteredSummaryByMessage(Integer offset, int limit, FilteredSummaryFilter filter, DeltaFileDirection direction) {
        return getSummaryByFlowAndMessage(offset, limit, filter, direction, ActionState.FILTERED);
    }

    @Override
    public Map<String, Integer> errorCountsByFlow(Set<String> flows) {
        String sql = """
                SELECT df.name, COUNT(a.id) AS count
                FROM actions a JOIN delta_file_flows df ON a.delta_file_flow_id = df.id
                WHERE a.state = :state
                AND a.error_acknowledged IS NULL
                AND df.name IN (:flows)
                GROUP BY df.name""";

        Query query = entityManager.createNativeQuery(sql)
                .setParameter("state", ActionState.ERROR.toString())
                .setParameter("flows", flows);

        @SuppressWarnings("unchecked")
        List<Object[]> resultList = query.getResultList();
        return resultList.stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> ((Number) row[1]).intValue()));
    }

    private SummaryByFlow getSummaryByFlow(Integer offset, int limit, SummaryFilter filter, DeltaFileDirection direction, ActionState actionState) {
        StringBuilder sql = new StringBuilder("""
                SELECT df.name, COUNT(a.id) AS count, ARRAY_AGG(df.delta_file_id) AS dids
                FROM actions a JOIN delta_file_flows df ON a.delta_file_flow_id = df.id
                WHERE a.state = :state\s""");

        addFilterClauses(sql, filter);

        sql.append("GROUP BY df.name ");

        addFooterClauses(sql, direction, null);

        Query query = entityManager.createNativeQuery(sql.toString())
                .setParameter("state", actionState.toString())
                .setParameter("limit", limit)
                .setParameter("offset", offset != null ? offset : 0);

        addSummaryFilterParameters(query, filter);

        @SuppressWarnings("unchecked")
        List<Object[]> resultList = query.getResultList();
        List<CountPerFlow> countPerFlow = resultList.stream()
                .map(row -> new CountPerFlow((String) row[0], ((Number) row[1]).intValue(), Arrays.stream((UUID[]) row[2]).sorted().distinct().toList()))
                .collect(Collectors.toList());

        Long totalCount = totalCountByFlow(filter, actionState);

        return new SummaryByFlow(offset != null ? offset : 0, countPerFlow.size(), totalCount.intValue(), countPerFlow);
    }

    private String causeField(ActionState actionState) {
        return actionState == ActionState.ERROR ? "error_cause" : "filtered_cause";
    }

    private SummaryByFlowAndMessage getSummaryByFlowAndMessage(Integer offset, int limit, SummaryFilter filter, DeltaFileDirection direction, ActionState actionState) {
        String causeField = causeField(actionState);
        StringBuilder sql = new StringBuilder("""
            SELECT a.%s, df.name, COUNT(a.id) AS count, ARRAY_AGG(df.delta_file_id) AS dids
            FROM actions a JOIN delta_file_flows df ON a.delta_file_flow_id = df.id
            WHERE a.%s IS NOT NULL
            AND a.state = :state\s""".formatted(causeField, causeField));

        addFilterClauses(sql, filter);

        sql.append("GROUP BY a.%s, df.name ".formatted(causeField));

        addFooterClauses(sql, direction, causeField);

        Query query = entityManager.createNativeQuery(sql.toString())
                .setParameter("state", actionState.toString())
                .setParameter("limit", limit)
                .setParameter("offset", offset != null ? offset : 0);

        addSummaryFilterParameters(query, filter);

        @SuppressWarnings("unchecked")
        List<Object[]> resultList = query.getResultList();
        List<CountPerMessage> countPerMessage = resultList.stream()
                .map(row -> new CountPerMessage((String) row[0], (String) row[1], ((Number) row[2]).intValue(), Arrays.stream((UUID[]) row[3]).sorted().distinct().toList()))
                .collect(Collectors.toList());

        Long totalCount = totalCountByFlowAndMessage(filter, actionState, causeField);

        return new SummaryByFlowAndMessage(offset != null ? offset : 0, countPerMessage.size(), totalCount.intValue(), countPerMessage);
    }

    private void addFilterClauses(StringBuilder sql, SummaryFilter filter) {
        if (filter != null) {
            if (filter.getModifiedAfter() != null) {
                sql.append("AND a.modified > :modifiedAfter ");
            }
            if (filter.getModifiedBefore() != null) {
                sql.append("AND a.modified < :modifiedBefore ");
            }
            if (filter.getFlow() != null) {
                sql.append("AND df.name = :flow ");
            }

            if (filter instanceof ErrorSummaryFilter errorFilter && errorFilter.getErrorAcknowledged() != null) {
                if (errorFilter.getErrorAcknowledged()) {
                    sql.append("AND a.error_acknowledged IS NOT NULL ");
                } else {
                    sql.append("AND a.error_acknowledged IS NULL ");
                }
            }
        }
    }

    private void addFooterClauses(StringBuilder sql, DeltaFileDirection direction, String causeField) {
        sql.append("ORDER BY df.name");
        if (causeField != null) {
            sql.append(", a.%s".formatted(causeField));
        }

        if (direction == null || direction == DeltaFileDirection.ASC) {
            sql.append(" ASC ");
        } else {
            sql.append(" DESC ");
        }

        sql.append("LIMIT :limit OFFSET :offset");
    }

    private Long totalCountByFlow(SummaryFilter filter, ActionState actionState) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM (
              SELECT DISTINCT df.name
              FROM actions a JOIN delta_file_flows df ON a.delta_file_flow_id = df.id
              WHERE a.state = :state\s""");

        addFilterClauses(sql, filter);

        sql.append(")");

        Query query = entityManager.createNativeQuery(sql.toString())
                .setParameter("state", actionState.toString());

        addSummaryFilterParameters(query, filter);

        return ((Number) query.getSingleResult()).longValue();
    }

    private Long totalCountByFlowAndMessage(SummaryFilter filter, ActionState actionState, String causeField) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM (
              SELECT DISTINCT a.%s, df.name
              FROM actions a JOIN delta_file_flows df ON a.delta_file_flow_id = df.id
              WHERE a.%s IS NOT NULL
              AND a.state = :state\s""".formatted(causeField, causeField));

        addFilterClauses(sql, filter);

        sql.append(")");

        Query query = entityManager.createNativeQuery(sql.toString())
                .setParameter("state", actionState.toString());

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
}
