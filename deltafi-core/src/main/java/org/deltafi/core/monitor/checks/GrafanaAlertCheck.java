/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.core.monitor.checks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.deltafi.core.monitor.MonitorProfile;
import org.deltafi.core.services.EventService;
import org.deltafi.core.types.Event;
import org.deltafi.core.types.Event.Severity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@MonitorProfile
public class GrafanaAlertCheck extends StatusCheck {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);


    private static final String ALERT_API = "/api/alertmanager/grafana/api/v2/alerts?active=true&silenced=false&inhibited=false";
    private static final String GRAFANA = "Grafana";

    private final HttpClient httpClient;
    private final EventService eventService;
    private final HttpRequest request;
    private Set<String> previousAlerts = new HashSet<>();

    public GrafanaAlertCheck(HttpClient httpClient, EventService eventService,
                             @Value("${DELTAFI_GRAFANA_URL:http://deltafi-grafana}") String grafanaUrl) {
        super("Grafana Alert Check");
        this.httpClient = httpClient;
        this.eventService = eventService;
        request = HttpRequest.newBuilder().GET()
                .uri(URI.create(grafanaUrl + ALERT_API))
                .setHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
                .setHeader("X-Metrics-Role", "Admin")
                .setHeader("X-User-Name", "admin").build();
    }

    @Override
    public CheckResult check() {
        List<GrafanaAlert> alerts = getAlerts();

        // create events for alerts that were not seen in the last check execution
        eventService.createEvents(alerts.stream()
                .filter(alert -> !previousAlerts.contains(alert.name()))
                .map(this::generateEvent).toList());

        clearOldAlerts(alerts);
        return result(0, "");
    }

    private void clearOldAlerts(List<GrafanaAlert> events) {
        Set<String> alertNames = events.stream().map(GrafanaAlert::name).collect(Collectors.toSet());

        // clear previous alerts that are no longer firing during this check
        eventService.createEvents(previousAlerts.stream()
                .filter(oldAlert -> !alertNames.contains(oldAlert))
                .map(this::genereateClearEvent).toList());

        this.previousAlerts = alertNames;
    }


    private Event generateEvent(GrafanaAlert alert) {
        return createEvent("Alert: " + alert.name(), alert.content(), alert.severity());
    }

    private Event genereateClearEvent(String name) {
        return createEvent("Alert cleared: " + name, null, Severity.SUCCESS);
    }

    private Event createEvent(String summary, String content, String severity) {
        return Event.builder()
                .summary(summary)
                .content(content)
                .severity(severity)
                .notification(true)
                .source(GRAFANA)
                .build();
    }

    private List<GrafanaAlert> getAlerts() {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return mapBody(response.body());
        } catch (IOException e) {
            log.error("Grafana request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Grafana Alert Check interrupted", e);
        }
        return List.of();
    }

    private List<GrafanaAlert> mapBody(String body) {
        try {
            return MAPPER.readValue(body, new TypeReference<>() {});
        } catch (IOException e) {
            log.error("Failed to read grafana alert json", e);
            return List.of();
        }
    }

    private record GrafanaAlert(GrafanaAnnotation annotations, Map<String, String> labels) {
        private static final Set<String> FILTERED_LABELS = Set.of("__alert_rule_uid__", "ref_id",
                "alertname", "datasource_uid", "grafana_folder");

        public String name() {
            return labels.getOrDefault("alertname", "Unknown");
        }

        public String severity() {
            return Severity.mapSeverity(labels.get("severity"));
        }

        public String content() {
            List<String> contentLines = new ArrayList<>();
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                String key = entry.getKey();
                if (!FILTERED_LABELS.contains(key)) {
                    String line = "- *" + StringUtils.capitalize(key) +
                            "*: " + entry.getValue();
                    contentLines.add(line);
                }
            }
            return StringUtils.join(contentLines, "\n");
        }
    }
    private record GrafanaAnnotation(String summary) {}
}
