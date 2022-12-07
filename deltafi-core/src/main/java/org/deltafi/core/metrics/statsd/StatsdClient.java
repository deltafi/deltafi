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
package org.deltafi.core.metrics.statsd;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.*;
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

  private Socket socket;
  private int failCount;

  static public class SocketFactory {
    public Socket create(String host, int port) throws IOException { return new Socket(host, port); }
  }

  static public class StreamFactory {
    public PrintWriter create(Socket socket) throws IOException { return new PrintWriter(socket.getOutputStream(), true);
    }
  }

  final String host;
  final int port;
  private final SocketFactory socketFactory;
  private final StreamFactory streamFactory;

  private PrintWriter out = null;
  /**
   * Constructs a client for the given host and port.
   *
   * @param host hostname of the statsd server.
   * @param port statsd port on the server
   */
  public StatsdClient(final String host, final int port) {
    this(host, port, new SocketFactory(), new StreamFactory());
  }

  /***
   * Creates a socket connection to the statsd server
   *
   * @throws IllegalStateException if the client is already connected
   * @throws IOException           if there is an error connecting
   */
  public void connect() throws IOException {
    if (socket != null) {
      failCount++;
      throw new IllegalStateException("Already connected");
    }

    try {
      socket = socketFactory.create(host, port);
      out = streamFactory.create(socket);
    } catch (Throwable e) {
      incrementFailCount(e);
      if (socket != null) { socket.close(); }
      throw e;
    }
  }

  private void incrementFailCount(Throwable e) {
    failCount++;
    if (failCount == 1) {
      log.warn("Unable to transmit metrics to statsd", e);
    } else {
      log.debug("Unable to transmit metrics to statsd", e);
    }
  }

  private void send(final String name, final String value, final String format) {
    String message = String.format(format, sanitize(name), value);
    try {
      out.println(message);
    } catch (Throwable e) {
      incrementFailCount(e);
      throw e;
    }

    if (failCount > 0) {
      log.info("Resumed sending metrics to statsd");
      failCount = 0;
    }
  }

  /**
   * Sends the given measurement to the server as a counter
   *
   * @param name  metric name
   * @param value metric value
   */
  public void sendCounter(final String name, final String value) {
    send(name, value, "%s:%s|c");
  }

  /**
   * Sends the given measurement to the server as a gauge
   *
   * @param name  metric name
   * @param value metric value
   */
  public void sendGauge(final String name, final String value) {
    send(name, value, "%s:%s|g");
  }

  /**
   * Close the connection to the StatsD server
   */
  @Override
  public void close() {
    if (out != null) {
      out.close();
    }
    if (socket != null) {
      try {
        socket.close();
      } catch (Throwable e) {
        incrementFailCount(e);
      }
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
