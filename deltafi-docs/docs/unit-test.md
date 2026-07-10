# Unit Testing 

## Java Action Testing

The `deltafi-action-kit-test` dependency provides classes to simplify writing unit tests for actions. It includes a helper class for setting up the tests and assertions to verify the results. This dependency is automatically included when using the `org.deltafi.plugin-convention` gradle plugin.

### Test Setup

The `org.deltafi.test.content.DeltaFiTestRunner` class is used to prepare input for the action under test. The `DeltaFiTestRunner` provides the following:

- A memory backed `ContentStorageService` that will be used by the action under test
- Methods to create `ActionContent` to use in the action input
- A method to get a pre-populated `ActionContext` to use in the action input

Below is a sample unit test showing the standard setup.

```java
package org.deltafi.helloworld.actions;

import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.asserters.*;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.util.*;

class HelloWorldTransformActionTest {

    // create the action to test
    HelloWorldTransformAction helloWorldTransformAction = new HelloWorldTransformAction();
    
    // prepare the test runner
    DeltaFiTestRunner deltaFiTestRunner = DeltaFiTestRunner.setup();
    
    // get an ActionContext to use in the action input in each test
    ActionContext actionContext = deltaFiTestRunner.actionContext();
    
    @Test
    void simpleTest() {
        // The saveContent method stores the content in memory and returns the ActionContent that points to it
        ActionContent content = deltaFiTestRunner.saveContent("content data", "content-name", "text/plain");

        // Create the test input using the content that was saved above
        TransformInput testInput = TransformInput.builder()
                .content(List.of(content))
                .metadata(Map.of("key", "value"))
                .build();

        // Create parameters to pass to the action
        HelloWorldTransformParameters params = new HelloWorldTransformParameters();

        // Execute the action
        ResultType resultType = helloWorldTransformAction.transform(actionContext, params, testInput);
    }
}
```

### Result Verification

The `org.deltafi.test.asserters` package provides classes used to verify the results from executing an `Action`. Each result type has a set of predefined assertions that can be used to validate the results.

The following code shows sample usage of a subset of the assertions.


```java
package org.deltafi.helloworld.actions;

import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.actionkit.action.transform.TransformResultType;
import org.deltafi.common.types.ActionContext;
import org.deltafi.test.asserters.*;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.util.*;

class HelloWorldTransformActionTest {

    HelloWorldTransformAction helloWorldTransformAction = new HelloWorldTransformAction();
    DeltaFiTestRunner deltaFiTestRunner = DeltaFiTestRunner.setup();
    ActionContext actionContext = deltaFiTestRunner.actionContext();
    
    @Test
    void simpleTest() {
        ResultType resultType = helloWorldTransformAction.transform(actionContext, new HelloWorldTransformParameters(), TransformInput.builder().build());

        // expect a transform result and verify all the parts
        TransformResultAssert.assertThat(resultType)
                .hasMatchingContentAt(0, "name", "mediaType", "expected this content to be saved")
                .addedMetadata("new-key", "value")
                .deletedMetadataKey("deleted-key")
                .hasMetric("some-metric", 1, Map.of("tag", "tag-value"));

        // expect a filter result with an exact cause
        FilterResultAssert.assertThat(resultType)
                .hasCause("filtered reason");

        // expect an error result with a cause that matches the regex
        ErrorResultAssert.assertThat(resultType)
                .hasCauseLike(".*errored reason substring.*");

        // expect an egress result
        EgressResultAssert.assertThat(resultType);
    }
}
```

## Go Action Testing

::: warning Beta
The Go action kit is currently in beta. Interfaces may change in future releases.
:::

The `deltafi-go-action-kit/v2` package includes test helpers for unit testing actions without external services (Redis, MinIO). The package provides:

- `NewTestContext(did)` — creates a `TestContext` with a pre-configured `ActionContext` wired to an in-memory content store
- `TestContext.Content(name, mediaType, data)` — creates an `ActionContent` from a string for use as input
- `TestContext.NewDeltafile(contents...)` — creates a `*Deltafile` wired to the test context
- `TestContext.NewTimedIngressDeltafile()` — creates a `*TimedIngressDeltafile` for ingress action tests
- `RunTransform`, `RunEgress`, `RunIngress` — generic test runners that execute actions and return `*ActionEvent`
- `RequireTransform`, `RequireSingleTransform`, `RequireError`, `RequireFilter`, `RequireEgress`, `RequireIngress` — result assertions on `*ActionEvent`
- `MockLookupClient` — configurable mock for lookup table queries with full query semantics
- `MockArtifactClient` — configurable mock for artifact registry access

### Test Setup

```go
package actions

import (
    "testing"

    "github.com/stretchr/testify/assert"
    "github.com/stretchr/testify/require"
    actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit/v2"
)

func TestMyTransformAction(t *testing.T) {
    // Create a test context with an in-memory content store
    tc := actionkit.NewTestContext("test-did")

    // Store test content
    content := tc.Content("input.txt", "text/plain", "hello world")

    // Create a Deltafile with the test content
    df := tc.NewDeltafile(content)

    // Execute the action and get the result event
    event := actionkit.RunTransform(t, &MyTransformAction{}, df, MyTransformParams{})

    // Assert it's a single transform result
    te := event.RequireSingleTransform(t)

    // Verify the output content
    require.Len(t, te.Content, 1)
    data := te.RequireContentString(t, 0)
    assert.Contains(t, data, "hello world")
    assert.Contains(t, data, "transformed")

    // Verify metadata and annotations
    assert.Equal(t, "transformValue", te.Metadata["transformKey"])
}
```

### Result Verification

The `*ActionEvent` returned by the test runners provides assertion methods for each result type:

```go
func TestFilterResult(t *testing.T) {
    tc := actionkit.NewTestContext("2-starts-with-two")
    df := tc.NewDeltafile(tc.Content("in.txt", "text/plain", "data"))

    event := actionkit.RunTransform(t, &MyTransformAction{}, df, MyTransformParams{})
    event.RequireFilter(t, "We prefer dids that do not start with 2")
}

func TestErrorResult(t *testing.T) {
    tc := actionkit.NewTestContext("test-did")
    df := tc.NewDeltafile() // no content

    event := actionkit.RunTransform(t, &MyTransformAction{}, df, MyTransformParams{})
    event.RequireError(t, "No content provided")
}
```

### Testing Split Actions

Split actions are transform actions that create children via `df.AddChild(name)`:

```go
func TestMySplitAction(t *testing.T) {
    tc := actionkit.NewTestContext("test-did")
    c1 := tc.Content("file1.txt", "text/plain", "data1")
    c2 := tc.Content("file2.txt", "text/plain", "data2")
    df := tc.NewDeltafile(c1, c2)

    event := actionkit.RunTransform(t, &MySplitAction{}, df, MySplitParams{})

    transforms := event.RequireTransform(t)
    assert.Len(t, transforms, 2)
}
```

### Testing Ingress Actions

```go
func TestMyIngressAction(t *testing.T) {
    tc := actionkit.NewTestContext("test-did")
    tc.ActionContext.Memo = "5" // simulate previous memo
    timed := tc.NewTimedIngressDeltafile()

    event := actionkit.RunIngress(t, &MyIngressAction{}, timed, MyIngressParams{})

    ie := event.RequireIngress(t)
    assert.Len(t, ie.IngressItems, 1)
    assert.Equal(t, "6", ie.Memo)
}
```

### Mock Lookup Client

```go
func TestWithLookup(t *testing.T) {
    tc := actionkit.NewTestContext("test-did")
    tc.SetLookupClient(t, &actionkit.MockLookupClient{
        Tables: []actionkit.LookupTable{
            {
                Name: "my_table",
                Rows: []actionkit.LookupRow{
                    {"name": "Alice"},
                    {"name": "Bob"},
                },
            },
        },
    })

    df := tc.NewDeltafile(tc.Content("in.txt", "text/plain", "data"))

    event := actionkit.RunTransform(t, &MyTransformAction{}, df, MyTransformParams{})
    te := event.RequireSingleTransform(t)
    assert.NotEmpty(t, te.Metadata["lookupResult"])
}
```

## C++ Action Testing

::: warning Beta
The C++ action kit is currently in beta. Interfaces may change in future releases.
:::

The `deltafi/testing.hpp` header provides test helpers for unit testing C++ actions without external services (Redis, MinIO). It includes:

- `InMemoryContentStore` — an in-memory content store replacing MinIO
- `MockLookupClient` — configurable mock for lookup table queries
- `MockArtifactClient` — configurable mock for artifact registry access
- `new_test_context(did)` — creates a pre-configured `ActionContext` wired to the in-memory store
- `run_transform`, `run_egress`, `run_ingress` — test runners that execute actions and return inspectable results
- `require_transform`, `require_error`, `require_filter`, `require_egress`, `require_ingress` — result assertions

### Test Setup

```cpp
#include <catch2/catch_test_macros.hpp>
#include <deltafi/testing.hpp>
#include "actions/transform.hpp"

using namespace deltafi;
using namespace deltafi::testing;

TEST_CASE("Transform produces expected output") {
    auto tc = new_test_context("test-did");
    auto content = tc.content("input.txt", "text/plain", "hello world");

    TransformInput input;
    input.content = {content};
    input.metadata = {{"key", "value"}};

    MyTransformAction action;
    auto event = run_transform(action, tc.action_context, input);

    // Assert it's a transform result
    auto& transforms = require_transform(event);

    // Verify the output content
    REQUIRE(transforms[0].content.size() == 1);
    auto data = transforms[0].content[0].load_string();
    CHECK(data.find("hello world") != std::string::npos);
    CHECK(data.find("transformed") != std::string::npos);

    // Verify metadata
    CHECK(transforms[0].metadata.at("transformKey") == "transformValue");
}
```

### Result Verification

The test assertion functions validate the result type and return the typed event for inspection:

```cpp
TEST_CASE("Filter result on DID prefix") {
    auto tc = new_test_context("2-starts-with-two");
    auto content = tc.content("in.txt", "text/plain", "data");

    TransformInput input;
    input.content = {content};

    MyTransformAction action;
    auto event = run_transform(action, tc.action_context, input);
    require_filter(event, "We prefer dids that do not start with 2");
}

TEST_CASE("Error result on no content") {
    auto tc = new_test_context("test-did");
    TransformInput input;  // no content

    MyTransformAction action;
    auto event = run_transform(action, tc.action_context, input);
    require_error(event, "No content provided");
}
```

### Mock Lookup Client

```cpp
TEST_CASE("Action uses lookup table") {
    auto tc = new_test_context("test-did");
    MockLookupClient lookup;
    lookup.set_results("my_table", LookupResults{
        2, std::nullopt, std::nullopt,
        {
            {{"name", "Alice"}},
            {{"name", "Bob"}},
        }
    });
    tc.action_context.lookup_client = &lookup;

    auto content = tc.content("in.txt", "text/plain", "data");
    TransformInput input;
    input.content = {content};

    MyTransformAction action;
    auto event = run_transform(action, tc.action_context, input);
    auto& transforms = require_transform(event);
    CHECK(!transforms[0].metadata.at("lookupResult").empty());
}
```
