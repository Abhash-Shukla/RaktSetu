package lifeline.api.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * PostgreSQL Testcontainers Integration Test.
 * Verifies actual PostgreSQL database migrations, table constraints, indexes, and schema integrity.
 * <p>
 * Under user instruction:
 * "H2 must not be treated as a replacement for PostgreSQL Testcontainers integration tests.
 *  Keep actual PostgreSQL integration tests. If Docker is unavailable, report the affected
 *  tests as BLOCKED rather than PASSED. Do not silently skip them."
 */
class PostgreSqlTestcontainersIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PostgreSqlTestcontainersIntegrationTest.class);

    private static PostgreSQLContainer<?> postgres;
    private static boolean dockerAvailable = false;

    @BeforeAll
    static void checkDockerAvailability() {
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            dockerAvailable = false;
        }

        if (!dockerAvailable) {
            log.warn("================================================================================");
            log.warn("TEST BLOCKED: PostgreSQL Testcontainers integration test is BLOCKED.");
            log.warn("Reason: Docker daemon is not active on host pipe //./pipe/dockerDesktopLinuxEngine.");
            log.warn("Reporting status: BLOCKED (NOT PASSED).");
            log.warn("================================================================================");
            abort("BLOCKED: Docker daemon unavailable on host environment (reported as BLOCKED, not PASSED)");
        } else {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("lifeline_test")
                .withUsername("lifeline_test")
                .withPassword("lifeline_test");
            postgres.start();
            log.info("PostgreSQL Testcontainer started at {}", postgres.getJdbcUrl());
        }
    }

    @AfterAll
    static void tearDown() {
        if (postgres != null && postgres.isRunning()) {
            postgres.stop();
        }
    }

    @Test
    @DisplayName("PostgreSQL Schema & Migrations: Flyway applies cleanly and constraints are enforced")
    void testPostgreSqlSchemaAndMigrations() throws Exception {
        if (!dockerAvailable || postgres == null) {
            abort("BLOCKED: Docker daemon unavailable on host environment");
            return;
        }

        assertThat(postgres.isRunning()).isTrue();

        // 1. Execute Flyway migrations against actual PostgreSQL container
        Flyway flyway = Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .load();

        MigrateResult migrateResult = flyway.migrate();
        assertThat(migrateResult.success).isTrue();
        assertThat(migrateResult.migrationsExecuted).isGreaterThanOrEqualTo(3);
        log.info("Flyway successfully executed {} migrations", migrateResult.migrationsExecuted);

        // 2. Verify PostgreSQL version and schema structure
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            // Version check
            ResultSet rs = stmt.executeQuery("SELECT version();");
            assertThat(rs.next()).isTrue();
            String version = rs.getString(1);
            assertThat(version).contains("PostgreSQL 16");
            log.info("Connected to PostgreSQL version: {}", version);

            // Table existence check
            String[] requiredTables = {
                "users", "donor_profiles", "blood_requests", "banks",
                "bank_inventory", "engine_config_versions", "match_logs", "audit_log"
            };
            DatabaseMetaData metaData = conn.getMetaData();
            for (String tableName : requiredTables) {
                try (ResultSet tables = metaData.getTables(null, "public", tableName, new String[]{"TABLE"})) {
                    assertThat(tables.next()).as("Table " + tableName + " should exist in PostgreSQL public schema").isTrue();
                }
            }

            // Verify demo seed data is present from V2 migration
            ResultSet countUsers = stmt.executeQuery("SELECT COUNT(*) FROM users;");
            assertThat(countUsers.next()).isTrue();
            assertThat(countUsers.getInt(1)).isGreaterThanOrEqualTo(8);

            ResultSet countBanks = stmt.executeQuery("SELECT COUNT(*) FROM banks;");
            assertThat(countBanks.next()).isTrue();
            assertThat(countBanks.getInt(1)).isGreaterThanOrEqualTo(3);

            ResultSet configVersion = stmt.executeQuery("SELECT version_number, is_active FROM engine_config_versions WHERE is_active = TRUE;");
            assertThat(configVersion.next()).isTrue();
            assertThat(configVersion.getInt("version_number")).isEqualTo(1);

            // 3. Verify Database Constraints (CHECK constraint on bank_inventory units_available >= 0)
            assertThatThrownBy(() -> {
                stmt.executeUpdate("INSERT INTO bank_inventory (bank_id, blood_group, units_available) VALUES (1, 'AB_NEG', -5);");
            }).as("Inserting negative units must violate CHECK constraint units_available >= 0")
              .isInstanceOf(SQLException.class)
              .hasMessageContaining("chk_units_available_non_negative");

            // 4. Verify Optimistic Locking version columns exist
            String[] versionedTables = {"donor_profiles", "blood_requests", "bank_inventory"};
            for (String vTable : versionedTables) {
                try (ResultSet cols = metaData.getColumns(null, "public", vTable, "version")) {
                    assertThat(cols.next()).as("Table " + vTable + " must contain 'version' column for JPA optimistic locking").isTrue();
                }
            }

            // 5. Verify V3 NLP Intake Columns exist on blood_requests
            String[] nlpColumns = {"contact_number", "unparsed_text", "extraction_confidence"};
            for (String colName : nlpColumns) {
                try (ResultSet cols = metaData.getColumns(null, "public", "blood_requests", colName)) {
                    assertThat(cols.next()).as("Table blood_requests must contain column '" + colName + "' from V3 migration").isTrue();
                }
            }
        }
    }
}
