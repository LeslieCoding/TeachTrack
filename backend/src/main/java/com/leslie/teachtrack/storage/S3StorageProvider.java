package com.leslie.teachtrack.storage;

import com.leslie.teachtrack.common.ApiException;
import com.leslie.teachtrack.config.TeachTrackProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3-compatible storage (Cloudflare R2 / MinIO / Aliyun OSS / Tencent COS via S3 API).
 * Business code only depends on {@link ObjectStorageProvider}.
 */
public class S3StorageProvider implements ObjectStorageProvider {
    private final String bucket;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3StorageProvider(TeachTrackProperties.S3 config) {
        if (!config.configured()) {
            throw new IllegalStateException("S3 storage selected but bucket/access-key/secret-key are incomplete");
        }
        this.bucket = config.bucket();
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.accessKey(), config.secretKey()));
        Region region = Region.of(config.region() == null || config.region().isBlank() ? "auto" : config.region());
        var builder = S3Client.builder()
                .region(region)
                .credentialsProvider(credentials)
                .forcePathStyle(true);
        var presignerBuilder = S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        if (config.endpoint() != null && !config.endpoint().isBlank()) {
            URI endpoint = URI.create(config.endpoint());
            builder.endpointOverride(endpoint);
            presignerBuilder.endpointOverride(endpoint);
        }
        this.client = builder.build();
        this.presigner = presignerBuilder.build();
    }

    @Override
    public String providerId() {
        return "s3";
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        client.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build(), RequestBody.fromBytes(data));
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket).key(key).build()).asByteArray());
        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            client.headObject(builder -> builder.bucket(bucket).key(key));
            return true;
        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException exception) {
            return false;
        } catch (software.amazon.awssdk.services.s3.model.S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw exception;
        }
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public PresignedUrl presignDownload(String key, Duration ttl) {
        var request = GetObjectRequest.builder().bucket(bucket).key(key).build();
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl).getObjectRequest(request).build());
        return new PresignedUrl(presigned.url().toString(), "GET",
                java.time.Instant.now().plus(ttl));
    }

    @Override
    public PresignedUrl presignUpload(String key, String contentType, Duration ttl) {
        var request = PutObjectRequest.builder()
                .bucket(bucket).key(key).contentType(contentType).build();
        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl).putObjectRequest(request).build());
        return new PresignedUrl(presigned.url().toString(), "PUT",
                java.time.Instant.now().plus(ttl));
    }
}
