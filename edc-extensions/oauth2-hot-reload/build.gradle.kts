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
    implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")
//    implementation(libs.jakarta.jakartaAnnotation)
}

