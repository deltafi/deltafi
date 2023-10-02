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
package org.deltafi.actionkit.action.load;

import lombok.Getter;
import org.deltafi.actionkit.action.ContentResult;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.converters.ContentConverter;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Specialized result class for LOAD actions
 */
@Getter
public class LoadResult extends ContentResult<LoadResult> implements LoadResultType {
    private final List<Domain> domains = new ArrayList<>();

    public LoadResult(@NotNull ActionContext context) {
        super(context, ActionEventType.LOAD);
    }

    /**
     * @param context Context of executing action
     * @param content List of content objects to be processed with the execution result
     */
    public LoadResult(@NotNull ActionContext context, @NotNull List<ActionContent> content) {
        super(context, ActionEventType.LOAD, content);
    }

    /**
     * Add a domain to the result.  This method can be invoked multiple times to add additional domains.
     * @param domainName Key/name of the domain that is being added
     * @param value Value of the domain that is being added
     * @param mediaType The media type of the added domain
     */
    public void addDomain(@NotNull String domainName, String value, @NotNull String mediaType) {
        domains.add(new Domain(domainName, value, mediaType));
    }

    @Override
    public final ActionEvent toEvent() {
        ActionEvent event = super.toEvent();
        event.setLoad(LoadEvent.builder()
                .domains(domains)
                .content(ContentConverter.convert(content))
                .annotations(annotations)
                .metadata(metadata)
                .deleteMetadataKeys(deleteMetadataKeys)
                .build());

        return event;
    }
}
