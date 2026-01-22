/********************************************************************************
 * Copyright (c) 2021,2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ********************************************************************************/

plugins {
    `java-library`
    id("application")
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":edc-dataplane:edc-dataplane-base"))

    runtimeOnly(libs.edc.bom.dataplane.feature.sql)

    runtimeOnly(project(":edc-extensions:migrations::data-plane-migration"))

    runtimeOnly(libs.edc.vault.hashicorp)
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
        "-Dedc.keystore.password=",
        // Data Plane specific ports (different from control plane to avoid conflicts)
        // Note: Data plane does NOT need protocol port - that's only for control plane DSP
        // These ports must be different from edc-runtime-memory which uses: 8181, 8185, 8186
        "-Dweb.http.port=28090",
        "-Dweb.http.path=/api",
        "-Dweb.http.control.port=9999",
        "-Dweb.http.control.path=/control",
        "-Dweb.http.public.port=8187",
        "-Dweb.http.public.path=/api/public"
        // Protocol port intentionally NOT set - data plane doesn't need DSP protocol endpoint
        // If extensions try to use it, they'll fail gracefully or use defaults
    )
}
