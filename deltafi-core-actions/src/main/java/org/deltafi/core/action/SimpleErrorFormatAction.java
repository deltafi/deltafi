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
package org.deltafi.core.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.format.FormatResult;
import org.deltafi.actionkit.action.format.SimpleFormatAction;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.common.types.Content;
import org.deltafi.common.types.Domain;
import org.deltafi.common.types.Enrichment;
import org.deltafi.common.types.ErrorDomain;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.core.MediaType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.deltafi.common.constant.DeltaFiConstants.ERROR_DOMAIN;

@Slf4j
public class SimpleErrorFormatAction extends SimpleFormatAction {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(MapperFeature.DEFAULT_VIEW_INCLUSION, true)
            .registerModule(new JavaTimeModule());

    @Override
    public List<String> getRequiresDomains() {
        return List.of(ERROR_DOMAIN);
    }

    @Override
    public Result format(@NotNull ActionContext context,
                         @NotNull SourceInfo sourceInfo,
                         @NotNull Content content,
                         @NotNull Map<String, String> metadata,
                         @NotNull Map<String, Domain> domains,
                         @NotNull Map<String, Enrichment> enrichment) {
        String json = domains.get(ERROR_DOMAIN).getValue();
        String filename;
        try {
            ErrorDomain errorDomain = OBJECT_MAPPER.readValue(json, ErrorDomain.class);

            filename = errorDomain.getOriginatorDid() + "." + sourceInfo.getFilename() + ".error";
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting JSON to ErrorDomain.\n" + json, e);
        } catch (Throwable t) {
            String err = "DeltaFile: Could not determine source file name for " + context.getDid();
            log.error(err, t);
            throw new RuntimeException(err, t);
        }

        try {
            FormatResult result = new FormatResult(context, filename);
            result.setContentReference(saveContent(context.getDid(), json.getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON));
            result.addMetadata(sourceInfo.getMetadata(), "sourceInfo.");

            return result;
        } catch (ObjectStorageException e) {
            throw new RuntimeException("Failed to write transformed data to content storage " + e.getMessage());
        }
    }
}
