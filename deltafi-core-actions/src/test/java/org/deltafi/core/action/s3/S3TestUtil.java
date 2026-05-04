/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.action.s3;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;

public class S3TestUtil {
    static S3ClientParams createS3ClientParams() throws URISyntaxException {
        S3ClientParams s3ClientParams = new S3ClientParams();
        s3ClientParams.setEnableSSL(false);
        s3ClientParams.setRegion(Region.US_EAST_1.toString());
        s3ClientParams.setAccessKey("test");
        s3ClientParams.setSecretKey("test");
        s3ClientParams.setEndpoint(new URI("http://test-host:12345").toString());
        return s3ClientParams;
    }

    static ResponseInputStream<GetObjectResponse> buildResponseInputStream(String content) {
        return new ResponseInputStream<>(GetObjectResponse.builder().contentType(MediaType.TEXT_PLAIN)
                .contentLength((long) content.length()).build(), new ByteArrayInputStream(content.getBytes()));
    }
}
