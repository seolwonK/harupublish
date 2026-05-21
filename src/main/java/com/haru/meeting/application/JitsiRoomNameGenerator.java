package com.haru.meeting.application;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class JitsiRoomNameGenerator {

    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private final SecureRandom secureRandom = new SecureRandom();

    public String createRoomName(Long bookingId) {
        return "haru-booking-%d-%s".formatted(bookingId, randomSuffix(12));
    }

    private String randomSuffix(int length) {
        StringBuilder value = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            value.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return value.toString();
    }
}
