package org.eclipse.tractusx.edc.audit.sql.sinks.postgres;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.tractusx.edc.spi.audit.sink.AuditSink;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEvent;
import org.eclipse.tractusx.edc.spi.audit.statements.AuditEventStatements;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;

/**
 * The PostgreSQL Audit Sink
 * Implements Audit Sink, persists canonical audit events to the audit_table (created via flyway migration)
 */
public class PostgresqlAuditSink implements AuditSink {
    private final DataSourceRegistry dataSourceRegistry;
    private final TransactionContext transactionContext;
    private final AuditEventStatements auditEventStatements;
    private final Monitor monitor;
    private final String dataSourceName;

    public PostgresqlAuditSink
            (
                    DataSourceRegistry dataSourceRegistry,
                    String dataSourceName,
                    TransactionContext transactionContext,
                    AuditEventStatements auditEventStatements,
                    Monitor monitor
            ) {
        this.dataSourceRegistry = dataSourceRegistry;
        this.transactionContext = transactionContext;
        this.auditEventStatements = auditEventStatements;
        this.monitor = monitor;
        this.dataSourceName = dataSourceName;
    }

    @Override
    public void write(AuditEvent event) {
        DataSource dataSource = dataSourceRegistry.resolve(dataSourceName);

        transactionContext.execute(() -> {
            try (
                    Connection connection = dataSource.getConnection();
                    PreparedStatement preparedStatement = connection.prepareStatement(
                            auditEventStatements.insertAuditEvent())
            ) {
                preparedStatement.setString(1, event.getId());
                preparedStatement.setTimestamp(2, Timestamp.from(event.getTimestamp()
                        .atOffset(ZoneOffset.UTC)
                        .toInstant())
                ); // TIMESTAMPTZ in Postgres, from Instant (UTC)
                preparedStatement.setString(3, event.getCategory().name());
                preparedStatement.setString(4, event.getEventName().name());
                preparedStatement.setString(5, event.getOutcome().name());
                // Nullable fields
                preparedStatement.setString(6, event.getSubjectId());
                preparedStatement.setString(7, event.getActorId());
                preparedStatement.setString(8, event.getDescription());

                preparedStatement.executeUpdate();
            } catch (SQLException s){
                monitor.warning("[PostgresAuditSink] SQL error persisting audit event " + event.getId(), s);
            }  catch (Exception e) {
                monitor.warning("[PostgresAuditSink] Failed to persist audit event " + event.getId(), e);
            }
        });
    }
}

