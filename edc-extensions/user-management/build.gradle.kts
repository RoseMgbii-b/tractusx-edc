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
    implementation(libs.edc.spi.boot)
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.web)
    implementation(libs.jakarta.rsApi)
    implementation(libs.jakartaJson)
    implementation(libs.okhttp)
    implementation(libs.jakartaAnnotation)
    implementation(libs.nimbus.jwt)
    implementation(libs.keycloak)
    implementation(libs.rest.easy.client)
    implementation(libs.rest.easy.client.api)
    implementation(libs.rest.easy.client.jackson)

    testImplementation(libs.edc.junit)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly(libs.jersey.common)
}

