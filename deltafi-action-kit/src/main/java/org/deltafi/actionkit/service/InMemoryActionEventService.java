package org.deltafi.actionkit.service;

import io.quarkus.arc.profile.UnlessBuildProfile;
import org.deltafi.actionkit.action.Result;
import org.deltafi.core.domain.api.types.ActionInput;
import org.deltafi.core.domain.generated.types.ActionEventInput;

import javax.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

@UnlessBuildProfile("prod")
@ApplicationScoped
public class InMemoryActionEventService implements ActionEventService {

    private final ConcurrentMap<String, LinkedBlockingQueue<ActionInput>> incoming = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LinkedBlockingQueue<ActionEventInput>> outgoing = new ConcurrentHashMap<>();

    public void putAction(String actionClassName, ActionInput actionInput) {
        incoming.computeIfAbsent(actionClassName, k -> new LinkedBlockingQueue<>());
        incoming.get(actionClassName).add(actionInput);
    }

    public ActionInput getAction(String actionClassName) throws InterruptedException {
        incoming.computeIfAbsent(actionClassName, k -> new LinkedBlockingQueue<>());
        return incoming.get(actionClassName).take();
    }

    public void clearIncoming() {
        incoming.clear();
    }

    public void submitResult(Result result) {
        outgoing.computeIfAbsent(result.toEvent().getAction(), k -> new LinkedBlockingQueue<>());
        outgoing.get(result.toEvent().getAction()).add(result.toEvent());
    }

    public List<ActionEventInput> getResults(String actionClassName) {
        List<ActionEventInput> results = new ArrayList<>();
        outgoing.computeIfAbsent(actionClassName, k -> new LinkedBlockingQueue<>());
        outgoing.get(actionClassName).drainTo(results);
        return results;
    }

    public void clearOutgoing() {
        outgoing.clear();
    }
}