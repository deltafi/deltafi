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

The `deltafi-go-action-kit` package includes test helpers for unit testing actions without external services (Redis, MinIO). The package provides:

- `InMemoryContentStore` — an in-memory content store replacing MinIO
- `MockLookupClient` — configurable mock for lookup table queries
- `MockArtifactClient` — configurable mock for artifact registry access
- `NewTestContext(did)` — creates a pre-configured `ActionContext` wired to the in-memory store
- `RunTransform`, `RunEgress`, `RunIngress` — test runners that execute actions and return inspectable results
- `RequireTransform`, `RequireError`, `RequireFilter`, `RequireEgress` — result assertions

### Test Setup

```go
package actions

import (
    "context"
    "testing"

    "github.com/stretchr/testify/assert"
    "github.com/stretchr/testify/require"
    actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit"
)

func TestMyTransformAction(t *testing.T) {
    // Create a test context with an in-memory content store
    tc := actionkit.NewTestContext("test-did")

    // Store test content
    content := tc.Content("input.txt", "text/plain", "hello world")

    // Build the input
    input := actionkit.TransformInput{
        ActionContext: tc.ActionContext,
        Content:       []actionkit.ActionContent{content},
        Metadata:      map[string]string{"key": "value"},
        Params:        map[string]any{},
    }

    // Execute the action and get the result event
    event := actionkit.RunTransform(t, &MyTransformAction{}, input)

    // Assert it's a transform result
    te := event.RequireTransform(t)

    // Verify the output content
    require.Len(t, te[0].Content, 1)
    data, err := te[0].Content[0].LoadString(context.Background(), tc.Store)
    require.NoError(t, err)
    assert.Contains(t, data, "hello world")
    assert.Contains(t, data, "transformed")

    // Verify metadata and annotations
    assert.Equal(t, "transformValue", te[0].Metadata["transformKey"])
}
```

### Result Verification

The `ActionEvent` returned by the test runners provides assertion methods for each result type:

```go
func TestFilterResult(t *testing.T) {
    tc := actionkit.NewTestContext("2-starts-with-two")
    input := actionkit.TransformInput{
        ActionContext: tc.ActionContext,
        Content:       []actionkit.ActionContent{tc.Content("in.txt", "text/plain", "data")},
        Metadata:      map[string]string{},
        Params:        map[string]any{},
    }

    event := actionkit.RunTransform(t, &MyTransformAction{}, input)
    event.RequireFilter(t, "We prefer dids that do not start with 2")
}

func TestErrorResult(t *testing.T) {
    tc := actionkit.NewTestContext("test-did")
    input := actionkit.TransformInput{
        ActionContext: tc.ActionContext,
        Content:       nil, // no content
        Metadata:      map[string]string{},
        Params:        map[string]any{},
    }

    event := actionkit.RunTransform(t, &MyTransformAction{}, input)
    event.RequireError(t, "No content provided")
}
```

### Mock Lookup Client

```go
func TestWithLookup(t *testing.T) {
    tc := actionkit.NewTestContext("test-did")
    tc.LookupClient = &actionkit.MockLookupClient{
        Results: map[string]*actionkit.LookupResults{
            "my_table": {
                TotalCount: 2,
                Rows: []map[string]string{
                    {"name": "Alice"},
                    {"name": "Bob"},
                },
            },
        },
    }

    input := actionkit.TransformInput{
        ActionContext: tc.ActionContext,
        Content:       []actionkit.ActionContent{tc.Content("in.txt", "text/plain", "data")},
        Metadata:      map[string]string{},
        Params:        map[string]any{},
    }

    event := actionkit.RunTransform(t, &MyTransformAction{}, input)
    te := event.RequireTransform(t)
    assert.NotEmpty(t, te[0].Metadata["lookupResult"])
}
```
