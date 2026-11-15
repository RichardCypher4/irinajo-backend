package com.irinajo.auth;

import com.irinajo.common.ApiException;
import com.irinajo.common.ChangeSequence;
import com.irinajo.common.Tokens;
import com.irinajo.config.AppProperties;
import com.irinajo.security.ExternalIdTokenVerifier;
import com.irinajo.security.TokenService;
import com.irinajo.user.Profile;
import com.irinajo.user.ProfileDto;
import com.irinajo.user.ProfileRepository;
import com.irinajo.user.ProfileService;
import com.irinajo.user.User;
import com.irinajo.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    public record AuthResponse(String accessToken, long expiresIn, String refreshToken, String tokenType,
                               UserInfo user, ProfileDto profile, boolean isNewUser) {}

    public record UserInfo(UUID id, String phone, boolean google, boolean apple, String email) {
        static UserInfo of(User u) {
            return new UserInfo(u.getId(), u.getPhone(), u.getGoogleSub() != null, u.getAppleSub() != null, u.getEmail());
        }
    }

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final ProfileService profileService;
    private final RefreshTokenRepository refreshTokens;
    private final TokenService tokens;
    private final OtpService otp;
    private final ExternalIdTokenVerifier idVerifier;
    private final ChangeSequence seq;
    private final AppProperties props;

    public AuthService(UserRepository users, ProfileRepository profiles, ProfileService profileService,
                       RefreshTokenRepository refreshTokens, TokenService tokens, OtpService otp,
                       ExternalIdTokenVerifier idVerifier, ChangeSequence seq, AppProperties props) {
        this.users = users;
        this.profiles = profiles;
        this.profileService = profileService;
        this.refreshTokens = refreshTokens;
        this.tokens = tokens;
        this.otp = otp;
        this.idVerifier = idVerifier;
        this.seq = seq;
        this.props = props;
    }

    // ---------- Sign in ----------

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse phone(String phone, String code, String name, String deviceId) {
        String verified = otp.verify(phone, code);
        Optional<User> existing = users.findByPhone(verified);
        User u = existing.orElseGet(() -> {
            User n = User.create();
            n.setPhone(verified);
            return users.save(n);
        });
        return issue(u, name, existing.isEmpty(), deviceId);
    }

    @Transactional
    public AuthResponse google(String idToken, String deviceId) {
        var id = idVerifier.verifyGoogle(idToken);
        Optional<User> existing = users.findByGoogleSub(id.subject());
        User u = existing.orElseGet(() -> {
            User n = User.create();
            n.setGoogleSub(id.subject());
            n.setEmail(id.email());
            return users.save(n);
        });
        return issue(u, null, existing.isEmpty(), deviceId);
    }

    /** Apple sends the person's name only on first sign-in; the app passes it as fullName. */
    @Transactional
    public AuthResponse apple(String identityToken, String fullName, String deviceId) {
        var id = idVerifier.verifyApple(identityToken);
        Optional<User> existing = users.findByAppleSub(id.subject());
        User u = existing.orElseGet(() -> {
            User n = User.create();
            n.setAppleSub(id.subject());
            n.setEmail(id.email());
            return users.save(n);
        });
        return issue(u, fullName, existing.isEmpty(), deviceId);
    }

    // ---------- Linking extra sign-in methods to the signed-in account ----------

    @Transactional(noRollbackFor = ApiException.class)
    public UserInfo linkPhone(UUID userId, String phone, String code) {
        String verified = otp.verify(phone, code);
        users.findByPhone(verified).filter(o -> !o.getId().equals(userId)).ifPresent(o -> {
            throw ApiException.conflict("PHONE_IN_USE", "This number is already used by another account");
        });
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Account not found"));
        u.setPhone(verified);
        return UserInfo.of(users.save(u));
    }

    @Transactional
    public UserInfo linkGoogle(UUID userId, String idToken) {
        var id = idVerifier.verifyGoogle(idToken);
        users.findByGoogleSub(id.subject()).filter(o -> !o.getId().equals(userId)).ifPresent(o -> {
            throw ApiException.conflict("GOOGLE_IN_USE", "This Google account is linked to another account");
        });
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Account not found"));
        u.setGoogleSub(id.subject());
        if (u.getEmail() == null) u.setEmail(id.email());
        return UserInfo.of(users.save(u));
    }

    @Transactional
    public UserInfo linkApple(UUID userId, String identityToken) {
        var id = idVerifier.verifyApple(identityToken);
        users.findByAppleSub(id.subject()).filter(o -> !o.getId().equals(userId)).ifPresent(o -> {
            throw ApiException.conflict("APPLE_IN_USE", "This Apple ID is linked to another account");
        });
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Account not found"));
        u.setAppleSub(id.subject());
        if (u.getEmail() == null) u.setEmail(id.email());
        return UserInfo.of(users.save(u));
    }

    // ---------- Sessions ----------

    /**
     * Refresh tokens are not rotated on every use: on patchy networks a lost response would otherwise
     * sign the person out. Instead the expiry slides forward on each use, and logout/revoke kills it.
     */
    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshToken rt = refreshTokens.findByTokenHash(Tokens.sha256(refreshToken == null ? "" : refreshToken))
                .filter(RefreshToken::isActive)
                .orElseThrow(() -> ApiException.unauthorized("REFRESH_INVALID", "Session ended. Sign in again"));
        User u = users.findById(rt.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("REFRESH_INVALID", "Session ended. Sign in again"));
        rt.extendTo(Instant.now().plus(props.jwt().refreshTtlDays(), ChronoUnit.DAYS));
        refreshTokens.save(rt);
        return new AuthResponse(tokens.accessToken(u.getId(), u.getRole()), tokens.accessTtlSeconds(),
                refreshToken, "Bearer", UserInfo.of(u), null, false);
    }

    @Transactional
    public void logout(String refreshToken, boolean everywhere) {
        if (refreshToken == null) return;
        refreshTokens.findByTokenHash(Tokens.sha256(refreshToken)).ifPresent(rt -> {
            if (everywhere) {
                refreshTokens.revokeAll(rt.getUserId(), Instant.now());
            } else {
                rt.revoke();
                refreshTokens.save(rt);
            }
        });
    }

    private AuthResponse issue(User u, String name, boolean isNew, String deviceId) {
        Profile p = profileService.getOrCreate(u.getId());
        if (name != null && !name.isBlank() && p.getName() == null) {
            String trimmed = name.trim();
            p.setName(trimmed.length() > 30 ? trimmed.substring(0, 30) : trimmed);
            p.setSeq(seq.next());
            profiles.save(p);
        }
        String refresh = Tokens.random(32);
        refreshTokens.save(new RefreshToken(u.getId(), Tokens.sha256(refresh), deviceId,
                Instant.now().plus(props.jwt().refreshTtlDays(), ChronoUnit.DAYS)));
        return new AuthResponse(tokens.accessToken(u.getId(), u.getRole()), tokens.accessTtlSeconds(),
                refresh, "Bearer", UserInfo.of(u), profileService.toDto(p), isNew);
    }
}
