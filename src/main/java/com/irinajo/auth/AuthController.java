package com.irinajo.auth;

import com.irinajo.security.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    public record OtpRequest(@NotBlank String phone, String channel) {}
    public record OtpVerify(@NotBlank String phone, @NotBlank String code, @Size(max = 30) String name) {}
    public record GoogleLogin(@NotBlank String idToken) {}
    public record AppleLogin(@NotBlank String identityToken, String fullName) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record LogoutRequest(String refreshToken, Boolean everywhere) {}

    private final OtpService otp;
    private final AuthService auth;

    public AuthController(OtpService otp, AuthService auth) {
        this.otp = otp;
        this.auth = auth;
    }

    /** The app sends a stable install ID in X-Device-Id, used for per-device rate limits. */
    @PostMapping("/auth/otp/request")
    public OtpService.RequestResult request(@Valid @RequestBody OtpRequest body,
                                            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
                                            HttpServletRequest req) {
        return otp.request(body.phone(), body.channel(), deviceId, ClientIp.of(req));
    }

    @PostMapping("/auth/otp/verify")
    public AuthService.AuthResponse verify(@Valid @RequestBody OtpVerify body,
                                           @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        return auth.phone(body.phone(), body.code(), body.name(), deviceId);
    }

    @PostMapping("/auth/google")
    public AuthService.AuthResponse google(@Valid @RequestBody GoogleLogin body,
                                           @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        return auth.google(body.idToken(), deviceId);
    }

    @PostMapping("/auth/apple")
    public AuthService.AuthResponse apple(@Valid @RequestBody AppleLogin body,
                                          @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        return auth.apple(body.identityToken(), body.fullName(), deviceId);
    }

    @PostMapping("/auth/refresh")
    public AuthService.AuthResponse refresh(@Valid @RequestBody RefreshRequest body) {
        return auth.refresh(body.refreshToken());
    }

    /**
     * Revokes the refresh token (or, with everywhere=true, every session of that account).
     * Needs only the refresh token, so it works even after the access token has expired. Always 204.
     */
    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest body) {
        if (body != null) auth.logout(body.refreshToken(), Boolean.TRUE.equals(body.everywhere()));
        return ResponseEntity.noContent().build();
    }
}
