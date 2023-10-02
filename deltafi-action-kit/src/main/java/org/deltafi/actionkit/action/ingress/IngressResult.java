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
package org.deltafi.actionkit.action.ingress;

import lombok.Getter;
import lombok.Setter;
import org.deltafi.actionkit.action.ContentResult;
import org.deltafi.actionkit.action.converters.ContentConverter;
import org.deltafi.common.types.*;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Specialized result class for INGRESS actions
 */
@Getter
@Setter
public class IngressResult extends ContentResult<IngressResult> implements IngressResultType {
    String filename;
    public IngressResult(@NotNull ActionContext context, @NotNull String filename) {
        super(context.copy(UUID.randomUUID().toString()), ActionEventType.INGRESS);
        this.filename = filename;
    }

    public IngressResult(@NotNull ActionContext context, @NotNull String did, @NotNull String filename) {
        super(context.copy(did), ActionEventType.INGRESS);
        this.filename = filename;
    }

    @Override
    public final ActionEvent toEvent() {
        ActionEvent event = super.toEvent();
        event.setIngress(IngressEvent.builder()
                .did(context.getDid())
                .content(ContentConverter.convert(content))
                .sourceInfo(SourceInfo.builder().metadata(metadata).filename(filename).build())
                .created(OffsetDateTime.now())
                .build());

        return event;
    }
}
