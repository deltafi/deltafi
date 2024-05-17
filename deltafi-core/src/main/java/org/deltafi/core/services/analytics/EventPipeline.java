/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.services.analytics;

import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
class EventPipeline<T extends AnalyticEvent> {
    final ConcurrentLinkedQueue<T> queue = new ConcurrentLinkedQueue<>();
    final ArrayList<T> pending = new ArrayList<>();
    final String tableName;
    final String insertSql;
    final StatementFunction schemaFunction;

    EventPipeline(String tableName, String insertSql, StatementFunction schemaFunction) {
        this.tableName = tableName;
        this.insertSql = insertSql;
        this.schemaFunction = schemaFunction;
    }

    /**
     * Adds an event to the queue.
     *
     * @param  event  the event to be added to the queue
     */
    public void add(T event) {
        queue.add(event);
    }

    /**
     * Checks if the queue is empty.
     *
     * @return         true if the queue is not empty, false otherwise
     */
    public boolean pending() {
        return !pending.isEmpty();
    }

    public void clearPending() {
        pending.clear();
    }

    public void addSchema(Statement statement) throws SQLException {
        schemaFunction.apply(statement);
    }
}
