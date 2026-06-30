package com.chubby.dolphin.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeEnvironmentServiceTest {

    @Test
    void runtimeIdentityReturnsSafeDatabaseIdentityWithoutSecrets() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Environment environment = mock(Environment.class);

        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getSchema()).thenReturn("public");
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metaData.getUserName()).thenReturn("dolphin");
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class)).thenReturn(35);
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = false", Integer.class)).thenReturn(0);

        RuntimeEnvironmentService service = new RuntimeEnvironmentService(
                dataSource,
                jdbcTemplate,
                environment,
                "jdbc:postgresql://localhost:5432/dolphindb?sslmode=disable",
                false,
                true,
                false,
                "owner@example.com",
                "super-secret-owner-password"
        );

        Map<String, Object> identity = service.runtimeIdentity();
        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) identity.get("database");
        String serialized = identity.toString();

        assertEquals("dev", identity.get("profile"));
        assertEquals("local-dev", identity.get("environment"));
        assertEquals("localhost", database.get("host"));
        assertEquals(5432, database.get("port"));
        assertEquals("dolphindb", database.get("name"));
        assertEquals("public", database.get("schema"));
        assertEquals("PostgreSQL", database.get("product"));
        assertEquals("OK(35)", database.get("flyway"));
        assertEquals(false, identity.get("fakeDataEnabled"));
        assertEquals(false, identity.get("mockAiEnabled"));
        assertEquals(true, identity.get("localModeEnabled"));
        assertEquals(true, identity.get("firstRunOwnerConfigured"));
        assertFalse(serialized.contains("super-secret-owner-password"));
        assertFalse(serialized.toLowerCase().contains("password"));
        assertFalse(serialized.toLowerCase().contains("jwt"));
        assertFalse(serialized.toLowerCase().contains("token"));
    }
}
