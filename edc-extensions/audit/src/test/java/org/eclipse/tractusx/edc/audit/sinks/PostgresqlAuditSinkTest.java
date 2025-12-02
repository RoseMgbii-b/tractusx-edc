package org.eclipse.tractusx.edc.audit.sinks;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEvent;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEventCategory;
import org.eclipse.tractusx.edc.spi.audit.types.AuditEventName;
import org.eclipse.tractusx.edc.spi.audit.types.AuditOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.eclipse.tractusx.edc.spi.audit.statements.AuditEventStatements;
import org.eclipse.tractusx.edc.audit.sql.sinks.postgres.PostgresqlAuditSink;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class PostgresqlAuditSinkTest {
    private DataSourceRegistry dataSourceRegistry = mock(DataSourceRegistry.class);
    private DataSource dataSource = mock(DataSource.class);
    private Connection connection = mock(Connection.class);
    private PreparedStatement preparedStatement = mock(PreparedStatement.class);
    private TransactionContext transactionContext = mock(TransactionContext.class);
    private Monitor monitor = mock(Monitor.class);
    private AuditEventStatements statements = mock(AuditEventStatements.class);

    @BeforeEach
    void setup() throws Exception {

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(dataSourceRegistry.resolve(anyString())).thenReturn(dataSource);

        doAnswer(invocation -> {
            var block = invocation.getArgument(0, TransactionContext.TransactionBlock.class);
            block.execute();
            return null;
        }).when(transactionContext).execute(any(TransactionContext.TransactionBlock.class));

        when(statements.insertAuditEvent()).thenReturn("INSERT INTO audit_events (...) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
    }

    @Test
    void write_inserts_event_using_prepared_statement() throws Exception {
        // Arrange
        Instant now = Instant.now();

        var mockedEvent = mock(AuditEvent.class);
        when(mockedEvent.getId()).thenReturn("id-1");
        when(mockedEvent.getTimestamp()).thenReturn(now);
        when(mockedEvent.getCategory()).thenReturn(AuditEventCategory.ASSET);
        when(mockedEvent.getEventName()).thenReturn(AuditEventName.ASSET_CREATED);
        when(mockedEvent.getOutcome()).thenReturn(AuditOutcome.SUCCESS);
        when(mockedEvent.getSubjectId()).thenReturn("subject");
        when(mockedEvent.getActorId()).thenReturn("actor");
        when(mockedEvent.getDescription()).thenReturn("desc");

        // Mocks

        when(dataSourceRegistry.resolve("aud")).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);

        // Sink
        var sink = new PostgresqlAuditSink(
                dataSourceRegistry,
                "aud",
                transactionContext,
                statements,
                monitor
        );

        // Act
        sink.write(mockedEvent);

        // Assert: verify prepared statement was created and executed, and resources closed
        InOrder inOrder = inOrder(connection, preparedStatement);

        inOrder.verify(connection).prepareStatement(anyString());
        verify(preparedStatement).setString(eq(1), eq("id-1"));
        verify(preparedStatement).executeUpdate();
        verify(preparedStatement).close();
        verify(connection).close();
    }

}
