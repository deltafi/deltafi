package org.deltafi.dgs.services;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class FeedStats {
    private final static int MAX_TIMES = 10;

    final String actionName;
    final Queue<OffsetDateTime> recentQueryTimes;
    final AtomicInteger numQueries;

    public FeedStats(String actionName) {
        this.actionName = actionName;
        recentQueryTimes = new ConcurrentLinkedQueue<>();
        numQueries = new AtomicInteger(0);
    }

    public void addQuery() {
        recentQueryTimes.add(OffsetDateTime.now());
        while (recentQueryTimes.size() > MAX_TIMES) {
            recentQueryTimes.remove();
        }
        numQueries.getAndIncrement();
    }

    public String getActionName() { return actionName; }
    public List<OffsetDateTime> getRecentQueryTimes() { return new ArrayList<>(recentQueryTimes); }
    public Integer getNumQueries() { return numQueries.get(); }
}
