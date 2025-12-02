package org.eclipse.tractusx.edc.audit.statements;

import org.junit.jupiter.api.Test;
import org.eclipse.tractusx.edc.audit.sql.statement.PostgresAuditEventStatements;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Post
 */
public class PostgresAuditEventStatementsTest {
    @Test
    void insertAuditEvent_contains_expected_table_and_columns() {
        var statements = new PostgresAuditEventStatements();
        var sql = statements.insertAuditEvent();

        assertThat(sql).containsIgnoringCase("INSERT INTO audit_events");
        assertThat(sql).containsIgnoringCase("id,");
        assertThat(sql).containsIgnoringCase("timestamp,");
        assertThat(sql).containsIgnoringCase("category,");
        assertThat(sql).containsIgnoringCase("event_name,");
    }
}
