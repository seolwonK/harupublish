package com.haru.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's {@code @Scheduled} support so settlement and (future) credit
 * expiry jobs run. Kept on a dedicated config so it can be excluded from web
 * slice tests that don't need background jobs.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
