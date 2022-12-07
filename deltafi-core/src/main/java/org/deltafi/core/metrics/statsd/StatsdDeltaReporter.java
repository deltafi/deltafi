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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metered;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

/**
 * Codahale metrics reporter for reporting delta counters to a statsd server
 */
@NotThreadSafe
@Slf4j
public class StatsdDeltaReporter extends ScheduledReporter {
  private final StatsdClient statsdClient;
  private final String prefix;

  @Builder(builderMethodName = "hiddenBuilder")
  protected StatsdDeltaReporter(final MetricRegistry registry,
                                final String host,
                                final int port,
                                final String prefix,
                                final TimeUnit rateUnit,
                                final TimeUnit durationUnit,
                                final MetricFilter filter) {
    super(registry, "statsd-delta-reporter", filter, rateUnit, durationUnit);
    this.statsdClient = new StatsdClient(host, port);
    this.prefix = prefix;
  }

  // Just to make javadocs happy
  public static class StatsdDeltaReporterBuilder {}

  public static StatsdDeltaReporter.StatsdDeltaReporterBuilder builder(String host, int port, MetricRegistry registry) {
    return hiddenBuilder()
            .host(host)
            .port(port)
            .registry(registry)
            .rateUnit(TimeUnit.SECONDS)
            .durationUnit(TimeUnit.MILLISECONDS)
            .filter(MetricFilter.ALL);
  }

  @SuppressWarnings("rawtypes")
  @Override
  public void report(final SortedMap<String, Gauge> gauges,
                     final SortedMap<String, Counter> counters,
                     final SortedMap<String, Histogram> histograms,
                     final SortedMap<String, Meter> meters,
                     final SortedMap<String, Timer> timers) {

    try (StatsdClient client = statsdClient) {
      client.connect();

      for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
        reportGauge(entry.getKey(), entry.getValue());
      }

      for (Map.Entry<String, Counter> entry : counters.entrySet()) {
        reportCounter(entry.getKey(), entry.getValue());
      }

      for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
        reportHistogram(entry.getKey(), entry.getValue());
      }

      for (Map.Entry<String, Meter> entry : meters.entrySet()) {
        reportMetered(entry.getKey(), entry.getValue());
      }

      for (Map.Entry<String, Timer> entry : timers.entrySet()) {
        reportTimer(entry.getKey(), entry.getValue());
      }
    } catch (IllegalStateException e) {
      log.error("multiple statsd connections attempted.  Possible threading issue", e);
    } catch (IOException e) {
      log.warn("statsd service failure: {}", e.getMessage());
    } catch (Throwable e) {
      log.error("Unexpected error", e);
    }
  }

  private void reportTimer(final String name, final Timer timer) {
    final Snapshot snapshot = timer.getSnapshot();

    statsdClient.sendGauge(prefix(name, "max"), formatNumber(convertDuration(snapshot.getMax())));
    statsdClient.sendGauge(prefix(name, "mean"), formatNumber(convertDuration(snapshot.getMean())));
    statsdClient.sendGauge(prefix(name, "min"), formatNumber(convertDuration(snapshot.getMin())));
    statsdClient.sendGauge(prefix(name, "stddev"), formatNumber(convertDuration(snapshot.getStdDev())));
    statsdClient.sendGauge(prefix(name, "p50"), formatNumber(convertDuration(snapshot.getMedian())));
    statsdClient.sendGauge(prefix(name, "p75"), formatNumber(convertDuration(snapshot.get75thPercentile())));
    statsdClient.sendGauge(prefix(name, "p95"), formatNumber(convertDuration(snapshot.get95thPercentile())));
    statsdClient.sendGauge(prefix(name, "p98"), formatNumber(convertDuration(snapshot.get98thPercentile())));
    statsdClient.sendGauge(prefix(name, "p99"), formatNumber(convertDuration(snapshot.get99thPercentile())));
    statsdClient.sendGauge(prefix(name, "p999"), formatNumber(convertDuration(snapshot.get999thPercentile())));

    reportMetered(name, timer);
  }

  private void reportMetered(final String name, final Metered meter) {
    statsdClient.sendGauge(prefix(name, "samples"), formatNumber(meter.getCount()));
    statsdClient.sendGauge(prefix(name, "m1_rate"), formatNumber(convertRate(meter.getOneMinuteRate())));
    statsdClient.sendGauge(prefix(name, "m5_rate"), formatNumber(convertRate(meter.getFiveMinuteRate())));
    statsdClient.sendGauge(prefix(name, "m15_rate"), formatNumber(convertRate(meter.getFifteenMinuteRate())));
    statsdClient.sendGauge(prefix(name, "mean_rate"), formatNumber(convertRate(meter.getMeanRate())));
  }

  private void reportHistogram(final String name, final Histogram histogram) {
    final Snapshot snapshot = histogram.getSnapshot();
    statsdClient.sendGauge(prefix(name, "samples"), formatNumber(histogram.getCount()));
    statsdClient.sendGauge(prefix(name, "max"), formatNumber(snapshot.getMax()));
    statsdClient.sendGauge(prefix(name, "mean"), formatNumber(snapshot.getMean()));
    statsdClient.sendGauge(prefix(name, "min"), formatNumber(snapshot.getMin()));
    statsdClient.sendGauge(prefix(name, "stddev"), formatNumber(snapshot.getStdDev()));
    statsdClient.sendGauge(prefix(name, "p50"), formatNumber(snapshot.getMedian()));
    statsdClient.sendGauge(prefix(name, "p75"), formatNumber(snapshot.get75thPercentile()));
    statsdClient.sendGauge(prefix(name, "p95"), formatNumber(snapshot.get95thPercentile()));
    statsdClient.sendGauge(prefix(name, "p98"), formatNumber(snapshot.get98thPercentile()));
    statsdClient.sendGauge(prefix(name, "p99"), formatNumber(snapshot.get99thPercentile()));
    statsdClient.sendGauge(prefix(name, "p999"), formatNumber(snapshot.get999thPercentile()));
  }

  /**
   * This method insures that delta counters are reported by capturing the counter value, reporting the captured
   * value, and then subtracting the captured value from the counter.  This is safe because the reporter is
   * single threaded and the counter decrement is atomic.
   * @param name name of counter
   * @param counter Counter object representing the delta counter metric
   */
  private void reportCounter(final String name, final Counter counter) {
    long value = counter.getCount();
    statsdClient.sendCounter(name, formatNumber(value));
    if (statsdClient.success()) { counter.dec(value); }

  }

  @SuppressWarnings("rawtypes")
  private void reportGauge(final String name, final Gauge gauge) {
    final String value = format(gauge.getValue());
    if (value != null) {
      statsdClient.sendGauge(prefix(name), value);
    }
  }

  @Nullable
  private String format(final Object o) {
    if (o instanceof Float) {
      return formatNumber(((Float) o).doubleValue());
    } else if (o instanceof Double) {
      return formatNumber((Double) o);
    } else if (o instanceof Byte) {
      return formatNumber(((Byte) o).longValue());
    } else if (o instanceof Short) {
      return formatNumber(((Short) o).longValue());
    } else if (o instanceof Integer) {
      return formatNumber(((Integer) o).longValue());
    } else if (o instanceof Long) {
      return formatNumber((Long) o);
    } else if (o instanceof BigInteger) {
      return formatNumber((BigInteger) o);
    } else if (o instanceof BigDecimal) {
      return formatNumber(((BigDecimal) o).doubleValue());
    }
    return null;
  }

  private String prefix(final String... components) {
    return MetricRegistry.name(prefix, components);
  }

  private String formatNumber(final BigInteger n) {
    return String.valueOf(n);
  }

  private String formatNumber(final long n) {
    return Long.toString(n);
  }

  private String formatNumber(final double v) {
    return String.format(Locale.US, "%2.2f", v);
  }
}
