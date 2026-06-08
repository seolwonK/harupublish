package com.haru.dev.api.dto;

/** Developer test-data request for a student account. All fields optional. */
public record CreateTestStudentRequest(
        String name,
        String email,
        String password
) {
}
