import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Paths

val kotlinVersion: String by project
val kotlinCoroutinesVersion: String by project
val exposedVersion: String by project
val ktorVersion: String by project
val jacksonVersion: String by project

plugins {
    id("org.openapi.generator") version "7.7.0"
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

group = "net.djvk"
version = "1.2.0"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.bundles.openapi)
    implementation(libs.ktor.cio)
    implementation(libs.ktor.logging)
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.semver4j:semver4j:5.3.0")
    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.mock)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

var generatePlaidClient = tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("generatePlaidClient") {
    generatorName.set("kotlin")
    inputSpec.set(layout.projectDirectory.dir("specs").file("plaid-2020-09-14.yml").toString())
    cleanupOutput.set(true)
    outputDir.set(layout.buildDirectory.dir("generated-plaid").get().toString())
    apiPackage.set("net.djvk.fireflyPlaidConnector2.api.plaid.apis")
    invokerPackage.set("net.djvk.fireflyPlaidConnector2.api.plaid.invoker")
    modelPackage.set("net.djvk.fireflyPlaidConnector2.api.plaid.models")
    globalProperties.put("modelDocs", "false")
    globalProperties.put("apiDocs", "false")
    globalProperties.put("modelTests", "false")
    globalProperties.put("apiTests", "false")
    configOptions.put("groupId", "net.djvk")
    configOptions.put("packageName", "net.djvk.fireflyPlaidConnector2.api.plaid")
    configOptions.put("library", "jvm-ktor")
    configOptions.put("dateLibrary", "java8")
    configOptions.put("serializationLibrary", "jackson")
    configOptions.put("additionalModelTypeAnnotations", "@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)")
    configOptions.put("requestDateConverter", "toString")
    configOptions.put("typeMappings", "BigDecimal=Double")
    configOptions.put("omitGradleWrapper", "true")
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir(Paths.get(generatePlaidClient.get().outputDir.get(), "src", "main", "kotlin"))
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
    dependsOn(generatePlaidClient)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
