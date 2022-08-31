/*
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

package org.deltafi.common.metrics.statsd;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

class StatsdClientTest {
    private final InetSocketAddress address = new InetSocketAddress("deltafi.org", 42);
    private final DatagramSocket socket = mock(DatagramSocket.class);
    private final StatsdClient.DatagramSocketFactory datagramSocketFactory = mock(StatsdClient.DatagramSocketFactory.class);
    private final StatsdClient.DatagramPacketFactory datagramPacketFactory = mock(StatsdClient.DatagramPacketFactory.class);
    private final StatsdClient statsdClient = new StatsdClient(address, datagramSocketFactory, datagramPacketFactory);

    private final ArgumentCaptor<byte[]> capturedBytes = ArgumentCaptor.forClass(byte[].class);
    private final ArgumentCaptor<InetSocketAddress> capturedAddress = ArgumentCaptor.forClass(InetSocketAddress.class);
    private final ArgumentCaptor<DatagramPacket> capturedDatagram = ArgumentCaptor.forClass(DatagramPacket.class);

    @BeforeEach @SneakyThrows
    public void setup() {
        when(datagramSocketFactory.create()).thenReturn(socket);
        when(datagramPacketFactory.create(capturedBytes.capture(), anyInt(), capturedAddress.capture())).thenCallRealMethod();
        doNothing().when(socket).send(capturedDatagram.capture());
    }

    @Test @SneakyThrows
    public void connect() {
        statsdClient.connect();
        verify(datagramSocketFactory).create();
    }

    @Test @SneakyThrows
    public void close() {
        try (StatsdClient client = statsdClient) {
            client.connect();
        }
        verify(datagramSocketFactory).create();
        verify(socket).close();
    }

    @Test @SneakyThrows
    public void sendCounter() {
        statsdClient.connect();
        statsdClient.sendCounter("foo", "42");
        assertThat(new String(capturedBytes.getValue()), equalTo("foo:42|c"));
        assertThat(capturedAddress.getValue(), equalTo(address));
        assertThat(statsdClient.success(), is(true));
    }

    @Test @SneakyThrows
    public void sendGauge() {
        statsdClient.connect();
        statsdClient.sendGauge("foo", "42");
        assertThat(new String(capturedBytes.getValue()), equalTo("foo:42|g"));
        assertThat(capturedAddress.getValue(), equalTo(address));
        assertThat(statsdClient.success(), is(true));
    }

    @Test @SneakyThrows
    public void failure() {
        doThrow(new IOException("Boom")).when(socket).send(capturedDatagram.capture());
        statsdClient.connect();
        statsdClient.sendGauge("foo", "42");
        assertThat(statsdClient.success(), is(false));
        statsdClient.sendGauge("foo", "42");
        assertThat(statsdClient.success(), is(false));
        doNothing().when(socket).send(capturedDatagram.capture());
        statsdClient.sendGauge("foo", "42");
        assertThat(statsdClient.success(), is(true));
    }

    @Test @SneakyThrows
    public void sanitize() {
        statsdClient.connect();
        statsdClient.sendGauge("foo |bar : baz", "42");
        assertThat(new String(capturedBytes.getValue()), equalTo("foo-bar-baz:42|g"));
        assertThat(capturedAddress.getValue(), equalTo(address));
        assertThat(statsdClient.success(), is(true));
    }

    @Test @SneakyThrows
    public void doubleConnect() {
        statsdClient.connect();
        assertThrows( IllegalStateException.class, statsdClient::connect);
    }

}