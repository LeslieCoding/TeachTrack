package com.leslie.teachtrack.auth;

import com.leslie.teachtrack.config.TeachTrackProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapTokenInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapTokenInitializer.class);

    private final TeachTrackProperties properties;
    private final TokenService tokenService;
    private final ApiTokenRepository repository;

    public BootstrapTokenInitializer(
            TeachTrackProperties properties,
            TokenService tokenService,
            ApiTokenRepository repository
    ) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        String bootstrapToken = properties.auth() == null ? "" : properties.auth().bootstrapTokenOrEmpty();
        if (repository.count() > 0) {
            return;
        }
        if (bootstrapToken.isEmpty()) {
            log.warn("No API token exists and TEACHTRACK_BOOTSTRAP_TOKEN is empty. "
                    + "All /api/v1 endpoints will reject requests until a token is provisioned.");
            return;
        }
        if (tokenService.bootstrapOwner(bootstrapToken)) {
            log.info("Bootstrapped owner API token from TEACHTRACK_BOOTSTRAP_TOKEN (raw token not logged).");
        }
    }
}
