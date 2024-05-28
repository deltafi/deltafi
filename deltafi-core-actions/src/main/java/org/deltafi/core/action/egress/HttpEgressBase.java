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
package org.deltafi.core.action.egress;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.action.egress.EgressAction;
import org.deltafi.actionkit.action.egress.EgressInput;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.egress.EgressResultType;
import org.deltafi.actionkit.action.error.ErrorResult;
import org.deltafi.common.http.HttpPostException;
import org.deltafi.common.http.HttpService;
import org.deltafi.common.types.ActionContext;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class HttpEgressBase<P extends HttpEgressParameters> extends EgressAction<P> {
    protected HttpService httpService;

    public HttpEgressBase(String description, HttpService httpService) {
        super(description);
        this.httpService = httpService;
    }

    @SuppressWarnings("BusyWait")
    public EgressResultType egress(@NotNull ActionContext context, @NotNull P params, @NotNull EgressInput input) {
        int tries = 0;

        while (true) {
            EgressResultType result = doEgress(context, params, input);
            tries++;

            if (!(result instanceof ErrorResult) || (tries > params.getRetryCount())) {
                return result;
            }

            log.error("Retrying HTTP POST after error: {} (retry {}/{})",
                    ((ErrorResult) result).getErrorCause(), tries, params.getRetryCount());

            try {
                Thread.sleep(params.getRetryDelayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // in the future we might want this to requeue instead of error
                // but first we want to catch the error in the wild and identify if and why it is happening
                return new ErrorResult(context, "HTTP egress thread interrupted", e).logErrorTo(log);
            }
        }
    }

    private EgressResultType doEgress(@NotNull ActionContext context, @NotNull P params, @NotNull EgressInput input) {
        try {
            // The stream is automatically closed by HttpClient when the end of stream is reached.
            HttpResponse<InputStream> response = httpService.post(params.getUrl(), buildHeaders(context, params, input),
                    openInputStream(context, input), getMediaType(input));
            Response.Status status = Response.Status.fromStatusCode(response.statusCode());
            if (Objects.isNull(status) || status.getFamily() != Response.Status.Family.SUCCESSFUL) {
                try (InputStream body = response.body()) {
                    return new ErrorResult(context, "Unsuccessful HTTP POST: " + response.statusCode(),
                            new String(body.readAllBytes())).logErrorTo(log);
                } catch (IOException e) {
                    return new ErrorResult(context, "Unsuccessful HTTP POST: " + response.statusCode(),
                            "Unable to read response body: " + e.getMessage()).logErrorTo(log);
                }
            }
        } catch (JsonProcessingException e) {
            return new ErrorResult(context, "Unable to build post headers", e).logErrorTo(log);
        } catch (IOException e) {
            return new ErrorResult(context, "Unable to open input stream", e).logErrorTo(log);
        } catch (HttpPostException e) {
            return new ErrorResult(context, "Service post failure", e).logErrorTo(log);
        }

        return new EgressResult(context, params.getUrl(), input.getContent().getSize());
    }

    protected InputStream openInputStream(@NotNull ActionContext context, @NotNull EgressInput input)
            throws IOException {
        return input.getContent().loadInputStream();
    }

    protected String getMediaType(@NotNull EgressInput input) {
        return input.getContent().getMediaType();
    }

    protected Map<String, String> buildHeaders(@NotNull ActionContext context, @NotNull P params,
            @NotNull EgressInput input) throws JsonProcessingException {
        return Collections.emptyMap();
    }
}
