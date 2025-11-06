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