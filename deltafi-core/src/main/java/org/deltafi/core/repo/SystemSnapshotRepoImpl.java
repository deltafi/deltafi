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
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.generated.types.DeltaFileDirection;
import org.deltafi.core.generated.types.SystemSnapshotFilter;
import org.deltafi.core.generated.types.SystemSnapshotSort;
import org.deltafi.core.generated.types.SystemSnapshots;
import org.deltafi.core.types.snapshot.SystemSnapshot;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Repository
@RequiredArgsConstructor
@Slf4j
public class SystemSnapshotRepoImpl implements SystemSnapshotRepoCustom {
    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("([a-z])([A-Z])");
    @PersistenceContext(unitName = "primary")
    private final EntityManager entityManager;

    private static String toSnakeCase(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }

        return CAMEL_CASE_PATTERN
                .matcher(str)
                .replaceAll("$1_$2")
                .toLowerCase();
    }

    private void populateQuery(SystemSnapshotFilter filter, Map<String, Object> parameters, StringBuilder sqlQuery, boolean countOnly) {
        if (countOnly) {
            sqlQuery.append("SELECT COUNT(*) FROM system_snapshot ");
        } else {
            sqlQuery.append("SELECT * FROM system_snapshot ");
        }
        sqlQuery.append("WHERE TRUE ");
        String criteria = buildFilterCriteria(parameters, filter);
        sqlQuery.append(criteria);
    }

    private String buildFilterCriteria(Map<String, Object> parameters, SystemSnapshotFilter filter) {
        StringBuilder criteria = new StringBuilder();
        if (filter == null) {
            return criteria.toString();
        }

        if (filter.getCreatedAfter() != null) {
            criteria.append("AND created > :createdAfter ");
            parameters.put("createdAfter", filter.getCreatedAfter());
        }
        if (filter.getCreatedBefore() != null) {
            criteria.append("AND created < :createdBefore ");
            parameters.put("createdBefore", filter.getCreatedBefore());
        }

        if (filter.getReasonFilter() != null) {
            String reason = filter.getReasonFilter().getName();

            // default is NOT case-sensitive
            if (filter.getReasonFilter().getCaseSensitive() == null || !filter.getReasonFilter().getCaseSensitive()) {
                criteria.append("AND LOWER(reason) LIKE :reason ");
                parameters.put("reason", "%" + reason.toLowerCase() + "%");
            } else {
                criteria.append("AND reason LIKE :reason ");
                parameters.put("reason", "%" + reason + "%");
            }
        }
        return criteria.toString();
    }

    @Override
    public SystemSnapshots getSnapshotsByFilter(Integer offset, int limit, SystemSnapshotFilter filter, DeltaFileDirection direction, SystemSnapshotSort sortField) {
        Map<String, Object> parameters = new HashMap<>();
        StringBuilder sqlQuery = new StringBuilder();
        populateQuery(filter, parameters, sqlQuery, false);

        sqlQuery.append("ORDER BY ");
        if (sortField != null) {
            sqlQuery.append(toSnakeCase(sortField.name()))
                    .append(" ")
                    .append(direction == null || direction == DeltaFileDirection.ASC ? "ASC" : "DESC")
                    .append(" ");
        } else {
            sqlQuery.append("created DESC ");
        }
        sqlQuery.append("LIMIT :limit OFFSET :offset");

        Query query = entityManager.createNativeQuery(sqlQuery.toString(), SystemSnapshot.class);

        query.setParameter("limit", limit);
        query.setParameter("offset", offset == null ? 0 : offset);
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }

        @SuppressWarnings("unchecked")
        List<SystemSnapshot> snapshotList = query.getResultList();
        Long totalCount = totalCountByFlowAndMessage(filter);

        return new SystemSnapshots(offset != null ? offset : 0,
                snapshotList.size(), totalCount.intValue(), snapshotList);
    }

    private Long totalCountByFlowAndMessage(SystemSnapshotFilter filter) {
        Map<String, Object> parameters = new HashMap<>();
        StringBuilder sqlQuery = new StringBuilder();
        populateQuery(filter, parameters, sqlQuery, true);

        Query query = entityManager.createNativeQuery(sqlQuery.toString());
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }

        return ((Number) query.getSingleResult()).longValue();
    }
}
