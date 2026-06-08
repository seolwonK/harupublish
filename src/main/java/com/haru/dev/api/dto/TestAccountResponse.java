package com.haru.dev.api.dto;

/**
 * A provisioned test account. The plaintext {@code password} is returned so the
 * developer can log in as this account — dev tooling only.
 */
public record TestAccountResponse(
        Long userId,
        String email,
        String password,
        String name,
        String activeRole
) {
}
