package com.irinajo.auth;

import com.irinajo.common.ApiException;
import com.irinajo.common.Mask;
import com.irinajo.common.Phones;
import com.irinajo.common.RateLimiter;
import com.irinajo.common.Tokens;
import com.irinajo.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Phone one-time codes: 6 digits, 10-minute expiry, at most 5 wrong attempts, resend after 60 s.
 * Codes are stored only as HMAC-style hashes (sha256(pepper:phone:code)).
 * Abuse protection: limits per phone, per device and per IP, Nigerian mobile ranges only, and a
 * global hourly volume alarm against SMS pumping fraud.
 */
@Service
public class OtpService {
    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final OtpChallengeRepository repo;
    private final CodeSender sender;
    private final RateLimiter limiter;
    private final AppProperties.Otp cfg;
    private final RestClient http;
    private final Map<String, String> testNumbers;

    public OtpService(OtpChallengeRepository repo, CodeSender sender, RateLimiter limiter,
                      AppProperties props, RestClient http) {
        this.repo = repo;
        this.sender = sender;
        this.limiter = limiter;
        this.cfg = props.otp();
        this.http = http;
        this.testNumbers = cfg.testNumberMap();
        if (!testNumbers.isEmpty()) log.warn("OTP test numbers are enabled ({}). Never enable these in production.", testNumbers.size());
    }

    public record RequestResult(String phone, String channel, int resendAfterSec, int expiresInSec) {}

    @Transactional
    public RequestResult request(String rawPhone, String channel, String deviceId, String ip) {
        String phone = Phones.normalise(rawPhone);
        String ch = "whatsapp".equalsIgnoreCase(channel) ? "whatsapp" : "sms";

        limiter.check("otp-phone:" + phone, cfg.perPhonePerHour(), 3600, "OTP_RATE_LIMITED");
        if (deviceId != null && !deviceId.isBlank()) {
            limiter.check("otp-device:" + deviceId, cfg.perDevicePerHour(), 3600, "OTP_RATE_LIMITED");
        }
        limiter.check("otp-ip:" + ip, cfg.perIpPerHour(), 3600, "OTP_RATE_LIMITED");

        OtpChallenge c = repo.findById(phone).orElseGet(() -> new OtpChallenge(phone));
        Instant now = Instant.now();
        if (c.getLastSentAt() != null) {
            long since = Duration.between(c.getLastSentAt(), now).getSeconds();
            if (since < cfg.resendSeconds()) {
                int wait = (int) (cfg.resendSeconds() - since);
                throw ApiException.tooMany("OTP_RESEND_TOO_SOON", "Wait " + wait + " seconds before asking for a new code", wait);
            }
        }

        boolean isTest = testNumbers.containsKey(phone);
        String code = isTest ? testNumbers.get(phone) : Tokens.sixDigitCode();

        c.setCodeHash(hash(phone, code));
        c.setExpiresAt(now.plus(Duration.ofMinutes(cfg.ttlMinutes())));
        c.setLastSentAt(now);
        c.setAttempts(0);
        c.setChannel(ch);
        repo.save(c);

        if (!isTest) {
            volumeAlarm();
            sender.send(phone, code, ch);
        }
        log.info("OTP issued to {} via {}", Mask.phone(phone), ch);
        return new RequestResult(phone, ch, cfg.resendSeconds(), cfg.ttlMinutes() * 60);
    }

    /**
     * Checks the code and consumes it on success. Throws a specific error otherwise.
     * Uses noRollbackFor so the attempt counter is saved even when we throw.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public String verify(String rawPhone, String code) {
        String phone = Phones.normalise(rawPhone);
        if (code == null || !code.matches("\\d{6}")) throw ApiException.badRequest("OTP_FORMAT", "Enter the 6-digit code");

        OtpChallenge c = repo.findById(phone)
                .orElseThrow(() -> ApiException.badRequest("OTP_NOT_FOUND", "Request a new code"));
        if (c.getExpiresAt().isBefore(Instant.now())) {
            repo.delete(c);
            throw ApiException.badRequest("OTP_EXPIRED", "This code has expired. Request a new one");
        }
        if (c.getAttempts() >= cfg.maxAttempts()) {
            throw ApiException.badRequest("OTP_TOO_MANY_ATTEMPTS", "Too many wrong tries. Request a new code");
        }
        if (!Tokens.constantTimeEquals(c.getCodeHash(), hash(phone, code))) {
            c.setAttempts(c.getAttempts() + 1);
            repo.save(c);
            int left = cfg.maxAttempts() - c.getAttempts();
            throw new ApiException(HttpStatus.BAD_REQUEST, left > 0 ? "OTP_WRONG" : "OTP_TOO_MANY_ATTEMPTS",
                    left > 0 ? "Wrong code. " + left + " tries left" : "Too many wrong tries. Request a new code", false, null);
        }
        repo.delete(c);
        return phone;
    }

    private String hash(String phone, String code) {
        return Tokens.sha256(cfg.pepper() + ":" + phone + ":" + code);
    }

    /** Alerts once per hour when total sends cross the threshold (possible SMS pumping). */
    private void volumeAlarm() {
        int over = limiter.hit("otp-global", cfg.alertThresholdPerHour(), 3600);
        if (over > 0 && limiter.hit("otp-global-alerted", 1, 3600) == 0) {
            String msg = "ALERT: Irin Ajo OTP volume exceeded " + cfg.alertThresholdPerHour() + " codes this hour";
            log.error(msg);
            String url = cfg.alertWebhookUrl();
            if (url != null && !url.isBlank()) {
                CompletableFuture.runAsync(() -> {
                    try {
                        http.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                                .body(Map.of("text", msg)).retrieve().toBodilessEntity();
                    } catch (Exception e) {
                        log.warn("Alert webhook failed: {}", e.getMessage());
                    }
                });
            }
        }
    }
}
