import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion: String by project
val kotlinCoroutinesVersion: String by project
val exposedVersion: String by project
val ktorVersion: String by project
val jacksonVersion: String by project

plugins {
    id("org.openapi.generator") version "7.27.0"
    id("org.springframework.boot") version "4.2.0"
    id("io.spring.dependency-management") version "1.1.7"
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

group = "net.djvk"
version = "1.5.1"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.bundles.openapi)
    implementation(libs.ktor.cio)
    implementation(libs.ktor.logging)
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.semver4j:semver4j:5.3.0")
    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.mock)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.4.0")
}

val generatePlaidClient = tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("generatePlaidClient") {
    generatorName.set("kotlin")
    inputSpec.set(layout.projectDirectory.dir("specs").file("plaid-2020-09-14.yml").toString())
    cleanupOutput.set(true)
    outputDir.set(layout.buildDirectory.dir("generated-plaid"))
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
            kotlin.srcDir(generatePlaidClient.flatMap { it.outputDir.dir("src/main/kotlin") })
        }
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_17)
    }
    dependsOn(generatePlaidClient)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val smokeTestJar = providers.gradleProperty("smokeTestJar").isPresent
val jarResources = setOf(
    "application.yml",
    "META-INF/spring-configuration-metadata.json",
    "smoke/bootstrap.php",
    "smoke/compose.yml",
) + if (smokeTestJar) emptySet() else setOf(
    "application-dev.yml",
    "application-prod.yml",
    "application-test.yml",
)

val processedResourcesDir = requireNotNull(sourceSets.main.get().output.resourcesDir)
tasks.withType<Jar>().configureEach {
    // Apply the allowlist at packaging time so local profiles are still available to bootRun.
    exclude { entry ->
        !entry.isDirectory && entry.file.startsWith(processedResourcesDir) &&
            entry.file.relativeTo(processedResourcesDir).invariantSeparatorsPath !in jarResources
    }
}

if (smokeTestJar) {
    tasks.processResources {
        exclude("**/application-*.yml", "**/application-*.yaml", "**/application-*.properties")
    }
    tasks.bootJar {
        destinationDirectory.set(layout.buildDirectory.dir("smoke"))
        archiveFileName.set("connector.jar")
    }
}
