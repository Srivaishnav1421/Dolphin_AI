package com.chubby.dolphin.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabasePreflightValidatorTest {

    @Test
    void rejectsMissingDatasourceUrlOutsideTestProfile() {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(environment.getProperty("spring.datasource.url", "")).thenReturn("");
        when(environment.getProperty("spring.datasource.username", "")).thenReturn("dolphin");
        when(environment.getProperty("spring.datasource.password", "")).thenReturn("secure-password");

        DatabasePreflightValidator validator = new DatabasePreflightValidator();
        validator.setEnvironment(environment);

        assertThrows(IllegalStateException.class,
                () -> validator.postProcessBeanFactory(mock(ConfigurableListableBeanFactory.class)));
    }

    @Test
    void rejectsNonPostgresDatasourceUrl() {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(environment.getProperty("spring.datasource.url", "")).thenReturn("jdbc:h2:mem:testdb");
        when(environment.getProperty("spring.datasource.username", "")).thenReturn("sa");
        when(environment.getProperty("spring.datasource.password", "")).thenReturn("secure-password");

        DatabasePreflightValidator validator = new DatabasePreflightValidator();
        validator.setEnvironment(environment);

        assertThrows(IllegalStateException.class,
                () -> validator.postProcessBeanFactory(mock(ConfigurableListableBeanFactory.class)));
    }
}
