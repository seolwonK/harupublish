package com.haru.payment.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "haru.payments.lemon-squeezy")
public class LemonSqueezyProperties {

    private boolean enabled;
    private String apiKey;
    private String storeId;
    private String variantId;
    private String signingSecret;
    private String redirectUrl;
    private String apiBaseUrl = "https://api.lemonsqueezy.com";
    private boolean testMode = true;
    private BigDecimal customPriceExchangeRate = BigDecimal.ONE;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getVariantId() {
        return variantId;
    }

    public void setVariantId(String variantId) {
        this.variantId = variantId;
    }

    public String getSigningSecret() {
        return signingSecret;
    }

    public void setSigningSecret(String signingSecret) {
        this.signingSecret = signingSecret;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public boolean isTestMode() {
        return testMode;
    }

    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }

    public BigDecimal getCustomPriceExchangeRate() {
        return customPriceExchangeRate;
    }

    public void setCustomPriceExchangeRate(BigDecimal customPriceExchangeRate) {
        this.customPriceExchangeRate = customPriceExchangeRate == null ? BigDecimal.ONE : customPriceExchangeRate;
    }
}
