package com.irinajo.push;

import com.irinajo.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PushController {

    public record PushTokenRequest(@NotBlank @Size(max = 255) String token,
                                   @Pattern(regexp = "^(android|ios)$") String platform,
                                   @Size(max = 100) String deviceId) {}

    private final PushService push;

    public PushController(PushService push) {
        this.push = push;
    }

    @PostMapping("/devices/push-token")
    public ResponseEntity<Void> register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PushTokenRequest body) {
        push.register(CurrentUser.id(jwt), body.token(), body.platform(), body.deviceId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/devices/push-token")
    public ResponseEntity<Void> unregister(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PushTokenRequest body) {
        push.unregister(CurrentUser.id(jwt), body.token());
        return ResponseEntity.noContent().build();
    }
}
