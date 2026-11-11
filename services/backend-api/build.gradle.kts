plugins {
    java
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "aerocore"
version = "1.0.0"

tasks.withType<JavaCompile> {
    // Target the LTS release; any installed JDK >= 21 can run the build.
    options.release = 21
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")
    runtimeOnly("com.microsoft.sqlserver:mssql-jdbc:12.8.1.jre11")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Only the executable (boot) jar is shipped
tasks.named<Jar>("jar") { enabled = false }

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Regenerates ../../contracts/{public,admin}.openapi.json from the controllers (the `test` task only verifies them).
tasks.register<Test>("exportApiDocs") {
    group = "documentation"
    description = "Writes the OpenAPI documents of the public and admin API to contracts/"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("aerocore.backend.OpenApiContractTest") }
    systemProperty("contracts.update", "true")
    outputs.upToDateWhen { false }
}
