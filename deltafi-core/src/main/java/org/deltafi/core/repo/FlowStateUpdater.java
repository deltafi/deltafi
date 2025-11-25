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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.deltafi.core.generated.types.FlowState;
import org.deltafi.core.types.Flow;
import org.deltafi.core.types.FlowTagFilter;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FlowStateUpdater {

    @PersistenceContext(unitName = "primary")
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    public List<Flow> findByTags(FlowTagFilter filter) {
        return findByTagsAndNewState(filter, null);
    }

    public List<Flow> findByTagsAndNewState(FlowTagFilter filter, FlowState newState) {
        Map<String, Object> params = new HashMap<>();
        String whereClause = newState != null ?
                buildCriteria(filter, params) :
                buildCriteriaWithoutState(filter, params);

        String sql = "SELECT * from flows \n" + whereClause;

        return executeQuery(sql, params, newState);
    }

    public List<Flow> setFlowStateByTags(FlowTagFilter filter, FlowState newState) {
        Map<String, Object> params = new HashMap<>();
        String whereClause = buildCriteria(filter, params);

        String sql = String.format("""
        UPDATE flows
        SET flow_status = jsonb_set(flow_status, '{state}', to_jsonb(CAST(:newState AS text)))
        %s
        RETURNING *
        """, whereClause);

        return executeQuery(sql, params, newState);
    }

    private List<Flow> executeQuery(String sql, Map<String, Object> params, FlowState newState) {
        Query query = entityManager.createNativeQuery(sql, Flow.class);

        if (newState != null) {
            query.setParameter("newState", newState.name());
        }

        params.forEach(query::setParameter);

        @SuppressWarnings("unchecked")
        List<Flow> updatedFlows = query.getResultList();
        return updatedFlows;
    }

    private String buildCriteria(FlowTagFilter filter, Map<String, Object> params) {
        StringBuilder whereClause = new StringBuilder("WHERE flow_status->>'state' != :newState");
        whereClause.append(" AND CAST((flow_status->>'valid') AS boolean) = true");
        return buildCriteria(whereClause, filter, params);
    }

    private String buildCriteriaWithoutState(FlowTagFilter filter, Map<String, Object> params) {
        StringBuilder whereClause = new StringBuilder("WHERE 1 = 1");
        return buildCriteria(whereClause, filter, params);
    }

    private String buildCriteria(StringBuilder whereClause, FlowTagFilter filter, Map<String, Object> params) {
        if (!filter.all().isEmpty()) {
            whereClause.append(" AND tags @> CAST(:allTags AS jsonb)");
            params.put("allTags", toJson(filter.all()));
        }

        if (!filter.any().isEmpty()) {
            whereClause.append(" AND jsonb_exists_any(tags, CAST(:anyTags AS text[]))");
            params.put("anyTags", filter.any().toArray(new String[0]));
        }

        if (!filter.none().isEmpty()) {
            whereClause.append(" AND NOT jsonb_exists_any(tags, CAST(:noneTags AS text[]))");
            params.put("noneTags", filter.none().toArray(new String[0]));
        }

        if (!filter.types().isEmpty()) {
            whereClause.append(" AND type IN (:types)");
            params.put("types", filter.types().stream()
                    .map(Enum::name)
                    .toList());
        }
        return whereClause.toString();
    }

    private String toJson(Collection<String> list) {
        try {
            return list == null ? null : objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error converting object to JSON", e);
        }
    }
}
