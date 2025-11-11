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
    implementation(libs.jakartaAnnotation)
    
    // Keycloak Admin Client
    implementation("org.keycloak:keycloak-admin-client:25.0.0")
    implementation("org.jboss.resteasy:resteasy-client:6.2.8.Final")
    implementation("org.jboss.resteasy:resteasy-jackson2-provider:6.2.8.Final")
}

