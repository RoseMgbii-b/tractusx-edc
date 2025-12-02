package org.eclipse.tractusx.edc.audit.sql.statement;

import org.eclipse.tractusx.edc.spi.audit.statements.AuditEventStatements;

/**
 * The Postgres Audit Statements
 * PostgreSQL-specific implementation of AuditEventStatements
 * provides SQL statements tailored for PostgreSQL database (created by flyway migration)
 */
public class PostgresAuditEventStatements implements AuditEventStatements {
    @Override
    public String insertAuditEvent() {
        return """
                INSERT INTO audit_events (
                id,
                timestamp,
                category,
                event_name,
                outcome,
                subject_id,
                actor_id,
                description
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
    }
}



