package com.leslie.teachtrack;

import com.leslie.teachtrack.config.TeachTrackProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TeachTrackProperties.class)
public class TeachTrackApplication {
    public static void main(String[] args) {
        SpringApplication.run(TeachTrackApplication.class, args);
    }
}
