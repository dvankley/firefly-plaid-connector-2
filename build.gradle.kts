import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Paths

val kotlinVersion: String by project
val kotlinCoroutinesVersion: String by project
val exposedVersion: String by project
val ktorVersion: String by project
val jacksonVersion: String by project

plugins {
    id("org.openapi.generator") version "7.7.0"
    id("org.springframework.boot") version "3.3.1"
    id("io.spring.dependency-management") version "1.1.5"
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
}

group = "net.djvk"
version = "1.2.0"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:2.3.12")
    implementation("io.ktor:ktor-client-cio-jvm:2.3.12")
    implementation("io.ktor:ktor-client-logging-jvm:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.semver4j:semver4j:5.3.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
    testImplementation("org.assertj:assertj-core:3.26.0")
    testImplementation("io.ktor:ktor-client-mock-jvm:2.3.12")
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
