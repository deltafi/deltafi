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
package org.deltafi.core.configuration.server.constants;

import java.util.List;

public class PropertyConstants {

    private PropertyConstants() {}

    public static final String CORE_APP_NAME = "deltafi-core";
    public static final String PROFILE = "default";
    public static final String DEFAULT_LABEL = "main";
    public static final String DELTAFI_PROPERTY_SET = "deltafi-common";
    public static final String ACTION_KIT_PROPERTY_SET = "action-kit";
    public static final String APPLICATION = "application";
    public static final String DEFAULT_PROPERTY_SETS = DELTAFI_PROPERTY_SET + "," + ACTION_KIT_PROPERTY_SET;
    public static final List<String> DEFAULT_APPLICATIONS = List.of(PropertyConstants.DELTAFI_PROPERTY_SET, PropertyConstants.ACTION_KIT_PROPERTY_SET);

}
