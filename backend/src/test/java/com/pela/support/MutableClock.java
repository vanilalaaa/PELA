package com.pela.support;

import com.pela.shared.application.port.out.ClockPort;
import java.time.Duration;
import java.time.Instant;

/** ClockPort cho test: thời gian đứng yên, tự tua bằng {@link #advance}. */
public class MutableClock implements ClockPort {

    private Instant now;

    public MutableClock(Instant start) {
        this.now = start;
    }

    @Override
    public Instant now() {
        return now;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }
}
