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

import lombok.extern.slf4j.Slf4j;
import org.apache.nifi.util.FlowFilePackagerV1;
import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.common.http.HttpPostException;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.types.ActionContext;
import org.deltafi.common.types.FormattedData;
import org.deltafi.common.types.SourceInfo;
import org.deltafi.core.parameters.HttpEgressParameters;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
public class FlowfileEgressAction extends HttpEgressActionBase<HttpEgressParameters> {
    static final String FLOWFILE_V1_CONTENT_TYPE = "application/flowfile";

    public FlowfileEgressAction() {
        super(HttpEgressParameters.class,
                "Egresses content and attributes in a NiFi V1 FlowFile (application/flowfile)");
    }

    protected Result doEgress(@NotNull ActionContext context, @NotNull HttpEgressParameters params, @NotNull SourceInfo sourceInfo, @NotNull FormattedData formattedData) {
        try (InputStream inputStream = loadContentAsInputStream(formattedData.getContentReference())) {
            FlowFilePackagerV1 packager = new FlowFilePackagerV1();
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                packager.packageFlowFile(inputStream, out,
                        buildHeadersMap(context.getDid(), sourceInfo, formattedData, context.getEgressFlow()),
                        formattedData.getContentReference().getSize());
                HttpResponse<InputStream> response;
                try (ByteArrayInputStream flowfile = new ByteArrayInputStream(out.toByteArray())) {
                    response = httpPostService.post(params.getUrl(), Map.of(), flowfile, FLOWFILE_V1_CONTENT_TYPE);
                } catch (IOException e) {
                    return new ErrorResult(context, "Unable to process flowfile stream", e);
                }
                Response.Status status = Response.Status.fromStatusCode(response.statusCode());
                if (Objects.isNull(status) || status.getFamily() != Response.Status.Family.SUCCESSFUL) {
                    return new ErrorResult(context, "Unsuccessful HTTP POST: " + response.statusCode() + " " + new String(response.body().readAllBytes())).logErrorTo(log);
                }
            } catch (IOException e) {
                return new ErrorResult(context, "Unable to extract flowfile content");
            }
        } catch (ObjectStorageException e) {
            return new ErrorResult(context, "Unable to get object from content storage", e);
        } catch (IOException e) {
            log.warn("Unable to close input stream from content storage", e);
        } catch (HttpPostException e) {
            return new ErrorResult(context, "Service post failure", e);
        }

        return new EgressResult(context, params.getUrl(), formattedData.getContentReference().getSize());
    }

}
