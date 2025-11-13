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
//    implementation("org.keycloak:keycloak-admin-client:25.0.0")
    implementation(libs.keycloak)
//    implementation("org.jboss.resteasy:resteasy-client:6.2.9.Final")
    implementation(libs.rest.easy.client)
//    implementation("org.jboss.resteasy:resteasy-client-api:6.2.9.Final")
    implementation(libs.rest.easy.client.api)
//    implementation("org.jboss.resteasy:resteasy-jackson2-provider:6.2.9.Final")
    implementation(libs.rest.easy.client.jackson)
}

