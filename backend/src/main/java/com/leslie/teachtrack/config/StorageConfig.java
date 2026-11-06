package com.leslie.teachtrack.config;

import com.leslie.teachtrack.storage.BaiduPanStorageProvider;
import com.leslie.teachtrack.storage.LocalFsStorageProvider;
import com.leslie.teachtrack.storage.ObjectStorageProvider;
import com.leslie.teachtrack.storage.S3StorageProvider;
import com.leslie.teachtrack.storage.StorageSigner;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {
    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    ObjectStorageProvider objectStorageProvider(
            TeachTrackProperties properties,
            StorageSigner signer,
            @org.springframework.beans.factory.annotation.Value("${teachtrack.storage.baidu.bypy:bypy}") String bypy,
            @org.springframework.beans.factory.annotation.Value("${teachtrack.storage.baidu.remote-root:/apps/bypy/teachtrack}") String baiduRoot,
            @org.springframework.beans.factory.annotation.Value("${teachtrack.storage.baidu.timeout-seconds:120}") int baiduTimeout
    ) {
        TeachTrackProperties.Storage storage = properties.storage();
        String provider = storage == null ? "local" : storage.providerOrDefault();
        if ("baidu-pan".equals(provider) || "baidu".equals(provider)) {
            log.info("Object storage provider: baidu-pan ({})", baiduRoot);
            return new BaiduPanStorageProvider(baiduRoot, bypy, baiduTimeout, signer);
        }
        if ("s3".equals(provider)) {
            log.info("Object storage provider: s3");
            return new S3StorageProvider(storage.s3OrDefault());
        }
        String root = storage == null || storage.localRoot() == null || storage.localRoot().isBlank()
                ? "./data/storage"
                : storage.localRoot();
        log.info("Object storage provider: local ({})", root);
        return new LocalFsStorageProvider(Path.of(root), signer);
    }
}
