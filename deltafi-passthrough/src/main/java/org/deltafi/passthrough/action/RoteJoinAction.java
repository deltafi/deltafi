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

import org.deltafi.actionkit.action.join.JoinAction;
import org.deltafi.actionkit.action.join.JoinResult;
import org.deltafi.common.types.*;
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
    protected JoinResult join(DeltaFileMessage deltaFile, List<DeltaFileMessage> joinedFromDeltaFiles, ActionContext context,
                              RoteJoinParameters params) {
        RandSleeper.sleep(params.getMinRoteDelayMS(), params.getMaxRoteDelayMS());

        SourceInfo sourceInfo = deltaFile.buildSourceInfo(context);
        if (params.getReinjectFlow() != null) {
            sourceInfo.setFlow(params.getReinjectFlow());
        }

        List<Content> contentList = joinedFromDeltaFiles.stream()
                .flatMap(joinedFromDeltaFile -> joinedFromDeltaFile.getContentList().stream())
                .collect(Collectors.toList());

        JoinResult joinResult = new JoinResult(context, sourceInfo, contentList);
        if (params.getDomains() != null) {
            params.getDomains().forEach(domain -> joinResult.addDomain(domain, null, MediaType.TEXT_PLAIN));
        }
        return joinResult;
    }
}
