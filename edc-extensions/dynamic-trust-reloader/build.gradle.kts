plugins {
    `maven-publish`
    `java-library`
}

dependencies {
    implementation(project(":core:core-utils"))
    implementation(project(":spi:bdrs-client-spi"))
    implementation(project(":spi:core-spi"))
    implementation(libs.edc.spi.boot)
    implementation(libs.edc.spi.core)

    api(libs.edc.spi.controlplane)
    implementation(libs.edc.lib.validator)

    annotationProcessor("org.eclipse.edc:runtime-metamodel:0.14.1")

    testImplementation(libs.netty.mockserver)
    testImplementation(libs.edc.junit)
    testImplementation(libs.testcontainers.junit)

}
