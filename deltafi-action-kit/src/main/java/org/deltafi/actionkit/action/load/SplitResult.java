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
package org.deltafi.actionkit.action.load;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.api.types.*;
import org.deltafi.core.domain.api.converters.KeyValueConverter;
import org.deltafi.core.domain.generated.types.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@EqualsAndHashCode(callSuper = true)
public class SplitResult extends Result {
    List<SplitInput> splitInputs = new ArrayList<>();

    public SplitResult(ActionContext context) {
        super(context);
    }

    public void addChild(String filename, String flow, List<KeyValue> metadata, List<Content> content) {
        splitInputs.add(SplitInput.newBuilder()
                .sourceInfo(new SourceInfo(filename, flow, metadata))
                .content(content)
                .build());
    }

    @SuppressWarnings("unused")
    public void addChild(String filename, String flow, Map<String, String> metadata, List<Content> content) {
        addChild(filename, flow, KeyValueConverter.fromMap(metadata), content);
    }

    @Override
    public final ActionEventType actionEventType() {
        return ActionEventType.SPLIT;
    }

    @Override
    public final ActionEventInput toEvent() {
        ActionEventInput event = super.toEvent();
        event.setSplit(splitInputs);
        return event;
    }
}
