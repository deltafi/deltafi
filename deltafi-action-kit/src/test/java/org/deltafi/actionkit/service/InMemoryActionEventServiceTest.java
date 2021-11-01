package org.deltafi.actionkit.service;

import org.deltafi.actionkit.action.egress.EgressActionParameters;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.actionkit.action.parameters.ActionParameters;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.core.domain.api.types.ActionInput;
import org.deltafi.core.domain.api.types.DeltaFile;
import org.deltafi.core.domain.generated.types.ActionEventInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class InMemoryActionEventServiceTest {
    InMemoryActionEventService actionEventService = new InMemoryActionEventService();

    @BeforeEach
    void setup() {
        actionEventService.clearIncoming();
        actionEventService.clearOutgoing();
    }

    @Test
    void testIncoming() throws InterruptedException, ExecutionException, TimeoutException {
        String actionClassName = "SnackAction";
        ActionInput actionInput = new ActionInput();
        ActionInput actionInput2 = new ActionInput();

        actionEventService.putAction(actionClassName, actionInput);
        actionEventService.putAction(actionClassName, actionInput2);

        CompletableFuture.supplyAsync(() -> {
            try {
                assertEquals(actionInput, actionEventService.getAction(actionClassName));
                assertEquals(actionInput2, actionEventService.getAction(actionClassName));
            } catch (InterruptedException ignored) {}
            return null;
        }).get(500, TimeUnit.MILLISECONDS);
    }

    @Test
    void testIncomingBlocksEternally() throws InterruptedException {
        String actionClassName = "SnackAction";

        AtomicReference<ActionInput> actual = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                actual.set(actionEventService.getAction(actionClassName));
            } catch (InterruptedException ignored) {}
        });
        thread.start();
        thread.join(500);
        assertTrue(thread.isAlive());
        thread.interrupt();
    }

    @Test
    void testIncomingBlocksAndGets() throws InterruptedException {
        String actionClassName = "SnackAction";
        ActionInput actionInput = new ActionInput();

        AtomicReference<ActionInput> actual = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                actual.set(actionEventService.getAction(actionClassName));
            } catch (InterruptedException ignored) {}
        });
        thread.start();

        // give the other thread time to do its thing
        Thread.sleep(500);

        actionEventService.putAction(actionClassName, actionInput);

        thread.join(500);
        assertFalse(thread.isAlive());
        assertEquals(actionInput, actual.get());
    }

    @Test
    void testOutgoingEmpty() {
        assertEquals(Collections.emptyList(), actionEventService.getResults("whatever"));
    }

    @Test
    void testOutgoing() {
        String actionClassName = "vanillaWafers";
        EgressResult<EgressActionParameters> result = new EgressResult<>(DeltaFile.newBuilder().did("did").build(),
                new EgressActionParameters(actionClassName, null, null), "destination", 0);
        FilterResult result2 = new FilterResult(DeltaFile.newBuilder().did("did2").build(),
                new ActionParameters("anotherName", null), "message");
        TransformResult<ActionParameters> result3 = new TransformResult<>(DeltaFile.newBuilder().did("did3").build(),
                new ActionParameters(actionClassName, null), "type");

        actionEventService.submitResult(result);
        actionEventService.submitResult(result2);
        actionEventService.submitResult(result3);

        List<ActionEventInput> results = actionEventService.getResults(actionClassName);
        assertEquals(2, results.size());
        assertEquals("did", results.get(0).getDid());
        assertEquals("did3", results.get(1).getDid());
    }
}