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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Client class for sending metrics to a StatsD server
 *
 *  @see <a href="https://github.com/etsy/statsd">StatsD</a>
 */
@NotThreadSafe
@Slf4j
@RequiredArgsConstructor
public class StatsdClient implements Closeable {

  private static final Pattern ILLEGAL_CHARACTERS = Pattern.compile("[|:\\s]+");

  private final InetSocketAddress address;
  private DatagramSocket socket;
  private int failCount;

  static public class DatagramSocketFactory {
    public DatagramSocket create() throws SocketException { return new DatagramSocket(); }
  }

  static public class DatagramPacketFactory {
    public DatagramPacket create(byte[] bytes, int length, InetSocketAddress address) throws SocketException { return new DatagramPacket(bytes, length, address); }
  }

  private final DatagramSocketFactory datagramSocketFactory;
  private final DatagramPacketFactory datagramPacketFactory;

  /**
   * Constructs a client for the given host and port.
   *
   * @param host hostname of the statsd server.
   * @param port statsd port on the server
   */
  public StatsdClient(final String host, final int port) {
    this(new InetSocketAddress(host, port), new DatagramSocketFactory(), new DatagramPacketFactory());
  }

  /***
   * Creates a socket for the statsd server
   *
   * @throws IllegalStateException if the client is already connected
   * @throws IOException           if there is an error connecting
   */
  public void connect() throws IOException {
    if (socket != null) {
      throw new IllegalStateException("Already connected");
    }

    this.socket = datagramSocketFactory.create();
  }

  private void send(final String name, final String value, final String format) {
    try {
      byte[] bytes = String.format(format, sanitize(name), value).getBytes(StandardCharsets.UTF_8);
      socket.send(datagramPacketFactory.create(bytes, bytes.length, address));
      if (failCount > 0) {
        log.info("Resumed sending metrics to statsd");
      }
      failCount = 0;
    } catch (IOException e) {
      failCount++;

      if (failCount == 1) {
        log.warn("Unable to transmit metrics to statsd", e);
      } else {
        log.debug("Unable to transmit metrics to statsd", e);
      }
    }
  }

  /**
   * Sends the given measurement to the server as a counter. Logs exceptions.
   *
   * @param name  metric name
   * @param value metric value
   */
  public void sendCounter(final String name, final String value) {
    send(name, value, "%s:%s|c");
  }

  /**
   * Sends the given measurement to the server as a gauge. Logs exceptions.
   *
   * @param name  metric name
   * @param value metric value
   */
  public void sendGauge(final String name, final String value) {
    send(name, value, "%s:%s|g");
  }

  @Override
  public void close() throws IOException {
    if (socket != null) {
      socket.close();
    }
    this.socket = null;
  }

  private String sanitize(final String s) {
    return ILLEGAL_CHARACTERS.matcher(s).replaceAll("-");
  }

  /**
   * @return true if the last transmission was successful
   */
  public boolean success() { return failCount == 0; }
}
