package com.irinajo.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.irinajo.account.AccountService;
import com.irinajo.auth.AuthService;
import com.irinajo.common.ApiException;
import com.irinajo.common.Tokens;
import com.irinajo.security.CurrentUser;
import com.irinajo.storage.StorageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class MeController {

    public record Me(AuthService.UserInfo user, ProfileDto profile) {}
    public record PatchResponse(ProfileDto profile, boolean applied) {}
    public record UploadUrlRequest(@NotBlank @Pattern(regexp = "^(avatar|cover)$") String kind) {}
    public record LinkPhone(@NotBlank String phone, @NotBlank String code) {}
    public record LinkGoogle(@NotBlank String idToken) {}
    public record LinkApple(@NotBlank String identityToken) {}

    private final UserRepository users;
    private final ProfileService profiles;
    private final StorageService storage;
    private final AccountService account;
    private final AuthService auth;

    public MeController(UserRepository users, ProfileService profiles, StorageService storage,
                        AccountService account, AuthService auth) {
        this.users = users;
        this.profiles = profiles;
        this.storage = storage;
        this.account = account;
        this.auth = auth;
    }

    @GetMapping("/me")
    public Me me(@AuthenticationPrincipal Jwt jwt) {
        UUID id = CurrentUser.id(jwt);
        User u = users.findById(id).orElseThrow(() -> ApiException.unauthorized("USER_NOT_FOUND", "Account not found"));
        return new Me(new AuthService.UserInfo(u.getId(), u.getPhone(), u.getGoogleSub() != null,
                u.getAppleSub() != null, u.getEmail()), profiles.toDto(profiles.getOrCreate(id)));
    }

    /**
     * Partial update; send only changed fields plus updatedAt (epoch ms on the phone).
     * Example: { "name": "Tolu", "voice": { "speaker": "tunde" }, "updatedAt": 1760000000000 }
     */
    @PatchMapping("/me/profile")
    public PatchResponse patch(@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode body) {
        var r = profiles.patch(CurrentUser.id(jwt), body);
        return new PatchResponse(r.profile(), r.applied());
    }

    /**
     * Step 1: get a signed URL; step 2: PUT the JPEG to it with Content-Type: image/jpeg;
     * step 3: PATCH /me/profile with { "avatarKey": key } (or coverKey).
     */
    @PostMapping("/me/photos/upload-url")
    public StorageService.SignedUpload uploadUrl(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UploadUrlRequest body) {
        String key = "users/" + CurrentUser.id(jwt) + "/" + body.kind() + "-" + Tokens.random(9) + ".jpg";
        return storage.signedJpegUpload(key);
    }

    @PostMapping("/me/link/phone")
    public AuthService.UserInfo linkPhone(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LinkPhone body) {
        return auth.linkPhone(CurrentUser.id(jwt), body.phone(), body.code());
    }

    @PostMapping("/me/link/google")
    public AuthService.UserInfo linkGoogle(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LinkGoogle body) {
        return auth.linkGoogle(CurrentUser.id(jwt), body.idToken());
    }

    @PostMapping("/me/link/apple")
    public AuthService.UserInfo linkApple(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LinkApple body) {
        return auth.linkApple(CurrentUser.id(jwt), body.identityToken());
    }

    @GetMapping("/me/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt) {
        byte[] zip = account.export(CurrentUser.id(jwt));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"irinajo-my-data.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zip);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt) {
        account.delete(CurrentUser.id(jwt));
        return ResponseEntity.noContent().build();
    }
}
