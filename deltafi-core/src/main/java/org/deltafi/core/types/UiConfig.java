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
package org.deltafi.core.types;

import lombok.Data;
import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.configuration.ui.Link;
import org.deltafi.core.configuration.ui.Link.LinkType;

import java.util.ArrayList;
import java.util.List;

@Data
public class UiConfig {
    private String title;
    private String domain;
    private String authMode;
    private boolean clusterMode;
    private boolean useUTC = true;
    private TopBar topBar;
    private SecurityBanner securityBanner;
    private List<Link> deltaFileLinks = new ArrayList<>();
    private List<Link> externalLinks = new ArrayList<>();

    public void setProperties(DeltaFiProperties properties) {
        this.setTitle(properties.getSystemName());
        this.topBar = new TopBar(properties);
        this.securityBanner = new SecurityBanner(properties);
    }

    public void setLinks(List<Link> links) {
        if (links == null) {
            return;
        }

        for (Link link : links) {
            if (link.getLinkType() == LinkType.DELTAFILE_LINK) {
                this.deltaFileLinks.add(link);
            } else if (link.getLinkType() == LinkType.EXTERNAL) {
                this.externalLinks.add(link);
            }
        }
    }

    public record TopBar(String backgroundColor, String textColor) {
        public TopBar(DeltaFiProperties deltaFiProperties) {
            this(deltaFiProperties.getTopBarBackgroundColor(), deltaFiProperties.getTopBarTextColor());
        }
    }

    public record SecurityBanner(String text, String backgroundColor, String textColor, boolean enabled) {
        public SecurityBanner(DeltaFiProperties deltaFiProperties) {
            this(deltaFiProperties.getSecurityBannerText(), deltaFiProperties.getSecurityBannerBackgroundColor(),
                    deltaFiProperties.getSecurityBannerTextColor(), deltaFiProperties.isSecurityBannerEnabled());
        }
    }
}
