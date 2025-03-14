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
package org.deltafi.actionkit.action.converters;

import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.types.Content;

import java.util.List;

public class ContentConverter {
    public static List<ActionContent> convert(List<Content> contentList, ContentStorageService contentStorageService) {
        return contentList.stream().map(content -> new ActionContent(content, contentStorageService)).toList();
    }

    public static List<Content> convert(List<ActionContent> actionContentList) {
        return actionContentList.stream().map(ContentConverter::convert).toList();
    }

    public static Content convert(ActionContent actionContent) {
        return new org.deltafi.common.types.Content(actionContent.getContent().getName(),
                actionContent.getMediaType(), actionContent.getContent().getSegments(),
                actionContent.getTags());
    }
}
