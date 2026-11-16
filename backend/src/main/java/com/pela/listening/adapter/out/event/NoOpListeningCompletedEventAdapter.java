package com.pela.listening.adapter.out.event;

import com.pela.listening.application.port.out.ListeningCompletedEventPort;
import org.springframework.stereotype.Component;

/** Chưa có module gamification: bỏ qua sự kiện (giống Writing, xem docs/backlog.md). */
@Component
public class NoOpListeningCompletedEventAdapter implements ListeningCompletedEventPort {

    @Override
    public void listeningCompleted(ListeningCompleted event) {
        // no-op
    }
}
