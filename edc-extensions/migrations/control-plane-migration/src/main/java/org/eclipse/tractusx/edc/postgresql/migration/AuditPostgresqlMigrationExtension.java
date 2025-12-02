package org.eclipse.tractusx.edc.postgresql.migration;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;

/**
 * Audit PostgreSQL Migration Extension for automatic table migration
 */
@Extension("Audit PostgreSQL Migration Extension")
public class AuditPostgresqlMigrationExtension extends AbstractPostgresqlMigrationExtension {

    private static final String NAME_SUBSYSTEM = "audit_events";

    @Override
    protected String getSubsystemName() {
        return NAME_SUBSYSTEM;
    }
}
