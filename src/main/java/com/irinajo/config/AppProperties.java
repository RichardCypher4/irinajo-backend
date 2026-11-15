package com.irinajo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "irinajo")
public record AppProperties(
        String publicBaseUrl,
        boolean requireHttps,
        Jwt jwt,
        Otp otp,
        Sms sms,
        Whatsapp whatsapp,
        Google google,
        Apple apple,
        Storage storage,
        Geocoding geocoding,
        Maps maps,
        Routing routing,
        Weather weather
) {
    public record Jwt(String secret, String issuer, long accessTtlMinutes, long refreshTtlDays) {}

    public record Otp(String pepper, int ttlMinutes, int maxAttempts, int resendSeconds,
                      int perPhonePerHour, int perDevicePerHour, int perIpPerHour,
                      int alertThresholdPerHour, String alertWebhookUrl, String testNumbers) {
        /** Parses "+234800...:123456,+234800...:654321" into a map. */
        public Map<String, String> testNumberMap() {
            Map<String, String> map = new HashMap<>();
            if (testNumbers == null || testNumbers.isBlank()) return map;
            Arrays.stream(testNumbers.split(","))
                    .map(String::trim)
                    .filter(s -> s.contains(":"))
                    .forEach(s -> {
                        int i = s.lastIndexOf(':');
                        map.put(s.substring(0, i), s.substring(i + 1));
                    });
            return map;
        }
    }

    public record Sms(String provider, String termiiApiKey, String termiiSenderId, String termiiBaseUrl) {}

    public record Whatsapp(boolean enabled, String phoneNumberId, String accessToken, String templateName) {}

    public record Google(String clientIds) {
        public List<String> ids() { return split(clientIds); }
    }

    public record Apple(String clientIds) {
        public List<String> ids() { return split(clientIds); }
    }

    public record Storage(String endpoint, String region, String bucket, String accessKey,
                          String secretKey, String cdnBaseUrl) {
        public boolean configured() {
            return accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank();
        }
    }

    public record Geocoding(boolean enabled, String nominatimUrl, String overpassUrl, String userAgent) {}

    public record Maps(String mapboxToken) {}

    public record Routing(String graphhopperKey) {}

    public record Weather(String baseUrl, String apiKey) {}

    private static List<String> split(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }
}
