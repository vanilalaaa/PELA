package com.pela.rag.application.port.out;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Thông tin learner cần cho UC-24: sở thích (RAG.Embed.Input) và múi giờ (tính "ngày" cho giới hạn mỗi ngày). */
public interface LearnerContextPort {

    LearnerContext context(UUID userId);

    record LearnerContext(List<String> interests, ZoneId zone) {
    }
}
