package com.chubby.dolphin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

@Component
public class DatabasePreflightValidator implements BeanFactoryPostProcessor, EnvironmentAware, PriorityOrdered {

    private static final Logger log = LoggerFactory.getLogger(DatabasePreflightValidator.class);

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return PriorityOrdered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (environment == null || Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            return;
        }

        String url = environment.getProperty("spring.datasource.url", "");
        String username = environment.getProperty("spring.datasource.username", "");
        String password = environment.getProperty("spring.datasource.password", "");

        if (url == null || url.isBlank()) {
            throw new IllegalStateException("SPRING_DATASOURCE_URL is required. DolphinAI refuses to boot without an explicit PostgreSQL database.");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("SPRING_DATASOURCE_USERNAME is required. DolphinAI refuses to boot without explicit database credentials.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("SPRING_DATASOURCE_PASSWORD is required. DolphinAI refuses to boot without explicit database credentials.");
        }
        if (!url.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("DolphinAI requires PostgreSQL. Refusing datasource URL: " + url);
        }

        DriverManager.setLoginTimeout(5);
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            String product = connection.getMetaData().getDatabaseProductName();
            if (product == null || !product.toLowerCase().contains("postgresql")) {
                throw new IllegalStateException("DolphinAI requires PostgreSQL. Refusing datasource: " + product);
            }

            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT 1")) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new IllegalStateException("PostgreSQL preflight validation query failed.");
                }
            }

            log.info("PostgreSQL preflight validation passed.");
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "DolphinAI cannot boot without a reachable PostgreSQL database. Start PostgreSQL and retry.",
                    ex
            );
        }
    }
}
