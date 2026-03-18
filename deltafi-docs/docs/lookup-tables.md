# Lookup Tables

Lookup tables provide a centralized database solution for actions requiring tabular data. A simple API in the DeltaFi
Action Kit provides queries for the data.

## Lookup Table Suppliers

Lookup table suppliers are defined in Plugins to supply data to lookup tables. Suppliers will provide data on startup
and can be configured to refresh it periodically.

### Java

A lookup table supplier will extend the `LookupTableSupplier` class, providing the `LookupTableClient` (auto-configured
by the DeltaFi Action Kit) and the lookup table definition. It will also define the `getRows` method to provide rows for
the lookup table.

```java
package org.deltafi.helloworld;

import lombok.extern.slf4j.Slf4j;
import org.deltafi.actionkit.lookup.LookupTableClient;
import org.deltafi.actionkit.lookup.LookupTableSupplier;
import org.deltafi.common.lookup.LookupTable;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class MyLookupTableSupplier extends LookupTableSupplier {
    public MyLookupTableSupplier(LookupTableClient lookupTableClient) {
        super(lookupTableClient, LookupTable.builder()
                .name("my_lookup_table")
                .columns(List.of("column_a", "column_b", "color"))
                .keyColumns(List.of("column_a"))
                .refreshDuration("PT1M")
                .build());
    }

    @Override
    public List<Map<String, String>> getRows(@Nullable Map<String, Set<String>> matchingColumnValues,
            @Nullable List<String> resultColumns) {
        log.info("Getting rows for {}", getLookupTable().getName());

        return List.of(
                Map.of("column_a", "A1", "column_b", "B1", "color", randomColor()),
                Map.of("column_a", "A2", "column_b", "B2", "color", randomColor()),
                Map.of("column_a", "A3", "column_b", "B3", "color", randomColor()));
    }

    private static final List<String> COLORS = List.of("red", "orange", "yellow", "green", "blue", "indigo", "violet");

    private String randomColor() {
        return COLORS.get((int) (Math.random() * COLORS.size()));
    }
}
```

### Go

A lookup table supplier in Go implements the `LookupTableSupplier` interface, which defines the table schema and provides rows on demand.

```go
package suppliers

import (
    "log"
    "math/rand"

    actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit"
)

var colors = []string{"red", "orange", "yellow", "green", "blue", "indigo", "violet"}

func init() {
    actionkit.RegisterLookupTableSupplier(&MyLookupTableSupplier{})
}

type MyLookupTableSupplier struct{}

func (s *MyLookupTableSupplier) TableDefinition() actionkit.LookupTableDefinition {
    return actionkit.LookupTableDefinition{
        Name:            "my_lookup_table",
        Columns:         []string{"column_a", "column_b", "color"},
        KeyColumns:      []string{"column_a"},
        RefreshDuration: "PT1M",
    }
}

func (s *MyLookupTableSupplier) GetRows(variables map[string]string,
    matchingColumns map[string][]string, resultColumns []string) []map[string]string {
    log.Println("Getting rows for my_lookup_table")
    return []map[string]string{
        {"column_a": "A1", "column_b": "B1", "color": colors[rand.Intn(len(colors))]},
        {"column_a": "A2", "column_b": "B2", "color": colors[rand.Intn(len(colors))]},
        {"column_a": "A3", "column_b": "B3", "color": colors[rand.Intn(len(colors))]},
    }
}
```

### C++

A lookup table supplier in C++ extends `LookupTableSupplierBase`, which defines the table schema and provides rows on demand.

```cpp
#pragma once
#include <deltafi/plugin>

class MyLookupTableSupplier : public deltafi::LookupTableSupplierBase {
public:
    deltafi::LookupTableDefinition table() const override {
        return {
            .name = "my_lookup_table",
            .columns = {"column_a", "column_b", "color"},
            .key_columns = {"column_a"},
            .refresh_duration = "PT1M",
        };
    }

    std::vector<std::map<std::string, std::string>> get_rows(
        const std::map<std::string, std::string>& variables,
        const std::map<std::string, std::vector<std::string>>& matching_column_values,
        const std::vector<std::string>& result_columns) override {
        return {
            {{"column_a", "A1"}, {"column_b", "B1"}, {"color", "red"}},
            {{"column_a", "A2"}, {"column_b", "B2"}, {"color", "green"}},
            {{"column_a", "A3"}, {"column_b", "B3"}, {"color", "blue"}},
        };
    }
};

DELTAFI_SUPPLIER(MyLookupTableSupplier)
```

## Lookup Table API

### Java

The `LookupTableClient` (auto-configured by the DeltaFi Action Kit) provides a `lookup` method for Actions to query a
lookup table. First, pass the `LookupTableClient` to the Action constructor and assign it to an instance variable. Then,
use it as follows:

```
...
    try {
        LookupResults rgbRows = lookupTableClient.lookup("my_lookup_table",
                LookupOptions.builder()
                    .matchingColumnValues(Map.of("color", Set.of("red", "green", "blue")))
                    .resultColumns(Set.of("column_a", "color"))
                    .sortColumn("column_a")
                    .sortDirection(SortDirection.DESC)
                    .offset(offset)
                    .limit(limit)
                    .build());
        result.addMetadata("Total RGB Rows", rgbRows.totalCount());
        result.addMetadata("RGB Rows", String.join(", ", rgbRows.results().stream().map(m -> m.get("column_a")).toList()));
    } catch (Exception e) {
        return new ErrorResult(context, "Unable to lookup RGB rows", e);
    }
```

To return all (unsorted) rows with all result columns, pass LookupOptions.defaultLookupOptions().

### Go

The `LookupClient` is available on the `ActionContext` and provides a `Lookup` method for querying lookup tables:

```go
func (a *MyAction) Transform(ctx context.Context, input actionkit.TransformInput) (actionkit.Result, error) {
    lookupResults, err := input.ActionContext.LookupClient.Lookup(ctx, "my_lookup_table",
        &actionkit.LookupOptions{
            MatchingColumnValues: map[string][]string{"color": {"red", "green", "blue"}},
            ResultColumns:        []string{"column_a", "color"},
            SortColumn:           "column_a",
            SortDirection:        "DESC",
            Offset:               0,
            Limit:                100,
        })
    if err != nil {
        return actionkit.NewErrorResult(input.ActionContext, "Unable to lookup rows").SetContext(err.Error()), nil
    }

    result := actionkit.NewTransformResult(input.ActionContext)
    result.AddMetadata("totalRows", fmt.Sprintf("%d", lookupResults.TotalCount))
    // ... use lookupResults.Rows
    return result, nil
}
```

To return all (unsorted) rows with all result columns, pass `nil` as the options.

### C++

The `LookupClient` is available on the `ActionContext` and provides a `lookup` method for querying lookup tables:

```cpp
deltafi::LookupOptions opts;
opts.matching_column_values = {{"color", {"red", "green", "blue"}}};
opts.result_columns = {"column_a", "color"};
opts.sort_column = "column_a";
opts.sort_direction = deltafi::SortDirection::DESC;
opts.offset = 0;
opts.limit = 100;

try {
    auto results = context.lookup_client->lookup("my_lookup_table", &opts);
    result.add_metadata("totalRows", std::to_string(results.total_count));
    // ... use results.rows
} catch (const std::exception& ex) {
    return deltafi::ErrorResult(context, "Unable to lookup rows")
        .set_context(ex.what());
}
```

To return all (unsorted) rows with all result columns, pass `nullptr` as the options.