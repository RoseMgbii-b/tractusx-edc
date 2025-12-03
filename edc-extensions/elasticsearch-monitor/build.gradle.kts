plugins {
    `maven-publish`
    `java-library`
}

dependencies {

    implementation(project(":spi:audit-spi"))
    implementation(libs.edc.spi.boot)
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.controlplane)

    // Needed for registering extensions
    implementation(libs.edc.spi.boot)

    // Required for Elasticsearch HTTP client
    implementation(libs.okhttp)
    implementation(libs.edc.spi.http)

    annotationProcessor("org.eclipse.edc:runtime-metamodel:0.14.1")

    testImplementation(libs.netty.mockserver)
    testImplementation(libs.edc.junit)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.edc.spi.http)
    testImplementation(libs.edc.junit)

    testImplementation(libs.edc.api.management)
    testImplementation(libs.edc.core.controlplane)
    testImplementation(libs.edc.core.runtime)
    testImplementation(libs.edc.api.core)

    tasks.test {
        useJUnitPlatform()
    }



}
