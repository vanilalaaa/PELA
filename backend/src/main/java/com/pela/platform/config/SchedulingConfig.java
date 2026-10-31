package com.pela.platform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bật các job @Scheduled (adapter/in/job). Tắt bằng pela.jobs.enabled=false (vd khi test). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "pela.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
