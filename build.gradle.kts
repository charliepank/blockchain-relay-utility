plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("maven-publish")
    jacoco
}

group = "com.utility"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux") // For reactive HTTP client
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    
    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    
    // Web3j for blockchain interaction
    implementation("org.web3j:core:4.12.2")
    
    // OpenAPI/Swagger documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("io.projectreactor:reactor-test:3.6.10")
    
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

jacoco {
    toolVersion = "0.8.11"
}

// Configure publishing for JitPack
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            // JitPack requires proper artifact configuration
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            
            pom {
                name.set("Blockchain Transaction Relay Utility")
                description.set("A generic Kotlin/Spring Boot utility for blockchain transaction relaying with gas management and pluggable business logic")
                url.set("https://github.com/charliep/blockchain-relay-utility")
                
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        id.set("charliep")
                        name.set("Charlie P")
                        email.set("charlie@example.com")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/charliep/blockchain-relay-utility.git")
                    developerConnection.set("scm:git:ssh://github.com:charliep/blockchain-relay-utility.git")
                    url.set("https://github.com/charliep/blockchain-relay-utility")
                }
            }
        }
    }
}

// JitPack requires the jar task to be available
tasks.jar {
    enabled = true
    archiveClassifier = ""
}

// Disable bootJar since this is a library, not an application
tasks.bootJar {
    enabled = false
}