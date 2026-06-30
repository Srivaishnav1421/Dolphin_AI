package com.chubby.dolphin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import com.chubby.dolphin.service.RuntimeEnvironmentService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

@Component
public class DatabaseStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseStartupValidator.class);

    private final DataSource dataSource;
    private final Environment environment;
    private final RuntimeEnvironmentService runtimeEnvironmentService;

    public DatabaseStartupValidator(DataSource dataSource,
                                    Environment environment,
                                    RuntimeEnvironmentService runtimeEnvironmentService) {
        this.dataSource = dataSource;
        this.environment = environment;
        this.runtimeEnvironmentService = runtimeEnvironmentService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        boolean testProfile = Arrays.asList(environment.getActiveProfiles()).contains("test");
        if (testProfile) {
            return;
        }

        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (product == null || !product.toLowerCase().contains("postgresql")) {
                throw new IllegalStateException("DolphinAI requires PostgreSQL. Refusing to start with datasource: " + product);
            }

            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT 1")) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new IllegalStateException("PostgreSQL startup validation query failed.");
                }
            }

            log.info("PostgreSQL startup validation passed.");
            runtimeEnvironmentService.logStartupIdentity();
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "DolphinAI cannot start without a reachable PostgreSQL database. Start PostgreSQL and retry.",
                    ex
            );
        }
    }
}
