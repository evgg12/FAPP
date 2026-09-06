package com.fapp;

import com.fapp.persistence.AbstractPostgresTest;
import com.fapp.statement.StatementAdapter;
import com.fapp.statement.StatementImportService;
import com.fapp.statement.monzo.MonzoStatementAdapter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads the real application context against a real PostgreSQL.
 *
 * <p>It used to run with persistence excluded, which stopped being a meaningful check
 * the moment the application had repositories and a service that need them: the context
 * it was proving loadable was not the one that runs.
 */
class FappApplicationTests extends AbstractPostgresTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBean(com.fapp.health.HealthController.class)).isNotNull();
        assertThat(applicationContext.getBean(StatementImportService.class)).isNotNull();
    }

    @Test
    void registersEveryStatementAdapterUnderADistinctProvider() {
        List<StatementAdapter> adapters = applicationContext.getBeansOfType(StatementAdapter.class)
                .values().stream().toList();

        assertThat(adapters).hasAtLeastOneElementOfType(MonzoStatementAdapter.class);
        assertThat(adapters).extracting(StatementAdapter::provider).doesNotHaveDuplicates();
    }
}
