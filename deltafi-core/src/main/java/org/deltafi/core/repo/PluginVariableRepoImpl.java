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
package org.deltafi.core.repo;

import lombok.RequiredArgsConstructor;
import org.deltafi.core.types.PluginVariables;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@RequiredArgsConstructor
public class PluginVariableRepoImpl implements PluginVariableRepoCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public void resetAllUnmaskedVariableValues() {
        Update unsetValues = new Update();
        unsetValues.unset("variables.$[variable].value");
        unsetValues.filterArray(Criteria.where("variable.masked").ne(true));

        mongoTemplate.updateMulti(new Query(), unsetValues, PluginVariables.class);
    }
}
