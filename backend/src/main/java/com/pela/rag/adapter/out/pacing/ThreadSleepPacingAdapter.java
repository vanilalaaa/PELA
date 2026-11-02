package com.pela.rag.adapter.out.pacing;

import com.pela.rag.application.port.out.PacingPort;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class ThreadSleepPacingAdapter implements PacingPort {

    @Override
    public void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
