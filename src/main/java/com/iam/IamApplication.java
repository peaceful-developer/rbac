package com.iam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point. Being in the {@code com.iam} root package is what makes Spring Boot's
 * component scan (via {@code @SpringBootApplication}) and Spring Data JPA's repository
 * scan both automatically cover every subpackage (config, controller, domain,
 * repository, security, service, etc.) with no explicit {@code @ComponentScan} or
 * {@code @EnableJpaRepositories} base-package configuration needed.
 * <p>
 * {@code @ConfigurationPropertiesScan} is what picks up {@code JwtProperties} and
 * {@code CorsProperties} (see the config package) without them needing individual
 * {@code @Bean} methods.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class IamApplication {

    public static void main(String[] args) {
        SpringApplication.run(IamApplication.class, args);
    }
}
