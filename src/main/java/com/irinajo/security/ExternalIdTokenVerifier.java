package com.irinajo.security;

import com.irinajo.common.ApiException;
import com.irinajo.config.AppProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Verifies Google ID tokens and Apple identity tokens against the providers' published keys,
 * checking signature, expiry, issuer and audience (our client IDs).
 */
@Component
public class ExternalIdTokenVerifier {

    public record Identity(String subject, String email) {}

    private final NimbusJwtDecoder google;
    private final NimbusJwtDecoder apple;
    private final List<String> googleIds;
    private final List<String> appleIds;

    public ExternalIdTokenVerifier(AppProperties props) {
        this.googleIds = props.google().ids();
        this.appleIds = props.apple().ids();

        this.google = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
        this.google.setJwtValidator(validator(Set.of("https://accounts.google.com", "accounts.google.com"), googleIds));

        this.apple = NimbusJwtDecoder.withJwkSetUri("https://appleid.apple.com/auth/keys").build();
        this.apple.setJwtValidator(validator(Set.of("https://appleid.apple.com"), appleIds));
    }

    public Identity verifyGoogle(String idToken) {
        if (googleIds.isEmpty()) throw ApiException.badRequest("GOOGLE_NOT_CONFIGURED", "Google sign-in is not configured");
        Jwt jwt = decode(google, idToken, "GOOGLE_TOKEN_INVALID");
        Object verified = jwt.getClaim("email_verified");
        String email = Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified)) ? jwt.getClaimAsString("email") : null;
        return new Identity(jwt.getSubject(), email);
    }

    public Identity verifyApple(String identityToken) {
        if (appleIds.isEmpty()) throw ApiException.badRequest("APPLE_NOT_CONFIGURED", "Apple sign-in is not configured");
        Jwt jwt = decode(apple, identityToken, "APPLE_TOKEN_INVALID");
        return new Identity(jwt.getSubject(), jwt.getClaimAsString("email"));
    }

    private Jwt decode(NimbusJwtDecoder decoder, String token, String code) {
        try {
            return decoder.decode(token);
        } catch (JwtException e) {
            throw ApiException.unauthorized(code, "Sign-in token could not be verified");
        }
    }

    private static OAuth2TokenValidator<Jwt> validator(Set<String> issuers, List<String> audiences) {
        OAuth2TokenValidator<Jwt> iss = jwt -> issuers.contains(jwt.getClaimAsString("iss"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Bad issuer", null));
        OAuth2TokenValidator<Jwt> aud = jwt -> jwt.getAudience() != null && jwt.getAudience().stream().anyMatch(audiences::contains)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Bad audience", null));
        return new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), iss, aud);
    }
}
