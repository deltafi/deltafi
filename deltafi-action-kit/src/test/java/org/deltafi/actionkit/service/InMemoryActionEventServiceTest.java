package org.deltafi.actionkit.service;

import org.deltafi.actionkit.action.Result;
import org.deltafi.actionkit.action.egress.EgressResult;
import org.deltafi.actionkit.action.filter.FilterResult;
import org.deltafi.actionkit.action.transform.TransformResult;
import org.deltafi.core.domain.api.types.ActionInput;
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
        } );
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
        } );
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
        Result result = new EgressResult(actionClassName, "did");
        Result result2 = new FilterResult("anotherName", "did2", "message");
        Result result3 = new TransformResult(actionClassName, "did3");

        actionEventService.submitResult(result);
        actionEventService.submitResult(result2);
        actionEventService.submitResult(result3);

        List<ActionEventInput> results =  actionEventService.getResults(actionClassName);
        assertEquals(2, results.size());
        assertEquals("did", results.get(0).getDid());
        assertEquals("did3", results.get(1).getDid());
    }
}