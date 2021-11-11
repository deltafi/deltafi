package org.deltafi.common.test.time;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper=false)
public class TestClock extends Clock {
    private Instant instant = Instant.now();
    private ZoneId zone = ZoneId.systemDefault();

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new TestClock(instant, zone);
    }
}
