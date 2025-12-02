/********************************************************************************
 * Copyright [...]
 ********************************************************************************/

plugins {
    `java-library`
    id("application")
    alias(libs.plugins.shadow)
}

configurations.all {
    exclude(group = "org.eclipse.edc", module = "target-node-directory-sql")
}

dependencies {
    // Base EDC Controlplane + Dataplane
    runtimeOnly(project(":edc-controlplane:edc-controlplane-base"))
    runtimeOnly(project(":edc-dataplane:edc-dataplane-base")) {
        exclude("org.eclipse.edc", "data-plane-selector-client")
    }
    // IMPORTANT: Seed tx.edc.vault.secrets into InMemoryVault
    runtimeOnly(project(":edc-controlplane:edc-runtime-memory"))


    // TOTAL SQL BOM (FIXES QueryExecutor / schema / dialect!)
    runtimeOnly(libs.edc.bom.controlplane.feature.sql)
    runtimeOnly(libs.edc.bom.federatedcatalog.feature.sql)

    // SQL store modules needed by your audit, agreements, etc.
    runtimeOnly(project(":edc-extensions:agreements:retirement-evaluation-store-sql"))
    runtimeOnly(project(":edc-extensions:agreements-bpns:bpns-evaluation-store-sql"))
    runtimeOnly(project(":edc-extensions:bpn-validation:business-partner-store-sql"))
    runtimeOnly(project(":edc-extensions:edr:edr-index-lock-sql"))


    // SQL migration
    runtimeOnly(project(":edc-extensions:migrations:control-plane-migration"))
    runtimeOnly(project(":edc-extensions:migrations:postgresql-migration-lib"))

    // YOUR EXTENSIONS
    implementation(project(":edc-extensions:audit"))
    implementation(project(":edc-extensions:dynamic-trust-reloader"))
    implementation(project(":core:core-utils"))
    implementation(libs.edc.spi.core)

    // JDBC driver
    runtimeOnly("org.postgresql:postgresql:${libs.versions.postgres.get()}")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    mergeServiceFiles()
    archiveFileName.set("${project.name}.jar")
    transform(com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer())
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")

    applicationDefaultJvmArgs = listOf(
        "-Dedc.fs.config=${project.rootDir}/configuration/config.properties",
        "-Dedc.keystore=",
        "-Dedc.keystore.password="
    )
}
