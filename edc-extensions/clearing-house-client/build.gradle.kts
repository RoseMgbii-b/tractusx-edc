/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

plugins {
    `java-library`
}

dependencies {
    implementation(project(":core:core-utils"))
    implementation(project(":spi:clearing-house-spi"))
    implementation(project(":spi:bdrs-client-spi"))
    implementation(project(":edc-extensions:certificate-validator"))

    implementation(libs.edc.spi.boot)
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.http)
    implementation(libs.edc.spi.web)
    implementation(libs.edc.spi.boot)
    implementation(libs.edc.spi.policyengine)
    implementation(libs.edc.spi.contract)
    implementation(libs.edc.spi.controlplane)
    implementation(libs.edc.spi.transactionspi)
    implementation(libs.edc.core.policy.monitor)
    implementation(libs.edc.lib.store)
    implementation(libs.edc.lib.query)
    implementation(libs.edc.spi.policy)
    implementation(libs.edc.spi.transfer)


    implementation(libs.edc.spi.transaction.datasource)
    implementation(libs.edc.spi.transactionspi)


    // Certificate and cryptographic libraries
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    // XML processing for EU Trust List
    implementation("org.apache.santuario:xmlsec:3.0.3")

    // HTTP client
    implementation(libs.okhttp)

    // JSON processing
    implementation(libs.jakartaJson)

    testImplementation(libs.edc.junit)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation("org.assertj:assertj-core:3.24.2")
    testRuntimeOnly(libs.jersey.common)
}

