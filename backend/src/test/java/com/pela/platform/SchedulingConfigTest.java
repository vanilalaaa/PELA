package com.pela.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.pela.identity.adapter.in.job.PasswordResetCleanupJob;
import com.pela.identity.adapter.in.job.SuspensionReleaseJob;
import com.pela.identity.application.port.in.PasswordRecoveryUseCase;
import com.pela.identity.application.port.in.ReleaseExpiredSuspensionUseCase;
import com.pela.platform.config.SchedulingConfig;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.Task;

/** Lịch job đọc từ cấu hình pela.jobs.* (application.yml / application-dev.yml), không cần DB. */
class SchedulingConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(SchedulingConfig.class))
            .withBean(ReleaseExpiredSuspensionUseCase.class, () -> mock(ReleaseExpiredSuspensionUseCase.class))
            .withBean(PasswordRecoveryUseCase.class, () -> mock(PasswordRecoveryUseCase.class))
            .withBean(SuspensionReleaseJob.class)
            .withBean(PasswordResetCleanupJob.class);

    private static final List<Class<?>> JOBS = List.of(SuspensionReleaseJob.class, PasswordResetCleanupJob.class);

    /**
     * Task đã đăng ký, khoá theo class chứa phương thức @Scheduled. Spring 6.2 bọc runnable
     * (OutcomeTrackingRunnable) nhưng toString() vẫn là tên đầy đủ "Class.method".
     */
    private static Map<Class<?>, Task> tasksByJob(ApplicationContext context) {
        return context.getBeansOfType(ScheduledTaskHolder.class).values().stream()
                .flatMap(holder -> holder.getScheduledTasks().stream())
                .map(ScheduledTask::getTask)
                .collect(Collectors.toMap(SchedulingConfigTest::jobOf, Function.identity()));
    }

    private static Class<?> jobOf(Task task) {
        String method = task.getRunnable().toString();
        return JOBS.stream()
                .filter(job -> method.startsWith(job.getName() + "."))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Unexpected scheduled task: " + method));
    }

    @Test
    void registersBothJobsWithConfiguredSchedules() {
        runner.withPropertyValues(
                        "pela.jobs.suspension-release-cron=0 0 * * * *",
                        "pela.jobs.password-reset-cleanup-delay=PT1M")
                .run(context -> {
                    Map<Class<?>, Task> tasks = tasksByJob(context);

                    assertThat(tasks.keySet()).isEqualTo(Set.of(SuspensionReleaseJob.class, PasswordResetCleanupJob.class));
                    assertThat(tasks.get(SuspensionReleaseJob.class)).isInstanceOfSatisfying(CronTask.class,
                            t -> assertThat(t.getExpression()).isEqualTo("0 0 * * * *"));
                    assertThat(tasks.get(PasswordResetCleanupJob.class)).isInstanceOfSatisfying(FixedDelayTask.class,
                            t -> assertThat(t.getIntervalDuration()).isEqualTo(Duration.ofMinutes(1)));
                });
    }

    @Test
    void schedulesCanBeOverriddenByProperties() {
        // Như application-dev.yml: chạy dày hơn khi dev
        runner.withPropertyValues(
                        "pela.jobs.suspension-release-cron=0 * * * * *",
                        "pela.jobs.password-reset-cleanup-delay=PT15S")
                .run(context -> {
                    Map<Class<?>, Task> tasks = tasksByJob(context);

                    assertThat(((CronTask) tasks.get(SuspensionReleaseJob.class)).getExpression()).isEqualTo("0 * * * * *");
                    assertThat(((FixedDelayTask) tasks.get(PasswordResetCleanupJob.class)).getIntervalDuration())
                            .isEqualTo(Duration.ofSeconds(15));
                });
    }

    @Test
    void defaultsMatchApplicationYmlWhenPropertiesAreMissing() {
        runner.run(context -> {
            Map<Class<?>, Task> tasks = tasksByJob(context);

            assertThat(((CronTask) tasks.get(SuspensionReleaseJob.class)).getExpression()).isEqualTo("0 0 * * * *");
            assertThat(((FixedDelayTask) tasks.get(PasswordResetCleanupJob.class)).getIntervalDuration())
                    .isEqualTo(Duration.ofMinutes(1));
        });
    }

    /** Đọc đúng file cấu hình của app (test classpath có application.yml riêng nên đọc từ src/main/resources). */
    private static String[] jobProperties(String... files) throws Exception {
        var loader = new YamlPropertySourceLoader();
        Map<String, String> merged = new java.util.LinkedHashMap<>();
        for (String file : files) {
            for (PropertySource<?> source : loader.load(file, new FileSystemResource("src/main/resources/" + file))) {
                for (String key : new String[] {"pela.jobs.suspension-release-cron", "pela.jobs.password-reset-cleanup-delay"}) {
                    Object value = source.getProperty(key);
                    if (value != null) {
                        merged.put(key, value.toString());
                    }
                }
            }
        }
        return merged.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toArray(String[]::new);
    }

    @Test
    void applicationYmlSchedules() throws Exception {
        runner.withPropertyValues(jobProperties("application.yml")).run(context -> {
            Map<Class<?>, Task> tasks = tasksByJob(context);

            assertThat(((CronTask) tasks.get(SuspensionReleaseJob.class)).getExpression()).isEqualTo("0 0 * * * *");
            assertThat(((FixedDelayTask) tasks.get(PasswordResetCleanupJob.class)).getIntervalDuration())
                    .isEqualTo(Duration.ofMinutes(1));
        });
    }

    @Test
    void devProfileRunsJobsMoreOften() throws Exception {
        runner.withPropertyValues(jobProperties("application.yml", "application-dev.yml")).run(context -> {
            Map<Class<?>, Task> tasks = tasksByJob(context);

            assertThat(((CronTask) tasks.get(SuspensionReleaseJob.class)).getExpression()).isEqualTo("0 * * * * *");
            assertThat(((FixedDelayTask) tasks.get(PasswordResetCleanupJob.class)).getIntervalDuration())
                    .isEqualTo(Duration.ofSeconds(15));
        });
    }

    @Test
    void jobsEnabledFalseRegistersNothing() {
        runner.withPropertyValues("pela.jobs.enabled=false")
                .run(context -> assertThat(tasksByJob(context)).isEmpty());
    }
}
