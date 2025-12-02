package org.eclipse.tractusx.edc.spi.audit.statements;

/**
 * The Audit Statements
 * SQL statements interface for persisting AuditEvents
 * allows for different implementations per database type
 */
public interface AuditEventStatements {

    /**
     * SQL statement to insert a single audit event row
     */
    String insertAuditEvent();
}

