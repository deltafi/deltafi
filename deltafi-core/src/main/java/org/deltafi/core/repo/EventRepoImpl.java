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
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.core.types.Event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@Slf4j
public class EventRepoImpl implements EventRepoCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public Optional<Event> updateAcknowledged(UUID id, boolean acknowledged) {
        Event event = entityManager.find(Event.class, id);
        if (event != null) {
            event.setAcknowledged(acknowledged);
            entityManager.merge(event);
            return Optional.of(event);
        }
        return Optional.empty();
    }

    @Override
    public List<Event> findEvents(Map<String, String> filters) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Event> query = cb.createQuery(Event.class);
        Root<Event> root = query.from(Event.class);

        Predicate predicate = cb.conjunction();

        OffsetDateTime end = null;
        OffsetDateTime start = null;

        for (Map.Entry<String, String> entry : filters.entrySet()) {
            switch (entry.getKey()) {
                case "acknowledged", "notification" -> predicate = cb.and(predicate, cb.equal(root.get(entry.getKey()), Boolean.parseBoolean(entry.getValue())));
                case "id", "severity", "summary", "content", "source" -> predicate = cb.and(predicate, cb.equal(root.get(entry.getKey()), entry.getValue()));
                case "start" -> start = OffsetDateTime.parse(entry.getValue());
                case "end" -> end = OffsetDateTime.parse(entry.getValue());
                default -> log.warn("Unexpected filter {}: {}", entry.getKey(), entry.getValue());
            }
        }

        final OffsetDateTime endTime = end != null ? end : OffsetDateTime.now();
        start = start != null ? start : endTime.minusDays(1);

        predicate = cb.and(predicate, cb.greaterThan(root.get("timestamp"), start));
        predicate = cb.and(predicate, cb.lessThan(root.get("timestamp"), endTime));

        query.where(predicate);
        query.orderBy(cb.desc(root.get("timestamp")));

        return entityManager.createQuery(query).getResultList();
    }
}
