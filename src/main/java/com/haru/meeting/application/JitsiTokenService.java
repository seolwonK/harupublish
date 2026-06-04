package com.haru.meeting.application;

import com.haru.booking.domain.Booking;
import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import com.haru.meeting.infra.JitsiProperties;
import com.haru.user.domain.UserAccount;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JitsiTokenService {

    public static final String PROVIDER = "JITSI_JAAS";

    private final JitsiProperties properties;

    public JitsiTokenService(JitsiProperties properties) {
        this.properties = properties;
    }

    public JitsiJoinPayload createJoinPayload(Booking booking, UserAccount user, boolean moderator, Instant now) {
        validateBasicConfigured();
        if (booking.getJitsiRoomName() == null || booking.getJitsiRoomName().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Jitsi room is not assigned to this booking.");
        }

        Instant expiresAt = now.plus(Duration.ofMinutes(properties.getTokenTtlMinutes()));
        String fullRoomName = properties.getAppId() + "/" + booking.getJitsiRoomName();
        String token = hasJwtConfiguration()
                ? Jwts.builder()
                        .header()
                        .keyId(properties.getKeyId())
                        .type("JWT")
                        .and()
                        .audience()
                        .single("jitsi")
                        .issuer("chat")
                        .subject(properties.getAppId())
                        .issuedAt(Date.from(now))
                        .notBefore(Date.from(now.minusSeconds(30)))
                        .expiration(Date.from(expiresAt))
                        .claim("room", booking.getJitsiRoomName())
                        .claim("context", context(user, moderator))
                        .signWith(loadPrivateKey(properties.getPrivateKeyPem()), Jwts.SIG.RS256)
                        .compact()
                : null;

        return new JitsiJoinPayload(
                PROVIDER,
                properties.getDomain(),
                fullRoomName,
                token,
                "https://" + properties.getDomain() + "/" + fullRoomName + (token == null ? "" : "?jwt=" + token),
                expiresAt
        );
    }

    private void validateBasicConfigured() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Jitsi integration is not enabled.");
        }
        if (isBlank(properties.getAppId()) || properties.getAppId().startsWith("PASTE_")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Jitsi integration is not configured.");
        }
    }

    private boolean hasJwtConfiguration() {
        return !isBlank(properties.getKeyId())
                && !isBlank(properties.getPrivateKeyPem())
                && !properties.getKeyId().startsWith("PASTE_");
    }

    private Map<String, Object> context(UserAccount user, boolean moderator) {
        Map<String, Object> userContext = new LinkedHashMap<>();
        userContext.put("id", String.valueOf(user.getId()));
        userContext.put("name", user.getName());
        userContext.put("email", user.getEmail());
        userContext.put("moderator", moderator);

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("livestreaming", false);
        features.put("recording", false);
        features.put("transcription", false);
        features.put("outbound-call", false);

        Map<String, Object> room = new LinkedHashMap<>();
        room.put("regex", false);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("user", userContext);
        context.put("features", features);
        context.put("room", room);
        return context;
    }

    private PrivateKey loadPrivateKey(String pem) {
        try {
            if (pem.contains("BEGIN RSA PRIVATE KEY")) {
                return loadPkcs1PrivateKey(pem);
            }
            String normalized = pem.replace("\\n", "\n")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Jitsi private key is invalid.");
        }
    }

    private PrivateKey loadPkcs1PrivateKey(String pem) throws Exception {
        String normalized = pem.replace("\\n", "\n")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        DerReader reader = new DerReader(Base64.getDecoder().decode(normalized));
        reader.readSequence();
        reader.readInteger();
        BigInteger modulus = reader.readInteger();
        BigInteger publicExponent = reader.readInteger();
        BigInteger privateExponent = reader.readInteger();
        BigInteger primeP = reader.readInteger();
        BigInteger primeQ = reader.readInteger();
        BigInteger primeExponentP = reader.readInteger();
        BigInteger primeExponentQ = reader.readInteger();
        BigInteger crtCoefficient = reader.readInteger();
        RSAPrivateCrtKeySpec keySpec = new RSAPrivateCrtKeySpec(
                modulus,
                publicExponent,
                privateExponent,
                primeP,
                primeQ,
                primeExponentP,
                primeExponentQ,
                crtCoefficient
        );
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class DerReader {
        private final byte[] bytes;
        private int offset;

        private DerReader(byte[] bytes) {
            this.bytes = bytes;
        }

        private void readSequence() {
            readTag(0x30);
            readLength();
        }

        private BigInteger readInteger() {
            readTag(0x02);
            int length = readLength();
            byte[] value = new byte[length];
            System.arraycopy(bytes, offset, value, 0, length);
            offset += length;
            return new BigInteger(1, value);
        }

        private void readTag(int expectedTag) {
            if (offset >= bytes.length || (bytes[offset++] & 0xff) != expectedTag) {
                throw new IllegalArgumentException("Unexpected DER tag.");
            }
        }

        private int readLength() {
            int first = bytes[offset++] & 0xff;
            if ((first & 0x80) == 0) {
                return first;
            }
            int byteCount = first & 0x7f;
            int length = 0;
            for (int index = 0; index < byteCount; index++) {
                length = (length << 8) + (bytes[offset++] & 0xff);
            }
            return length;
        }
    }
}
