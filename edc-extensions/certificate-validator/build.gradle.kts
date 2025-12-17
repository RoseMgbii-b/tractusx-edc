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
    implementation(libs.edc.spi.http)
    implementation(libs.edc.spi.web)
    
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

