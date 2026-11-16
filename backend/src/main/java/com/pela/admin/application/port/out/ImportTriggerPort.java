package com.pela.admin.application.port.out;

import java.util.UUID;

/** Kích hoạt xử lý nền ngay sau khi tải file, để Admin không phải chờ job theo lịch. */
public interface ImportTriggerPort {

    void processSoon(UUID jobId);
}
