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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class LemonSqueezyClient {

    public static final String PROVIDER = "LEMON_SQUEEZY";
    private static final BigDecimal LARGE_LOCAL_PRICE_THRESHOLD = new BigDecimal("1000");

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

    public long toProviderMinorUnits(BigDecimal amount) {
        return customPriceMinorUnits(amount);
    }

    public Optional<LemonSqueezyOrder> retrieveOrder(String orderId) {
        if (!StringUtils.hasText(orderId)) {
            return Optional.empty();
        }
        assertConfigured();

        try {
            String response = RestClient.builder()
                    .baseUrl(properties.getApiBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                    .defaultHeader("Accept", "application/vnd.api+json")
                    .defaultHeader("Content-Type", "application/vnd.api+json")
                    .build()
                    .get()
                    .uri("/v1/orders/{orderId}", orderId)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            return Optional.of(parseOrder(root.path("data")));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw providerException(exception);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Failed to verify Lemon Squeezy order state.");
        }
    }

    public List<LemonSqueezyOrder> listOrdersByUserEmail(String userEmail) {
        if (!StringUtils.hasText(userEmail)) {
            return List.of();
        }
        assertConfigured();

        try {
            return parseOrders(RestClient.builder()
                    .baseUrl(properties.getApiBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                    .defaultHeader("Accept", "application/vnd.api+json")
                    .defaultHeader("Content-Type", "application/vnd.api+json")
                    .build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/orders")
                            .queryParam("filter[user_email]", userEmail)
                            .queryParam("page[size]", 100)
                            .build())
                    .retrieve()
                    .body(String.class));
        } catch (RestClientResponseException exception) {
            throw providerException(exception);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Failed to verify Lemon Squeezy order state.");
        }
    }

    public List<LemonSqueezyOrder> listRecentOrders() {
        assertConfigured();

        try {
            return parseOrders(RestClient.builder()
                    .baseUrl(properties.getApiBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                    .defaultHeader("Accept", "application/vnd.api+json")
                    .defaultHeader("Content-Type", "application/vnd.api+json")
                    .build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/orders")
                            .queryParam("page[size]", 100)
                            .build())
                    .retrieve()
                    .body(String.class));
        } catch (RestClientResponseException exception) {
            throw providerException(exception);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Failed to verify Lemon Squeezy order state.");
        }
    }

    private List<LemonSqueezyOrder> parseOrders(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return List.of();
        }

        List<LemonSqueezyOrder> orders = new ArrayList<>();
        data.forEach(item -> orders.add(parseOrder(item)));
        return orders;
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
        BigDecimal price = amount == null ? BigDecimal.ZERO : amount;

        if (price.compareTo(LARGE_LOCAL_PRICE_THRESHOLD) >= 0) {
            return price.movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
        }

        BigDecimal exchangeRate = properties.getCustomPriceExchangeRate();
        if (exchangeRate != null && exchangeRate.compareTo(BigDecimal.ONE) > 0) {
            return price.multiply(exchangeRate)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
        }

        return price.movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .intValueExact();
    }

    private LemonSqueezyOrder parseOrder(JsonNode data) {
        JsonNode attributes = data.path("attributes");
        JsonNode firstOrderItem = attributes.path("first_order_item");

        return new LemonSqueezyOrder(
                data.path("id").asText(null),
                attributes.path("identifier").asText(null),
                attributes.path("status").asText(""),
                attributes.path("refunded").asBoolean(false),
                attributes.path("currency").asText(null),
                attributes.path("total").asLong(0L),
                attributes.path("total_usd").asLong(0L),
                String.valueOf(firstOrderItem.path("variant_id").asText("")),
                attributes.path("test_mode").asBoolean(false),
                Instant.parse(attributes.path("created_at").asText())
        );
    }

    private BusinessException providerException(RestClientResponseException exception) {
        String responseBody = exception.getResponseBodyAsString();
        String detail = StringUtils.hasText(responseBody) ? " " + responseBody : "";
        return new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "Failed to verify Lemon Squeezy order state. Provider responded with "
                        + exception.getStatusCode().value()
                        + "."
                        + detail
        );
    }

    private void assertConfigured() {
        if (!StringUtils.hasText(properties.getApiKey())
                || !StringUtils.hasText(properties.getStoreId())
                || !StringUtils.hasText(properties.getVariantId())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Lemon Squeezy is enabled but configuration is incomplete. Provide LEMON_SQUEEZY_API_KEY, LEMON_SQUEEZY_STORE_ID, and LEMON_SQUEEZY_VARIANT_ID."
            );
        }
    }

    public record LemonSqueezyOrder(
            String id,
            String identifier,
            String status,
            boolean refunded,
            String currency,
            long total,
            long totalUsd,
            String variantId,
            boolean testMode,
            Instant createdAt
    ) {
    }
}
