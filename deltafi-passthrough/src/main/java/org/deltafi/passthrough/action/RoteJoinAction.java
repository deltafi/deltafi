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
package org.deltafi.passthrough.action;

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.join.*;
import org.deltafi.common.types.ActionContext;
import org.deltafi.passthrough.param.RoteJoinParameters;
import org.deltafi.passthrough.util.RandSleeper;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RoteJoinAction extends JoinAction<RoteJoinParameters> {
    public RoteJoinAction() {
        super("Merges multiple files");
    }

    @Override
    protected JoinResultType join(ActionContext context, RoteJoinParameters params, List<JoinInput> joinInputs) {
        RandSleeper.sleep(params.getMinRoteDelayMS(), params.getMaxRoteDelayMS());

        List<ActionContent> contentList = joinInputs.stream()
                .flatMap(joinedFromDeltaFile -> joinedFromDeltaFile.getContentList().stream())
                .collect(Collectors.toList());

        if (params.getReinjectFlow() != null) {
            return new JoinReinjectResult(context, params.getReinjectFlow(), contentList);
        } else {
            JoinResult joinResult = new JoinResult(context, contentList);
            if (params.getDomains() != null) {
                params.getDomains().forEach(domain -> joinResult.addDomain(domain, null, MediaType.TEXT_PLAIN));
            }
            return joinResult;
        }
    }
}
