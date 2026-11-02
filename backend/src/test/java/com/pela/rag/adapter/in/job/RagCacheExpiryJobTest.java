package com.pela.rag.adapter.in.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.pela.platform.config.SchedulingConfig;
import com.pela.rag.application.port.in.ExpireRagCacheUseCase;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

class RagCacheExpiryJobTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(SchedulingConfig.class))
            .withBean(ExpireRagCacheUseCase.class, () -> mock(ExpireRagCacheUseCase.class))
            .withBean(RagCacheExpiryJob.class);

    @Test
    void runExpiresDueEntries() {
        ExpireRagCacheUseCase cache = mock(ExpireRagCacheUseCase.class);

        new RagCacheExpiryJob(cache).run();

        verify(cache).expireDue();
    }

    @Test
    void runsDailyAt0330ByDefaultAndFromApplicationYml() throws Exception {
        runner.run(context -> assertThat(cronOf(context.getBeansOfType(ScheduledTaskHolder.class).values().stream()
                .flatMap(holder -> holder.getScheduledTasks().stream()).toList())).containsExactly("0 30 3 * * *"));

        PropertySource<?> yml = new YamlPropertySourceLoader()
                .load("application.yml", new FileSystemResource("src/main/resources/application.yml")).get(0);
        assertThat(yml.getProperty("pela.jobs.rag-cache-expiry-cron")).isEqualTo("0 30 3 * * *");
        runner.withPropertyValues("pela.jobs.rag-cache-expiry-cron=0 * * * * *").run(context -> assertThat(
                cronOf(context.getBeansOfType(ScheduledTaskHolder.class).values().stream()
                        .flatMap(holder -> holder.getScheduledTasks().stream()).toList())).containsExactly("0 * * * * *"));
    }

    @Test
    void jobsEnabledFalseRegistersNothing() {
        runner.withPropertyValues("pela.jobs.enabled=false").run(context -> assertThat(
                context.getBeansOfType(ScheduledTaskHolder.class).values().stream()
                        .flatMap(holder -> holder.getScheduledTasks().stream()).toList()).isEmpty());
    }

    private static List<String> cronOf(List<ScheduledTask> tasks) {
        return tasks.stream().map(ScheduledTask::getTask).filter(CronTask.class::isInstance)
                .map(task -> ((CronTask) task).getExpression()).collect(Collectors.toList());
    }
}
