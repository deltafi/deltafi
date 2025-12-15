# Contributing to deltafi-core

This guide covers patterns and conventions specific to the `deltafi-core` module.

## Code Organization

Key directories within `deltafi-core/src/main/java/org/deltafi/core/`:

| Directory | Purpose |
|-----------|---------|
| `types/` | Entity classes (`DeltaFile`, `DeltaFileFlow`, `Action`, etc.) |
| `services/` | Business logic (`DeltaFilesService`, `StateMachine`, etc.) |
| `datafetchers/` | GraphQL resolvers |
| `repo/` | Database repositories |
| `security/` | Permissions (`NeedsPermission.java`) |

## GraphQL Schemas

Schemas are in `deltafi-core/src/main/resources/schema/`, organized by domain:

| Schema | Purpose |
|--------|---------|
| `deltafi.graphqls` | DeltaFile operations, queries, core mutations |
| `action-config.graphqls` | Action configuration types |
| `flow-plan.graphqls` | Flow definitions |
| `plugin-schema.graphqls` | Plugin management |
| `delete-policy.graphqls` | Data retention policies |
| `resume-policy.graphqls` | Auto-resume configuration |
| `properties-schema.graphqls` | System properties |
| `publish-subscribe.graphqls` | Topic routing |
| `system-snapshot.graphqls` | Snapshot/restore |
| `testing.graphqls` | Integration testing |

Types defined in these schemas are generated into `org.deltafi.core.generated.types`. To use an existing Java class instead of generating one, add a `typeMapping` entry in `deltafi-core/build.gradle`:

```groovy
generateJava {
    typeMapping = [
        "MyGraphQLType": "org.deltafi.common.types.MyExistingClass"
    ]
}
```

## DeltaFile State Model

State cascades from Actions up through Flows to DeltaFiles:

```
DeltaFile.stage (IN_FLIGHT, COMPLETE, ERROR, CANCELLED)
    └── DeltaFileFlow.state (IN_FLIGHT, COMPLETE, ERROR, CANCELLED, FILTERED, PAUSED, PENDING_ANNOTATIONS)
            └── Action.state (QUEUED, COLD_QUEUED, COMPLETE, ERROR, FILTERED, CANCELLED, etc.)
```

When modifying DeltaFile state:
1. Update action state via methods like `action.error()` or `action.complete()`
2. Call `flow.updateState()` to recalculate flow state from action state
3. Call `deltaFile.updateState(now)` to recalculate DeltaFile stage and flags

## Common Patterns

### Adding a GraphQL Mutation or Query

1. Add the type and mutation/query to the appropriate `.graphqls` file
2. Run `./gradlew :deltafi-core:generateJava` to generate types
3. Add service method in the appropriate `*Service.java`
4. Add datafetcher method in `*Datafetcher.java` with appropriate `@NeedsPermission` annotation

### Bulk DeltaFile Operations

Follow this pattern (see `cancel()`, `acknowledge()` for examples):

```java
public Result bulkOperation(DeltaFilesFilter filter, ...) {
    filter.setStage(DeltaFileStage.IN_FLIGHT);  // Set required filter constraints
    filter.setCreatedBefore(OffsetDateTime.now(clock));  // Prevent race with new data

    int numFound = REQUEUE_BATCH_SIZE;  // 5000
    while (numFound == REQUEUE_BATCH_SIZE) {
        List<DeltaFile> batch = deltaFileRepo.deltaFiles(filter, REQUEUE_BATCH_SIZE);
        // Process batch...
        numFound = batch.size();
    }
}
```

Key considerations:
- Use `createdBefore` or `modifiedBefore` to prevent endless loops with incoming data
- Process in batches to avoid memory issues and timeouts
- Use optimistic locking (`@Version` field) for concurrent modification safety
- Catch `OptimisticLockingFailureException` per item when individual failures are acceptable
