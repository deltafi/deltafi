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
package org.deltafi.core.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.deltafi.common.content.Segment;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.core.exceptions.EntityNotFound;
import org.deltafi.core.services.FetchContentService;
import org.deltafi.core.types.ContentRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.deltafi.core.rest.ContentRest.BASE64_DECODE_ERROR;
import static org.deltafi.core.rest.ContentRest.JSON_PARSE_ERROR;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(ContentRest.class)
@AutoConfigureMockMvc(addFilters = false)
class ContentRestTest {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final String DATA = "data in content storage";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FetchContentService fetchContentService;

    @Test
    void testGet() throws Exception {
        ContentRequest contentRequest = contentRequestObj();
        Mockito.when(fetchContentService.streamContent(contentRequest)).thenReturn(new ByteArrayInputStream(DATA.getBytes()));

        String encodedJson = Base64.getEncoder().encodeToString(json(contentRequest).getBytes());
        MvcResult mvcResult = mockMvc.perform(get("/api/v2/content?content=" + encodedJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN_VALUE))
                .andReturn();

        String result = new String(mvcResult.getResponse().getContentAsByteArray());
        assertThat(result).isEqualTo(DATA);
    }

    @Test
    void testPost() throws Exception {
        ContentRequest contentRequest = contentRequestObj();
        Mockito.when(fetchContentService.streamContent(contentRequest)).thenReturn(new ByteArrayInputStream(DATA.getBytes()));

        MvcResult mvcResult = mockMvc.perform(post("/api/v2/content").content(json(contentRequest)).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN_VALUE))
                .andReturn();

        String result = new String(mvcResult.getResponse().getContentAsByteArray());
        assertThat(result).isEqualTo(DATA);
    }

    @Test
    void testNotFound() throws Exception {
        String error = "Couldn't find it";
        ContentRequest contentRequest = contentRequestObj();
        Mockito.when(fetchContentService.streamContent(contentRequest)).thenThrow(new EntityNotFound(error));

        String encodedJson = Base64.getEncoder().encodeToString(json(contentRequest).getBytes());
        mockMvc.perform(get("/api/v2/content?content=" + encodedJson))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void testObjectStorageError() throws Exception {
        String error = "Couldn't get it";
        ContentRequest contentRequest = contentRequestObj();
        Mockito.when(fetchContentService.streamContent(contentRequest)).thenThrow(new ObjectStorageException(error));

        String encodedJson = Base64.getEncoder().encodeToString(json(contentRequest).getBytes());
        mockMvc.perform(get("/api/v2/content?content=" + encodedJson))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void badInput() throws Exception {
        ContentRequest contentRequest = contentRequestObj();

        String encodedJson = Base64.getEncoder().encodeToString(json(contentRequest).getBytes());
        mockMvc.perform(get("/api/v2/content?content=" + encodedJson + "some junk"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(BASE64_DECODE_ERROR))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        encodedJson = Base64.getEncoder().encodeToString("{badJson: }".getBytes());
        mockMvc.perform(get("/api/v2/content?content=" + encodedJson))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value(JSON_PARSE_ERROR))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    private String json(ContentRequest contentRequest) throws JsonProcessingException {
        return MAPPER.writeValueAsString(contentRequest);
    }

    private ContentRequest contentRequestObj() {
        Segment segment = new Segment();
        segment.setDid(UUID.randomUUID());
        segment.setOffset(0);
        segment.setSize(DATA.length());

        return ContentRequest.builder()
                .name("content.txt")
                .mediaType(MediaType.TEXT_PLAIN_VALUE)
                .size(segment.getSize())
                .segments(List.of(segment))
                .build();
    }

}