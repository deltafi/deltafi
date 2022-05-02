/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2022 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.domain.delete;

import org.deltafi.core.domain.configuration.DeleteActionConfiguration;
import org.deltafi.core.domain.generated.types.ActionFamily;

import java.util.List;

public class DeleteConstants {

    private DeleteConstants() {}

    public static final String DELETE_ACTION = "DeleteAction";
    public static final DeleteActionConfiguration DELETE_ACTION_CONFIGURATION = new DeleteActionConfiguration(DELETE_ACTION, "org.deltafi.core.action.delete.DeleteAction");
    public static final ActionFamily DELETE_FAMILY = ActionFamily.newBuilder().family("delete").actionNames(List.of(DELETE_ACTION)).build();
}