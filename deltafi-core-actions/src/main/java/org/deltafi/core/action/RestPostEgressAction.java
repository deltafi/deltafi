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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.annotation.Action;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.actionkit.exception.HttpPostException;
import org.deltafi.actionkit.service.HttpService;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.FormattedData;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.core.parameters.RestPostEgressParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Action
public class RestPostEgressAction extends HttpEgressActionBase<RestPostEgressParameters> {
    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Autowired
    HttpService httpPostService;

    public RestPostEgressAction() {
        super(RestPostEgressParameters.class);
    }

    protected Result doEgress(@NotNull ActionContext context, @NotNull RestPostEgressParameters params, @NotNull SourceInfo sourceInfo, @NotNull FormattedData formattedData) {
        try (InputStream inputStream = loadContentAsInputStream(formattedData.getContentReference())) {
            HttpResponse<InputStream> response = httpPostService.post(params.getUrl(), Map.of(params.getMetadataKey(),
                    buildHeadersMapString(context.getDid(), sourceInfo, formattedData, params)), inputStream, formattedData.getContentReference().getMediaType());
            Response.Status status = Response.Status.fromStatusCode(response.statusCode());
            if (Objects.isNull(status) || status.getFamily() != Response.Status.Family.SUCCESSFUL) {
                return new ErrorResult(context, "Unsuccessful POST: " + response.statusCode() + " " + new String(response.body().readAllBytes())).logErrorTo(log);
            }

        } catch (JsonProcessingException e) {
            return new ErrorResult(context, "Unable to build post headers", e);
        } catch (ObjectStorageException e) {
            return new ErrorResult(context, "Unable to get object from content storage", e);
        } catch (IOException e) {
            log.warn("Unable to close input stream from content storage", e);
        } catch (HttpPostException e) {
            return new ErrorResult(context, "Service post failure", e);
        }

        return new EgressResult(context, params.getUrl(), formattedData.getContentReference().getSize());
    }

    private String buildHeadersMapString(String did, SourceInfo sourceInfo, FormattedData formattedData, RestPostEgressParameters params)
            throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(buildHeadersMap(did, sourceInfo, formattedData, params));
    }
}
