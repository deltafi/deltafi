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
