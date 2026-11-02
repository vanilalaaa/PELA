package com.pela.writing.adapter.out.event;

import com.pela.writing.application.port.out.WritingGradedEventPort;
import org.springframework.stereotype.Component;

/** Chưa có module gamification: bỏ qua sự kiện (xem docs/backlog.md, mục Writing). */
@Component
public class NoOpWritingGradedEventAdapter implements WritingGradedEventPort {

    @Override
    public void writingGraded(WritingGraded event) {
        // no-op
    }
}
