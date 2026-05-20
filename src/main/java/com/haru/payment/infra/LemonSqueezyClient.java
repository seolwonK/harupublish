package com.haru.payment.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import com.haru.payment.domain.Payment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Component
public class LemonSqueezyClient {

    public static final String PROVIDER = "LEMON_SQUEEZY";

    private final LemonSqueezyProperties properties;
    private final ObjectMapper objectMapper;

    public LemonSqueezyClient(LemonSqueezyProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public LemonSqueezyCheckout createCheckout(Payment payment) {
        assertConfigured();

        Map<String, Object> body = Map.of(
                "data", Map.of(
                        "type", "checkouts",
                        "attributes", Map.of(
                                "custom_price", customPriceMinorUnits(payment.getTotalAmount()),
                                "product_options", productOptions(payment),
                                "checkout_options", checkoutOptions(),
                                "checkout_data", checkoutData(payment),
                                "preview", true,
                                "test_mode", properties.isTestMode()
                        ),
                        "relationships", Map.of(
                                "store", relationship("stores", properties.getStoreId()),
                                "variant", relationship("variants", properties.getVariantId())
                        )
                )
        );

        try {
            String response = RestClient.builder()
                    .baseUrl(properties.getApiBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                    .defaultHeader("Accept", "application/vnd.api+json")
                    .defaultHeader("Content-Type", "application/vnd.api+json")
                    .build()
                    .post()
                    .uri("/v1/checkouts")
                    .contentType(MediaType.valueOf("application/vnd.api+json"))
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");
            String id = data.path("id").asText(null);
            String url = data.path("attributes").path("url").asText(null);
            if (!StringUtils.hasText(id) || !StringUtils.hasText(url)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Lemon Squeezy checkout response did not include a checkout URL.");
            }
            return new LemonSqueezyCheckout(id, url);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            String responseBody = exception.getResponseBodyAsString();
            String detail = StringUtils.hasText(responseBody) ? " " + responseBody : "";
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Failed to create Lemon Squeezy checkout. Provider responded with "
                            + exception.getStatusCode().value()
                            + "."
                            + detail
            );
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Failed to create Lemon Squeezy checkout.");
        }
    }

    private Map<String, Object> productOptions(Payment payment) {
        String lessonName = "%d min Korean lesson x %d".formatted(payment.getLessonDurationMinutes(), payment.getLessonPackCount());
        return Map.of(
                "name", "Haru " + lessonName,
                "description", "Haru 1:1 Korean tutoring lesson pack",
                "redirect_url", StringUtils.hasText(properties.getRedirectUrl()) ? properties.getRedirectUrl() : "",
                "enabled_variants", List.of(Integer.valueOf(properties.getVariantId()))
        );
    }

    private Map<String, Object> checkoutOptions() {
        return Map.of(
                "embed", false,
                "media", false,
                "logo", true,
                "desc", true,
                "discount", true,
                "button_color", "#ff8a3d",
                "button_text_color", "#ffffff",
                "locale", "ko"
        );
    }

    private Map<String, Object> checkoutData(Payment payment) {
        return Map.of(
                "email", payment.getStudent().getEmail(),
                "name", payment.getStudent().getName(),
                "custom", Map.of(
                        "payment_id", String.valueOf(payment.getId()),
                        "student_user_id", String.valueOf(payment.getStudent().getId()),
                        "tutor_profile_id", String.valueOf(payment.getTutorProfile().getId()),
                        "lesson_duration_minutes", String.valueOf(payment.getLessonDurationMinutes()),
                        "lesson_pack_count", String.valueOf(payment.getLessonPackCount())
                )
        );
    }

    private Map<String, Object> relationship(String type, String id) {
        return Map.of("data", Map.of("type", type, "id", id));
    }

    private int customPriceMinorUnits(BigDecimal amount) {
        return amount.multiply(properties.getCustomPriceExchangeRate())
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    private void assertConfigured() {
        if (!StringUtils.hasText(properties.getApiKey())
                || !StringUtils.hasText(properties.getStoreId())
                || !StringUtils.hasText(properties.getVariantId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Lemon Squeezy is enabled but API key, store ID, or variant ID is missing.");
        }
    }
}
