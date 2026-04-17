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
    "context"
    "log"
    "math/rand/v2"

    actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"
)

var colors = []string{"red", "orange", "yellow", "green", "blue", "indigo", "violet"}

func init() {
    actionkit.RegisterSupplier(&MyLookupTableSupplier{})
}

type MyLookupTableSupplier struct{}

func (s *MyLookupTableSupplier) Table() actionkit.LookupTableDefinition {
    return actionkit.LookupTableDefinition{
        Name:            "my_lookup_table",
        Columns:         []string{"column_a", "column_b", "color"},
        KeyColumns:      []string{"column_a"},
        RefreshDuration: "PT1M",
    }
}

func (s *MyLookupTableSupplier) GetRows(ctx context.Context, variables map[string]string,
    matchingColumns map[string][]string, resultColumns []string) ([]map[string]string, error) {
    log.Println("Getting rows for my_lookup_table")
    return []map[string]string{
        {"column_a": "A1", "column_b": "B1", "color": colors[rand.IntN(len(colors))]},
        {"column_a": "A2", "column_b": "B2", "color": colors[rand.IntN(len(colors))]},
        {"column_a": "A3", "column_b": "B3", "color": colors[rand.IntN(len(colors))]},
    }, nil
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

The package-level `Lookup` function queries lookup tables using a builder pattern:

```go
func (a *MyAction) Transform(df *actionkit.Deltafile, params MyParams) *actionkit.Deltafile {
    lookupResults, err := actionkit.Lookup("my_lookup_table",
        actionkit.LookupQuery().
            WithMatch("color", "red", "green", "blue").
            WithResultColumns("column_a", "color").
            WithSort("column_a", actionkit.SortDESC).
            WithOffset(0).
            WithLimit(100))
    if err != nil {
        return df.Errorf("Unable to lookup rows").SetErrorContext(err.Error())
    }

    df.PassthroughContent()
    df.AddMetadata("totalRows", fmt.Sprintf("%d", lookupResults.TotalCount))
    // ... use lookupResults.Rows
    return df
}
```

To return all (unsorted) rows with all result columns, pass `nil` for the query builder.

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