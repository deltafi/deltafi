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
package org.deltafi.core.converters;

import org.deltafi.common.types.OnErrorDataSourcePlan;
import org.deltafi.core.types.OnErrorDataSource;

public class OnErrorDataSourcePlanConverter extends FlowPlanConverter<OnErrorDataSourcePlan, OnErrorDataSource> {

    @Override
    public OnErrorDataSource createFlow(OnErrorDataSourcePlan dataSourcePlan, FlowPlanPropertyHelper flowPlanPropertyHelper) {
        OnErrorDataSource onErrorDataSource = new OnErrorDataSource();
        onErrorDataSource.setTopic(dataSourcePlan.getTopic());
        onErrorDataSource.setMetadata(dataSourcePlan.getMetadata());
        onErrorDataSource.setAnnotationConfig(dataSourcePlan.getAnnotationConfig());
        onErrorDataSource.setErrorMessageRegex(dataSourcePlan.getErrorMessageRegex());
        onErrorDataSource.setSourceFilters(dataSourcePlan.getSourceFilters());
        onErrorDataSource.setMetadataFilters(dataSourcePlan.getMetadataFilters());
        onErrorDataSource.setAnnotationFilters(dataSourcePlan.getAnnotationFilters());
        onErrorDataSource.setIncludeSourceMetadataRegex(dataSourcePlan.getIncludeSourceMetadataRegex());
        onErrorDataSource.setSourceMetadataPrefix(dataSourcePlan.getSourceMetadataPrefix());
        onErrorDataSource.setIncludeSourceAnnotationsRegex(dataSourcePlan.getIncludeSourceAnnotationsRegex());
        return onErrorDataSource;
    }
}
