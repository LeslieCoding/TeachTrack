package com.leslie.teachtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "teachtrack")
public record TeachTrackProperties(Auth auth, Storage storage) {

    public record Auth(String bootstrapToken) {
        public String bootstrapTokenOrEmpty() {
            return bootstrapToken == null ? "" : bootstrapToken.trim();
        }
    }

    public record Storage(
            String provider,
            String localRoot,
            String secret,
            int downloadTtlSeconds,
            S3 s3
    ) {
        public String providerOrDefault() {
            return provider == null || provider.isBlank() ? "local" : provider.trim().toLowerCase();
        }

        public S3 s3OrDefault() {
            return s3 == null ? new S3("", "auto", "", "", "") : s3;
        }
    }

    public record S3(String endpoint, String region, String bucket, String accessKey, String secretKey) {
        public boolean configured() {
            return bucket != null && !bucket.isBlank()
                    && accessKey != null && !accessKey.isBlank()
                    && secretKey != null && !secretKey.isBlank();
        }
    }
}
