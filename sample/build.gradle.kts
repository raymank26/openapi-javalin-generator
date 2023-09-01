plugins {
    kotlin("jvm") version "1.9.0"
    id("com.github.raymank26.javalin-swagger")
}

repositories {
    mavenCentral()
}

tasks.create("foo") {
    dependsOn("generateSwaggerClient")
}


kotlin {
    jvmToolchain(8)
}