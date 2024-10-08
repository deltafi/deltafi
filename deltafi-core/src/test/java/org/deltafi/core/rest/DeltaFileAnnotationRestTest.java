/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.rest;

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException;
import org.deltafi.core.audit.CoreAuditLogger;
import org.deltafi.core.services.DeltaFilesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DeltaFileAnnotationRestTest {

    private static final UUID DID = UUID.randomUUID();
    private static final Map<String, String> METADATA = Map.of("k1", "v1", "k2", "v2");

    private MockMvc mockMvc;

    @InjectMocks
    private DeltaFileAnnotationRest deltaFileAnnotationRest;
    @Mock
    private DeltaFilesService deltaFilesService;
    @Mock
    private CoreAuditLogger auditLogger;

    @BeforeEach
    public void before() {
        mockMvc = MockMvcBuilders.standaloneSetup(deltaFileAnnotationRest).build();
    }

    @Test
    void annotateDeltaFile_noOverwrites() throws Exception {
        this.mockMvc.perform(post("/api/v2/deltafile/annotate/%s?k1=v1&k2=v2".formatted(DID)))
                .andExpect(status().isOk())
                .andExpect(content().string(is("Success")));

        Mockito.verify(deltaFilesService).addAnnotations(DID, METADATA, false);
    }

    @Test
    void annotateDeltaFile_allowOverwrites() throws Exception {
        this.mockMvc.perform(post("/api/v2/deltafile/annotate/%s/allowOverwrites?k1=v1&k2=v2".formatted(DID)))
                .andExpect(status().isOk())
                .andExpect(content().string(is("Success")));

        Mockito.verify(deltaFilesService).addAnnotations(DID, METADATA, true);
    }

    @Test
    void annotateDeltaFile_missingDid() throws Exception {
        Mockito.doThrow(new DgsEntityNotFoundException("Missing did"))
                .when(deltaFilesService).addAnnotations(DID, METADATA, false);

        this.mockMvc.perform(post("/api/v2/deltafile/annotate/%s?k1=v1&k2=v2".formatted(DID)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(is("Missing did")));
    }

}