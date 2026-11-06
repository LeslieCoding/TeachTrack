plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.leslie.teachtrack"
version = "4.0.0-alpha.1"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // S3-compatible object storage (R2 / MinIO / OSS / COS via S3 API)
    implementation("software.amazon.awssdk:s3:2.29.45")

    // Practice export: DOCX + PDF (CJK font is read from the host, not bundled)
    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("com.github.librepdf:openpdf:2.0.3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("file.encoding", "UTF-8")
}
