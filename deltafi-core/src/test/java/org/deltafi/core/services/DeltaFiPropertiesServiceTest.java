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
package org.deltafi.core.services;

import org.deltafi.core.configuration.DeltaFiProperties;
import org.deltafi.core.configuration.ui.Link;
import org.deltafi.core.configuration.ui.SecurityBanner;
import org.deltafi.core.configuration.ui.TopBar;
import org.deltafi.core.configuration.ui.UiProperties;
import org.deltafi.core.repo.DeltaFiPropertiesRepo;
import org.deltafi.core.types.PropertyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DeltaFiPropertiesServiceTest {

    @InjectMocks
    DeltaFiPropertiesService deltaFiPropertiesService;

    @Mock
    DeltaFiPropertiesRepo deltaFiPropertiesRepo;

    @Captor
    ArgumentCaptor<DeltaFiProperties> propsCaptor;

    @BeforeEach
    public void clearRepo() {
        Mockito.reset(deltaFiPropertiesRepo);
    }

    @Test
    void testSaveExternalLink() {
        DeltaFiProperties deltaFiProperties = deltaFiProperties();

        deltaFiProperties.getUi().getExternalLinks().add(link("a", "a.com", "a"));
        deltaFiProperties.getUi().getExternalLinks().add(link("b", "b.com", "b"));

        Mockito.when(deltaFiPropertiesRepo.findById(DeltaFiProperties.PROPERTY_ID)).thenReturn(Optional.of(deltaFiProperties));

        Link replacement = link("a", "replace.com", "changed");
        deltaFiPropertiesService.saveExternalLink(replacement);

        Mockito.verify(deltaFiPropertiesRepo).save(propsCaptor.capture());
        DeltaFiProperties updated = propsCaptor.getValue();
        assertThat(updated.getUi().getExternalLinks()).hasSize(2).contains(replacement);
    }

    @Test
    void testSaveDeltaFileLink() {
        DeltaFiProperties deltaFiProperties = deltaFiProperties();

        deltaFiProperties.getUi().getDeltaFileLinks().add(link("a", "a.com", "a"));
        deltaFiProperties.getUi().getDeltaFileLinks().add(link("b", "b.com", "b"));

        Mockito.when(deltaFiPropertiesRepo.findById(DeltaFiProperties.PROPERTY_ID)).thenReturn(Optional.of(deltaFiProperties));

        Link newLink = link("c", "c", "new link");
        deltaFiPropertiesService.saveDeltaFileLink(newLink);

        Mockito.verify(deltaFiPropertiesRepo).save(propsCaptor.capture());
        DeltaFiProperties updated = propsCaptor.getValue();
        assertThat(updated.getUi().getDeltaFileLinks()).hasSize(3).contains(newLink);
    }

    @Test
    void testReplaceDeltaFileLink() {
        DeltaFiProperties deltaFiProperties = deltaFiProperties();

        Link originalLink = link("a", "a.com", "a");
        deltaFiProperties.getUi().getDeltaFileLinks().add(originalLink);
        deltaFiProperties.getUi().getDeltaFileLinks().add(link("b", "b.com", "b"));

        Mockito.when(deltaFiPropertiesRepo.findById(DeltaFiProperties.PROPERTY_ID)).thenReturn(Optional.of(deltaFiProperties));

        Link newLink = link("c", "c", "new link");
        deltaFiPropertiesService.replaceDeltaFileLink("a", newLink);

        Mockito.verify(deltaFiPropertiesRepo).save(propsCaptor.capture());
        DeltaFiProperties updated = propsCaptor.getValue();
        assertThat(updated.getUi().getDeltaFileLinks()).hasSize(2).contains(newLink).doesNotContain(originalLink);
    }

    @Test
    void testReplaceExternalLink() {
        DeltaFiProperties deltaFiProperties = deltaFiProperties();

        Link originalLink = link("a", "a.com", "a");
        deltaFiProperties.getUi().getExternalLinks().add(originalLink);
        deltaFiProperties.getUi().getExternalLinks().add(link("b", "b.com", "b"));

        Mockito.when(deltaFiPropertiesRepo.findById(DeltaFiProperties.PROPERTY_ID)).thenReturn(Optional.of(deltaFiProperties));

        Link newLink = link("c", "c", "new link");
        deltaFiPropertiesService.replaceExternalLink("a", newLink);

        Mockito.verify(deltaFiPropertiesRepo).save(propsCaptor.capture());
        DeltaFiProperties updated = propsCaptor.getValue();
        assertThat(updated.getUi().getExternalLinks()).hasSize(2).contains(newLink).doesNotContain(originalLink);
    }

    @Test
    void testMergeProperties() {
        Set<String> setInBoth = Stream.of(PropertyType.REQUEUE_SECONDS, PropertyType.DELETE_AGE_OFF_DAYS).map(Enum::name).collect(Collectors.toSet());

        Link targetCommon = link("both", "target.both.com", "target both");
        Link targetOnly = link("target", "target.com", "target only");

        Set<String> setTargetProps = Stream.of(PropertyType.SYSTEM_NAME, PropertyType.UI_SECURITY_BANNER_ENABLED, PropertyType.UI_SECURITY_BANNER_BACKGROUND_COLOR, PropertyType.UI_SECURITY_BANNER_TEXT, PropertyType.UI_SECURITY_BANNER_TEXT_COLOR)
                .map(Enum::name).collect(Collectors.toSet());
        DeltaFiProperties targetProperties = new DeltaFiProperties();
        targetProperties.setRequeueSeconds(1);
        targetProperties.getDelete().setAgeOffDays(1);
        targetProperties.setSystemName("target");
        targetProperties.getUi().setSecurityBanner(securityBanner());
        targetProperties.getSetProperties().addAll(setInBoth);
        targetProperties.getSetProperties().addAll(setTargetProps);
        targetProperties.getUi().getExternalLinks().addAll(List.of(targetCommon, targetOnly));
        targetProperties.getUi().getDeltaFileLinks().addAll(List.of(targetCommon, targetOnly));
        targetProperties.setInMemoryQueueSize(10);
        Mockito.when(deltaFiPropertiesRepo.findById(DeltaFiProperties.PROPERTY_ID)).thenReturn(Optional.of(targetProperties));

        Link sourceCommon = link("both", "source.both.com", "source both");
        Link sourceOnly = link("source", "source.com", "source only");
        DeltaFiProperties snapshotSource = new DeltaFiProperties();
        Set<String> setSourceProps = Stream.of(PropertyType.UI_USE_UTC, PropertyType.UI_TOP_BAR_TEXT_COLOR, PropertyType.UI_TOP_BAR_BACKGROUND_COLOR).map(Enum::name).collect(Collectors.toSet());
        snapshotSource.setRequeueSeconds(2);
        snapshotSource.getDelete().setAgeOffDays(2);
        snapshotSource.getUi().setUseUTC(false);
        snapshotSource.getUi().setTopBar(topBar());
        snapshotSource.getUi().getExternalLinks().addAll(List.of(sourceCommon, sourceOnly));
        snapshotSource.getUi().getDeltaFileLinks().addAll(List.of(sourceCommon, sourceOnly));
        snapshotSource.getSetProperties().addAll(setInBoth);
        snapshotSource.getSetProperties().addAll(setSourceProps);
        snapshotSource.setInMemoryQueueSize(50);

        DeltaFiProperties updated = deltaFiPropertiesService.mergeProperties(snapshotSource);

        assertThat(updated.getSetProperties()).hasSize(10).containsAll(setInBoth).containsAll(setSourceProps).containsAll(setTargetProps);

        // verify fields in the snapshot are applied over top of the target settings
        assertThat(updated.getRequeueSeconds()).isEqualTo(snapshotSource.getRequeueSeconds());
        assertThat(updated.getDelete().getAgeOffDays()).isEqualTo(snapshotSource.getDelete().getAgeOffDays());
        assertThat(updated.getUi().isUseUTC()).isEqualTo(snapshotSource.getUi().isUseUTC());
        assertThat(updated.getUi().getTopBar()).isEqualTo(snapshotSource.getUi().getTopBar());

        // verify fields that were set in target properties only are left unchanged
        assertThat(updated.getUi().getSecurityBanner()).isEqualTo(targetProperties.getUi().getSecurityBanner());
        assertThat(updated.getSystemName()).isEqualTo(targetProperties.getSystemName());

        assertThat(updated.getUi().getExternalLinks()).hasSize(3).contains(targetOnly, sourceCommon, sourceOnly);
        assertThat(updated.getUi().getDeltaFileLinks()).hasSize(3).contains(targetOnly, sourceCommon, sourceOnly);
        assertThat(updated.getInMemoryQueueSize()).isEqualTo(10);
    }

    DeltaFiProperties deltaFiProperties() {
        DeltaFiProperties props = new DeltaFiProperties();
        UiProperties uiProperties = new UiProperties();
        uiProperties.setTopBar(topBar());
        uiProperties.setSecurityBanner(securityBanner());
        props.setUi(uiProperties);
        return props;
    }

    TopBar topBar() {
        TopBar topBar = new TopBar();
        topBar.setTextColor("orange");
        topBar.setBackgroundColor("green");
        return topBar;
    }

    SecurityBanner securityBanner() {
        SecurityBanner securityBanner = new SecurityBanner();
        securityBanner.setTextColor("orange");
        securityBanner.setBackgroundColor("green");
        securityBanner.setText("Banner");
        securityBanner.setEnabled(true);
        return securityBanner;
    }
    
    Link link(String name, String url, String description) {
        Link link = new Link();
        link.setName(name);
        link.setUrl(url);
        link.setDescription(description);
        return link;
    }
}