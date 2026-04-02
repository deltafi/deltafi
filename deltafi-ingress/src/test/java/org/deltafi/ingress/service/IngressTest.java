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
package org.deltafi.ingress.service;

import org.assertj.core.api.Assertions;
import org.deltafi.common.content.ContentStorageService;
import org.deltafi.common.content.Segment;
import org.deltafi.common.storage.s3.ObjectStorageException;
import org.deltafi.common.test.content.InMemoryContentStorageService;
import org.deltafi.common.types.Content;
import org.deltafi.ingress.exception.IngressException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Collectors;

@ExtendWith(MockitoExtension.class)
class IngressTest {

    private final ContentStorageService contentStorageService;
    private final IngressRequestor ingressRequestor;
    private final DeltaFiIngressService ingressService;

    public IngressTest() {
        this.ingressService = Mockito.mock(DeltaFiIngressService.class);
        ContentStorageService realInstance = new InMemoryContentStorageService();
        contentStorageService = Mockito.spy(realInstance);
        this.ingressRequestor = new IngressRequestor(ingressService, contentStorageService);
    }

    @Test
    void testIngressByRef() throws IngressException, ObjectStorageException {
        IngressRequest.ByRef byRef = ingressRequestor.byReference()
                .deltaFileName("my-name")
                .addMetadata("abc", "efg")
                .saveContent("some content", "my-filename", "plain/text")
                .saveContent("some-other-content", "other-filename", "plain/text");

        Assertions.assertThat(byRef.did()).isNotNull();
        Set<UUID> uuids = byRef.content().stream()
                .map(Content::getSegments)
                .flatMap(Collection::stream)
                .map(Segment::getDid).collect(Collectors.toSet());

        Assertions.assertThat(uuids).hasSize(1).contains(byRef.did());

        byRef.execute();
        Mockito.verify(ingressService).ingress(byRef);
    }

    @Test
    void testIngressByRef_cleanup() throws IngressException, ObjectStorageException {
        IngressRequest.ByRef byRef = ingressRequestor.byReference()
                .deltaFileName("my-name")
                .addMetadata("abc", "efg")
                .saveContent("some content", "my-filename", "plain/text")
                .saveContent("some-other-content", "other-filename", "plain/text");

        Mockito.doThrow(new IngressException("failed to ingress"))
                .when(ingressService).ingress(byRef);

        Assertions.assertThatThrownBy(byRef::execute)
                .isInstanceOf(IngressException.class)
                .hasMessage("failed to ingress");

        for (Content content : byRef.content()) {
            Mockito.verify(contentStorageService).delete(content);
        }
    }

    @Test
    void testIngressValue() throws IngressException {
        IngressRequest.ByValue byValue = ingressRequestor.byValue()
                .deltaFileName("my-delta-file")
                .bytes("bob")
                .dataSource("ingressor")
                .addMetadata("abc", "efg")
                .mediaType("plain/text");

        byValue.execute();
        Mockito.verify(ingressService).ingress(byValue);
    }

}
