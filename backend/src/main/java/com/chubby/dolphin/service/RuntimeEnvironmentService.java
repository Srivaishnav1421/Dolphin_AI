package com.chubby.dolphin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RuntimeEnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeEnvironmentService.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;
    private final String datasourceUrl;
    private final boolean mockAiEnabled;
    private final boolean localModeEnabled;
    private final boolean demoUsersEnabled;
    private final String firstRunOwnerEmail;
    private final String firstRunOwnerPassword;

    public RuntimeEnvironmentService(DataSource dataSource,
                                     JdbcTemplate jdbcTemplate,
                                     Environment environment,
                                     @Value("${spring.datasource.url:}") String datasourceUrl,
                                     @Value("${ai.mock.enabled:false}") boolean mockAiEnabled,
                                     @Value("${dolphin.local-mode.enabled:false}") boolean localModeEnabled,
                                     @Value("${app.seed.demo-users-enabled:false}") boolean demoUsersEnabled,
                                     @Value("${first-run.owner.email:}") String firstRunOwnerEmail,
                                     @Value("${first-run.owner.password:}") String firstRunOwnerPassword) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
        this.datasourceUrl = datasourceUrl;
        this.mockAiEnabled = mockAiEnabled;
        this.localModeEnabled = localModeEnabled;
        this.demoUsersEnabled = demoUsersEnabled;
        this.firstRunOwnerEmail = firstRunOwnerEmail;
        this.firstRunOwnerPassword = firstRunOwnerPassword;
    }

    public Map<String, Object> runtimeIdentity() {
        JdbcParts parts = parseJdbcUrl(datasourceUrl);
        Map<String, Object> database = new LinkedHashMap<>();
        database.put("connected", false);
        database.put("host", parts.host());
        database.put("port", parts.port());
        database.put("name", parts.databaseName());

        try (Connection connection = dataSource.getConnection()) {
            database.put("connected", true);
            database.put("schema", connection.getSchema());
            database.put("product", connection.getMetaData().getDatabaseProductName());
            database.put("user", connection.getMetaData().getUserName());
            database.put("flyway", flywayStatus());
        } catch (Exception ex) {
            database.put("schema", "");
            database.put("product", "");
            database.put("user", "");
            database.put("flyway", "UNKNOWN");
        }

        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("profile", activeProfile());
        identity.put("database", database);
        identity.put("localModeEnabled", localModeEnabled);
        identity.put("fakeDataEnabled", demoUsersEnabled);
        identity.put("mockAiEnabled", mockAiEnabled);
        identity.put("firstRunOwnerConfigured", configured(firstRunOwnerEmail) && configured(firstRunOwnerPassword));
        identity.put("environment", environmentLabel(parts));
        return identity;
    }

    public void logStartupIdentity() {
        Map<String, Object> identity = runtimeIdentity();
        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) identity.get("database");
        log.info(
                "DolphinAI Runtime: profile={}, dbHost={}, dbPort={}, dbName={}, schema={}, user={}, product={}, flyway={}, firstRunOwnerConfigured={}, demoUsersEnabled={}, mockAiEnabled={}",
                identity.get("profile"),
                database.get("host"),
                database.get("port"),
                database.get("name"),
                database.get("schema"),
                database.get("user"),
                database.get("product"),
                database.get("flyway"),
                identity.get("firstRunOwnerConfigured"),
                identity.get("fakeDataEnabled"),
                identity.get("mockAiEnabled")
        );
    }

    private String activeProfile() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            return "default";
        }
        return String.join(",", profiles);
    }

    private String environmentLabel(JdbcParts parts) {
        boolean devProfile = Arrays.stream(environment.getActiveProfiles()).anyMatch("dev"::equalsIgnoreCase);
        boolean localHost = "localhost".equalsIgnoreCase(parts.host()) || "127.0.0.1".equals(parts.host());
        if (devProfile && localHost) {
            return "local-dev";
        }
        return activeProfile();
    }

    private String flywayStatus() {
        try {
            Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
            Integer failed = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = false", Integer.class);
            return failed != null && failed > 0 ? "FAILED(" + failed + "/" + total + ")" : "OK(" + total + ")";
        } catch (Exception ex) {
            return "UNKNOWN";
        }
    }

    private JdbcParts parseJdbcUrl(String url) {
        if (url == null || !url.startsWith("jdbc:postgresql://")) {
            return new JdbcParts("", 5432, "");
        }
        try {
            URI uri = URI.create(url.substring("jdbc:".length()));
            String path = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
            return new JdbcParts(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 5432, path);
        } catch (Exception ex) {
            return new JdbcParts("", 5432, "");
        }
    }

    private boolean configured(String value) {
        return value != null && !value.isBlank();
    }

    private record JdbcParts(String host, int port, String databaseName) {
    }
}
