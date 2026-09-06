package com.fapp.persistence;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Boots the application against a real PostgreSQL of the same major version as
 * production, with Flyway applying every migration on startup.
 *
 * <p>This is not optional cover. Flyway owns the schema and {@code ddl-auto} is
 * {@code none}, so Hibernate never checks that the entities and the migration agree —
 * a wrong column name or type would fail silently until the first real query. A
 * container is the only thing that can prove the two halves match.
 *
 * <p>The container is a JVM-wide singleton started once in a static initialiser, and
 * deliberately never stopped. The JUnit {@code @Testcontainers}/{@code @Container}
 * pair would stop it after each test class while Spring's cached application context
 * kept pointing at the dead port; Ryuk reaps it when the JVM exits instead.
 */
@SpringBootTest
public abstract class AbstractPostgresTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }
}
