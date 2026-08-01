package com.velora.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Turns on the {@code @CreatedDate}/{@code @LastModifiedDate} handling every entity
 * relies on.
 * <p>
 * Deliberately its own class rather than an annotation on {@code VeloraBackendApplication}:
 * class-level annotations on the application class apply to every test slice that uses it
 * as its configuration source, so a {@code @WebMvcTest} would drag in JPA auditing and
 * fail on an empty metamodel. Here, only contexts that actually scan the config package
 * pick it up.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
