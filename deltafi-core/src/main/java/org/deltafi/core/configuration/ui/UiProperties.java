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
package org.deltafi.core.configuration.ui;


import lombok.Data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Data
public class UiProperties {
    private boolean useUTC = true;
    private TopBar topBar = new TopBar();
    private SecurityBanner securityBanner = new SecurityBanner();
    private List<Link> deltaFileLinks = new ArrayList<>();
    private List<Link> externalLinks = new ArrayList<>();

    public void mergeLinkLists(UiProperties source) {
        setExternalLinks(combineLinkList(this.externalLinks, source.externalLinks));
        setDeltaFileLinks(combineLinkList(this.deltaFileLinks, source.deltaFileLinks));
    }

    private List<Link> combineLinkList(List<Link> target, List<Link> source) {
        if (target == null) {
            if (source == null) {
                return null;
            } else {
                return List.copyOf(source);
            }
        } else if (source == null) {
            return List.copyOf(target);
        }

        // Remove any overlapping links from the target prior to copying the source links into the target list
        List<Link> result = new ArrayList<>();
        for (Link sourceLink : source) {
            Iterator<Link> targetLinkIt = target.iterator();
            while(targetLinkIt.hasNext()) {
                Link targetLink = targetLinkIt.next();
                if (targetLink.nameMatches(sourceLink)) {
                    targetLinkIt.remove();
                    break;
                }
            }
        }
        result.addAll(target);
        result.addAll(source);

        return result;
    }
}