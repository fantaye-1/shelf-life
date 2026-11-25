package com.shelflife.digest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the digest job on a timer, by default a few minutes past every hour (the job itself decides
 * which stores are due in their own time zone). Turned off with
 * {@code shelflife.digest.schedule-enabled=false}, which the test build does so tests drive the
 * job directly.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "shelflife.digest.schedule-enabled", havingValue = "true", matchIfMissing = true)
public class DigestScheduler {

    private final DailyDigestJob job;

    public DigestScheduler(DailyDigestJob job) {
        this.job = job;
    }

    @Scheduled(cron = "${shelflife.digest.cron:0 5 * * * *}", zone = "UTC")
    void run() {
        job.runDue();
    }
}
