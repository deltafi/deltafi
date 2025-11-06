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
package org.deltafi.actionkit.lookup;

import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.GraphQLResponse;
import org.deltafi.actionkit.ActionKitAutoConfiguration;
import org.deltafi.actionkit.generated.types.Result;
import org.deltafi.common.http.HttpService;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.*;

@SpringBootTest(
        classes = { LookupTableClientTest.TestApp.class, LookupTableClientTest.Config.class },
        properties = {"cache.caches.lookup-table-client-cache=expireAfterWrite=PT1S"},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableAutoConfiguration(exclude = { ActionKitAutoConfiguration.class })
@EnableCaching
public class LookupTableClientTest {
    @SpringBootApplication
    public static class TestApp {
        public static void main(String... args) {
            SpringApplication.run(TestApp.class, args);
        }
    }

    private static final GraphQLClient GRAPH_QL_CLIENT = Mockito.mock(GraphQLClient.class);

    @Configuration
    public static class Config {
        @Bean
        public GraphQLClient graphQLClient() {
            return GRAPH_QL_CLIENT;
        }

        @Bean
        public HttpService httpService() {
            return Mockito.mock(HttpService.class);
        }

        @Bean
        public LookupTableClient lookupTableClient(Environment environment, GraphQLClient graphQLClient,
                HttpService httpService) {
            return new LookupTableClient("unused", graphQLClient, httpService);
        }
    }

    @Autowired
    private LookupTableClient lookupTableClient;

    private static final GraphQLResponse LOOKUP_RESPONSE = new GraphQLResponse("""
            {
              "data": {
                "lookup": {
                  "rows": [
                    [
                      {
                        "column": "city",
                        "value": "Miami"
                      },
                      {
                        "column": "state",
                        "value": "FL"
                      }
                    ],
                    [
                      {
                        "column": "city",
                        "value": "Austin"
                      },
                      {
                        "column": "state",
                        "value": "TX"
                      }
                    ],
                    [
                      {
                        "column": "city",
                        "value": "Las Vegas"
                      },
                      {
                        "column": "state",
                        "value": "NV"
                      }
                    ]
                  ],
                  "totalCount": 25
                }
              }
            }""");

    @BeforeEach
    public void setUp() {
        Mockito.reset(GRAPH_QL_CLIENT);

        String simple = """
                {
                  lookup(lookupTableName: "test-lookup-table") {
                    offset
                    count
                    totalCount
                    rows {
                      column
                      value
                    }
                  }
                }""";
        Mockito.when(GRAPH_QL_CLIENT.executeQuery(Mockito.eq(simple), Mockito.eq(Collections.emptyMap()))).thenReturn(LOOKUP_RESPONSE);

        // Spaces after map keys in matchingColumnValues are intentional to match generated DGS serialization!
        String complex1 = """
                {
                  lookup(lookupTableName: "test-lookup-table", matchingColumnValues: [{column : "state", value : ["NV", "TX"]}], resultColumns: ["city"]) {
                    offset
                    count
                    totalCount
                    rows {
                      column
                      value
                    }
                  }
                }""";
        Mockito.when(GRAPH_QL_CLIENT.executeQuery(Mockito.eq(complex1), Mockito.eq(Collections.emptyMap()))).thenReturn(LOOKUP_RESPONSE);
        String complex2 = """
                {
                  lookup(lookupTableName: "test-lookup-table", matchingColumnValues: [{column : "state", value : ["FL"]}], resultColumns: ["city"]) {
                    offset
                    count
                    totalCount
                    rows {
                      column
                      value
                    }
                  }
                }""";
        Mockito.when(GRAPH_QL_CLIENT.executeQuery(Mockito.eq(complex2), Mockito.eq(Collections.emptyMap()))).thenReturn(LOOKUP_RESPONSE);
        String complex3 = """
                {
                  lookup(lookupTableName: "test-lookup-table", matchingColumnValues: [{column : "state", value : ["NV", "TX"]}], resultColumns: ["city", "state"]) {
                    offset
                    count
                    totalCount
                    rows {
                      column
                      value
                    }
                  }
                }""";
        Mockito.when(GRAPH_QL_CLIENT.executeQuery(Mockito.eq(complex3), Mockito.eq(Collections.emptyMap()))).thenReturn(LOOKUP_RESPONSE);
    }

    @Test
    public void lookup() throws Exception {
        LookupResults results = lookupTableClient.lookup("test-lookup-table", LookupOptions.defaultLookupOptions());

        Assertions.assertEquals(3, results.results().size());
        Assertions.assertEquals("Austin", results.results().get(1).get("city"));
        Assertions.assertEquals("TX", results.results().get(1).get("state"));
    }

    @Test
    public void cachesSimple() throws Exception {
        lookupTableClient.clearCache();

        LookupResults results = lookupTableClient.lookup("test-lookup-table", LookupOptions.defaultLookupOptions());

        Mockito.verify(GRAPH_QL_CLIENT).executeQuery(Mockito.anyString(), Mockito.anyMap());
        Assertions.assertEquals(3, results.results().size());
        Assertions.assertEquals("Austin", results.results().get(1).get("city"));
        Assertions.assertEquals("TX", results.results().get(1).get("state"));

        lookupTableClient.lookup("test-lookup-table", LookupOptions.defaultLookupOptions());
        Mockito.verify(GRAPH_QL_CLIENT).executeQuery(Mockito.anyString(), Mockito.anyMap());

        Thread.sleep(Duration.ofSeconds(1));
        lookupTableClient.lookup("test-lookup-table", LookupOptions.defaultLookupOptions());
        Mockito.verify(GRAPH_QL_CLIENT, Mockito.times(2)).executeQuery(Mockito.anyString(), Mockito.anyMap());
    }

    @Test
    public void cachesComplex() throws Exception {
        lookupTableClient.clearCache();

        LookupResults results = lookupTableClient.lookup("test-lookup-table",
                LookupOptions.builder().matchingColumnValues(Map.of("state", new TreeSet<>(Set.of("NV", "TX"))))
                        .resultColumns(new TreeSet<>(Set.of("city"))).build());

        Mockito.verify(GRAPH_QL_CLIENT).executeQuery(Mockito.anyString(), Mockito.anyMap());
        Assertions.assertEquals(3, results.results().size());
        Assertions.assertEquals("Austin", results.results().get(1).get("city"));
        Assertions.assertEquals("TX", results.results().get(1).get("state"));

        // Same query; pulls from cache
        lookupTableClient.lookup("test-lookup-table",
                LookupOptions.builder().matchingColumnValues(Map.of("state", new TreeSet<>(Set.of("NV", "TX"))))
                        .resultColumns(new TreeSet<>(Set.of("city"))).build());
        Mockito.verify(GRAPH_QL_CLIENT).executeQuery(Mockito.anyString(), Mockito.anyMap());

        // Different matching values; makes new query
        lookupTableClient.lookup("test-lookup-table", LookupOptions.builder()
                        .matchingColumnValues(Map.of("state", new TreeSet<>(Set.of("FL"))))
                        .resultColumns(new TreeSet<>(Set.of("city"))).build());
        Mockito.verify(GRAPH_QL_CLIENT, Mockito.times(2)).executeQuery(Mockito.anyString(), Mockito.anyMap());

        // Different result columns; makes new query
        lookupTableClient.lookup("test-lookup-table", LookupOptions.builder()
                        .matchingColumnValues(Map.of("state", new TreeSet<>(Set.of("NV", "TX"))))
                        .resultColumns(new TreeSet<>(Set.of("city", "state"))).build());
        Mockito.verify(GRAPH_QL_CLIENT, Mockito.times(3)).executeQuery(Mockito.anyString(), Mockito.anyMap());
    }

    @Test
    public void batchesUpserts() throws Exception {
        GraphQLResponse graphQLResponse = Mockito.mock(GraphQLResponse.class);
        Mockito.when(graphQLResponse.hasErrors()).thenReturn(false);
        Mockito.when(graphQLResponse.extractValueAsObject(Mockito.eq("upsertLookupTableRows"), Mockito.eq(Result.class)))
                .thenReturn(Result.newBuilder().success(true).build());
        Mockito.when(GRAPH_QL_CLIENT.executeQuery(Mockito.anyString(), Mockito.anyMap())).thenReturn(graphQLResponse);

        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 0; i < 20001; i++) {
            rows.add(Map.of("column_a", Integer.toString(i)));
        }
        Result result = lookupTableClient.upsertRows("test-lookup-table", rows);
        Assertions.assertTrue(result.getSuccess());
        Mockito.verify(GRAPH_QL_CLIENT, Mockito.times(3)).executeQuery(Mockito.anyString(), Mockito.anyMap());
    }
}
