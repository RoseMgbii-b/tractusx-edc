/********************************************************************************
 * Copyright (c) 2025 Contributors
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

plugins {
    `maven-publish`
    `java-library`
}

dependencies {
    // EDC Core
    implementation(libs.edc.runtime.metamodel)
    implementation(libs.edc.spi.boot)
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.web)
    implementation(libs.edc.spi.controlplane)
    implementation(libs.edc.core.connector)
    implementation(libs.edc.core.controlplane)

    // JWT and VC
    implementation(libs.edc.spi.jwt)
    implementation(libs.edc.spi.jwt.signer)
    implementation(libs.edc.spi.vc)
    implementation(libs.edc.spi.identitytrust)
    implementation(libs.nimbus.jwt)

    // Policy
    implementation(libs.edc.spi.policyengine)
    implementation(libs.edc.spi.policy)
    implementation(libs.edc.spi.contract)

    // Existing components
    implementation(project(":edc-extensions:clearing-house-client"))
    implementation(project(":core:core-utils"))

    // JSON
    implementation(libs.jakartaJson)

    // JAX-RS API
    implementation(libs.jakarta.rsApi)

    // HTTP
    implementation(libs.okhttp)

    testImplementation(libs.edc.junit)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation("org.assertj:assertj-core:3.24.2")
}