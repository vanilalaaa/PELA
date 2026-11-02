package com.pela.writing.application.port.out;

import java.util.UUID;

/** Báo cho bộ chấm bất đồng bộ rằng có bài mới cần chấm (không chặn request). Job theo lịch vẫn là đường dự phòng. */
public interface GradingTriggerPort {

    void gradeSoon(UUID submissionId);
}
