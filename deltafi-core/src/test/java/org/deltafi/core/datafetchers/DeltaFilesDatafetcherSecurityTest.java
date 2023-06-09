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
package org.deltafi.core.datafetchers;

import graphql.schema.DataFetchingEnvironmentImpl;
import org.assertj.core.api.Assertions;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.core.security.SecurityConfig;
import org.deltafi.core.services.DeltaFilesService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@SpringBootTest(classes = {DeltaFilesDatafetcher.class, SecurityConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DeltaFilesDatafetcherSecurityTest {

    @Autowired
    DeltaFilesDatafetcher deltaFilesDatafetcher;

    @MockBean
    DeltaFilesService deltaFilesService;

    @MockBean
    ContentStorageService contentStorageService;

    @Test
    @WithMockUser(username = "user", authorities = { "NO_MATCH" })
    void allMethodsSecured() {
        // the mock user does should be denied access
        allMethods().forEach(this::runDeniedTest);
    }

    @Test
    @WithMockUser(username = "user", authorities = { "Admin" })
    void superUserAccess() {
        // the Admin authority should never be denied access
        allMethods().forEach(this::runHasAccessTest);
    }

    void runDeniedTest(Callable<?> method) {
        Assertions.assertThatThrownBy(method::call)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Access Denied");
    }

    void runHasAccessTest(Callable<?> method) {
        try {
            method.call();
        } catch (AccessDeniedException e) {
            Assertions.fail("Auth failed", e);
        } catch (Exception exception) {
            // ignore other exceptions
        }
    }

    List<Callable<?>> allMethods() {
        List<Callable<?>> callables = new ArrayList<>();
        callables.add(() -> deltaFilesDatafetcher.deltaFile("did"));
        callables.add(() -> deltaFilesDatafetcher.deltaFiles(DataFetchingEnvironmentImpl.newDataFetchingEnvironment().build(), null, null));
        callables.add(() -> deltaFilesDatafetcher.domains());
        callables.add(() -> deltaFilesDatafetcher.rawDeltaFile("did", true));
        callables.add(() -> deltaFilesDatafetcher.acknowledge(List.of("did"), "reason"));
        callables.add(() -> deltaFilesDatafetcher.cancel(List.of("did")));
        callables.add(() -> deltaFilesDatafetcher.resume(List.of("did"), null, null));
        callables.add(() -> deltaFilesDatafetcher.replay(List.of("did"), null, null, null, null));
        callables.add(() -> deltaFilesDatafetcher.lastCreated(1));
        callables.add(() -> deltaFilesDatafetcher.lastModified(1));
        callables.add(() -> deltaFilesDatafetcher.lastErrored(1));
        callables.add(() -> deltaFilesDatafetcher.lastWithFilename(""));
        callables.add(() -> deltaFilesDatafetcher.errorSummaryByFlow(0, 0, null, null));
        callables.add(() -> deltaFilesDatafetcher.errorSummaryByMessage(0, 0, null, null));
        callables.add(() -> deltaFilesDatafetcher.annotationKeys(""));
        callables.add(() -> deltaFilesDatafetcher.stressTest(null, null, null, null, null));
        callables.add(() -> deltaFilesDatafetcher.sourceMetadataUnion(null));
        return callables;
    }
}