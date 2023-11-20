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
package org.deltafi.common.stream;

import java.io.InputStream;
import java.io.PipedInputStream;
import java.util.concurrent.CountDownLatch;

public class PipelineBlockingInputStream extends PipedInputStream implements BlockingInputStream {
    private static final int DEFAULT_BUFFER_SIZE = 512 * 1024;
    CountDownLatch latch;

    public PipelineBlockingInputStream() {
        this(DEFAULT_BUFFER_SIZE);
    }

    public PipelineBlockingInputStream(int bufferSize) {
        super(bufferSize);
        this.latch = new CountDownLatch(1);
    }

    public void unblock() {
        while (latch.getCount() > 0) {
            latch.countDown();
        }
    }

    @Override
    public void await() throws InterruptedException {
        latch.await();
    }

    @Override
    public InputStream getInputStream() {
        return this;
    }
}
