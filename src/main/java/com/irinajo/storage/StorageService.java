package com.irinajo.storage;

import com.irinajo.common.ApiException;
import com.irinajo.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

/**
 * S3-compatible object storage: Cloudflare R2 (free egress, recommended), DigitalOcean Spaces or AWS S3.
 * Files are served via CDN_BASE_URL.
 */
@Service
public class StorageService {
    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final AppProperties.Storage cfg;
    private final S3Client s3;
    private final S3Presigner presigner;

    public StorageService(AppProperties props) {
        this.cfg = props.storage();
        if (cfg.configured()) {
            var creds = StaticCredentialsProvider.create(AwsBasicCredentials.create(cfg.accessKey(), cfg.secretKey()));
            var region = Region.of(cfg.region() == null || cfg.region().isBlank() ? "auto" : cfg.region());
            var s3cfg = S3Configuration.builder().pathStyleAccessEnabled(true).build();
            var b = S3Client.builder().credentialsProvider(creds).region(region).serviceConfiguration(s3cfg);
            var p = S3Presigner.builder().credentialsProvider(creds).region(region).serviceConfiguration(s3cfg);
            if (cfg.endpoint() != null && !cfg.endpoint().isBlank()) {
                b.endpointOverride(URI.create(cfg.endpoint()));
                p.endpointOverride(URI.create(cfg.endpoint()));
            }
            this.s3 = b.build();
            this.presigner = p.build();
        } else {
            this.s3 = null;
            this.presigner = null;
            log.warn("Object storage not configured: photo uploads and voice-pack hosting are disabled");
        }
    }

    public record SignedUpload(String uploadUrl, String key, String publicUrl, String contentType, long expiresInSec) {}

    public SignedUpload signedJpegUpload(String key) {
        requireConfigured();
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(cfg.bucket()).key(key).contentType("image/jpeg")
                .cacheControl("public, max-age=31536000, immutable")
                .build();
        var req = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(put).build();
        String url = presigner.presignPutObject(req).url().toString();
        return new SignedUpload(url, key, publicUrl(key), "image/jpeg", 600);
    }

    public void put(String key, byte[] bytes, String contentType) {
        requireConfigured();
        s3.putObject(PutObjectRequest.builder().bucket(cfg.bucket()).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(bytes));
    }

    public String publicUrl(String key) {
        if (key == null) return null;
        String base = cfg.cdnBaseUrl();
        if (base == null || base.isBlank()) return null;
        return base.replaceAll("/$", "") + "/" + key;
    }

    public void delete(String key) {
        if (s3 == null || key == null) return;
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(cfg.bucket()).key(key).build());
        } catch (Exception e) {
            log.warn("Could not delete object {}: {}", key, e.getMessage());
        }
    }

    /** Deletes everything under a prefix, e.g. "users/{id}/" when an account is deleted. */
    public void deletePrefix(String prefix) {
        if (s3 == null) return;
        try {
            var resp = s3.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(cfg.bucket()).prefix(prefix).build());
            for (S3Object o : resp.contents()) delete(o.key());
        } catch (Exception e) {
            log.warn("Could not delete prefix {}: {}", prefix, e.getMessage());
        }
    }

    private void requireConfigured() {
        if (s3 == null) throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "STORAGE_UNAVAILABLE", "File storage is not configured", true, 60);
    }
}
