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
package org.deltafi.core.metrics.statsd;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.MissingResourceException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class StatsdClientTest {
    final String HOSTNAME = "deltafi.org";
    final int PORT = 42;
    private final Socket socket = mock(Socket.class);
    private final PrintWriter stream = mock(PrintWriter.class);
    private final StatsdClient.SocketFactory socketFactory = mock(StatsdClient.SocketFactory.class);
    private final StatsdClient.StreamFactory streamFactory = mock(StatsdClient.StreamFactory.class);
    private final StatsdClient statsdClient = new StatsdClient(HOSTNAME, PORT, socketFactory, streamFactory);

    private final ArgumentCaptor<String> capturedString = ArgumentCaptor.forClass(String.class);

    @BeforeEach @SneakyThrows
    public void setup() {
        when(socketFactory.create(HOSTNAME, PORT)).thenReturn(socket);
        when(streamFactory.create(socket)).thenReturn(stream);
        doNothing().when(stream).println(capturedString.capture());
    }

    @Test @SneakyThrows
    void connect() {
        statsdClient.connect();
        verify(socketFactory).create(HOSTNAME, PORT);
    }

    @Test @SneakyThrows
    void close() {
        try (StatsdClient client = statsdClient) {
            client.connect();
        }
        verify(socketFactory).create(HOSTNAME, PORT);
        verify(socket).close();
    }

    @Test @SneakyThrows
    void sendCounter() {
        statsdClient.connect();
        statsdClient.sendCounter("foo", "42");
        assertThat(capturedString.getValue(), equalTo("foo:42|c"));
        assertThat(statsdClient.success(), is(true));
        statsdClient.close();
        verify(stream).close();
        verify(socket).close();
    }

    @Test @SneakyThrows
    void sendGauge() {
        statsdClient.connect();
        statsdClient.sendGauge("foo", "42");
        assertThat(capturedString.getValue(), equalTo("foo:42|g"));
        assertThat(statsdClient.success(), is(true));
        statsdClient.close();
        verify(stream).close();
        verify(socket).close();
    }

    @Test @SneakyThrows
    void connectFailure() {
        statsdClient.connect();
        assertThat(statsdClient.success(), is(true));
        statsdClient.sendGauge("foo", "42");
        assertThat(statsdClient.success(), is(true));
        statsdClient.close();
        doThrow(new IOException("Boom")).when(socketFactory).create(HOSTNAME, PORT);
        assertThrows(IOException.class, statsdClient::connect);
        assertThat(statsdClient.success(), is(false));
        assertThrows(IOException.class, statsdClient::connect);
        assertThat(statsdClient.success(), is(false));
    }

    @Test @SneakyThrows
    void closeFailure() {
        doThrow(new IOException("Boom")).when(socket).close();
        statsdClient.connect();
        assertThat(statsdClient.success(), is(true));
        statsdClient.sendGauge("foo", "42");
        assertThat(statsdClient.success(), is(true));
        statsdClient.close();
        assertThat(statsdClient.success(), is(false));
    }

    @Test @SneakyThrows
    void sendFailure() {
        doThrow(new MissingResourceException("Boom", "Boom", "Boom")).when(stream).println(anyString());
        statsdClient.connect();
        assertThat(statsdClient.success(), is(true));
        assertThrows(MissingResourceException.class, () -> statsdClient.sendGauge("foo", "42"));
        assertThat(statsdClient.success(), is(false));
        statsdClient.close();
        assertThat(statsdClient.success(), is(false));
    }

    @Test @SneakyThrows
    void sanitize() {
        statsdClient.connect();
        statsdClient.sendGauge("foo |bar : baz", "42");
        assertThat(capturedString.getValue(), equalTo("foo-bar-baz:42|g"));
        assertThat(statsdClient.success(), is(true));
    }

    @Test @SneakyThrows
    void doubleConnect() {
        statsdClient.connect();
        assertThrows( IllegalStateException.class, statsdClient::connect);
    }

}