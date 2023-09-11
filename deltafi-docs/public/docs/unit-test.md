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

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.common.types.ActionContext;
import org.deltafi.helloworld.parameters.HelloWorldTransformParameters;
import org.deltafi.test.asserters.ActionResultAssertions;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.deltafi.test.content.loader.ContentLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class HelloWorldTransformActionTest {

    // create the action to test
    HelloWorldTransformAction helloWorldTransformAction = new HelloWorldTransformAction();
    
    // prepare the action for testing (injects the ContentStorageService into the action)
    DeltaFiTestRunner deltaFiTestRunner = DeltaFiTestRunner.setup(helloWorldTransformAction);
    
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

The `org.deltafi.test.asserters.ActionResultAssertions` class provides the main entry points used to verify the results from executing an `Action`. Each result type has a set of predefined assertions that can be used to validate the results.

The following code shows sample usage of a subset of the assertions.


```java
package org.deltafi.helloworld.actions;

import org.deltafi.actionkit.action.ResultType;
import org.deltafi.actionkit.action.content.ActionContent;
import org.deltafi.actionkit.action.transform.TransformInput;
import org.deltafi.common.types.ActionContext;
import org.deltafi.helloworld.parameters.HelloWorldTransformParameters;
import org.deltafi.test.asserters.ActionResultAssertions;
import org.deltafi.test.content.DeltaFiTestRunner;
import org.deltafi.test.content.loader.ContentLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class HelloWorldTransformActionTest {

    HelloWorldTransformAction helloWorldTransformAction = new HelloWorldTransformAction();
    
    @Test
    void simpleTest() {
        ResultType resultType = helloWorldTransformAction.transform(new ActionContext(), new HelloWorldTransformParameters(), TransformInput.builder().build());

        // expect a transform result and verify all the parts
        ActionResultAssertions.assertTransformResult(resultType)
                .contentLoadStringEquals(List.of("expected this content to be saved"))
                .addedMetadata("new-key", "value")
                .deletedKeyEquals("deleted-key")
                .hasMetric("some-metric", 1, Map.of("tag", "tag-value"));

        // expect a filter result with an exact cause
        ActionResultAssertions.assertFilterResult(resultType)
                .hasCause("filtered reason");

        // expect an error result with a cause that matches the regex
        ActionResultAssertions.assertErrorResult(resultType)
                .hasCauseLike(".*errored reason substring.*");

        // expect a load result with the given domain
        ActionResultAssertions.assertLoadResult(resultType)
                .hasDomain("domain", "value", "application/xml");

        // expect a domain result with annotations added
        ActionResultAssertions.assertDomainResult(resultType)
                .addedAnnotation("key", "value");

        // expect an enrich result with the given enrichment
        ActionResultAssertions.assertEnrichResult(resultType)
                .hasEnrichment("name", "value", "application/json");

        // expect a format result with the given formatted string
        ActionResultAssertions.assertFormatResult(resultType)
                .formattedContentEquals("some formatted data");

        // expect a format result with the given name, formatted string, and mediaType
        ActionResultAssertions.assertFormatResult(resultType)
                .hasFormattedContent("file1", "input 1", "text/plain");

        // expect a validate result
        ActionResultAssertions.assertValidateResult(resultType);

        // expect an egress result
        ActionResultAssertions.assertEgressResult(resultType);

        // Get the actual result to do advanced verification
        LoadResult actualResult = ActionResultAssertions.assertLoadResult(resultType).getResult();
    }
}
```
