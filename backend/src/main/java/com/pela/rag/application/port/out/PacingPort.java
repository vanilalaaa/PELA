package com.pela.rag.application.port.out;

import java.time.Duration;

/** Nghỉ giữa các lô gọi Gemini để tránh rate limit; test thay bằng bản không chờ. */
public interface PacingPort {

    void pause(Duration duration);
}
