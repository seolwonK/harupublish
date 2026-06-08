package com.haru.common.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight liveness probe (no DB, no auth) for the platform health check.
 * Lets prod disable springdoc/swagger without losing a health endpoint.
 */
@RestController
public class HealthController {

    @GetMapping("/healthz")
    public String healthz() {
        return "ok";
    }
}
