import com.github.raymank26.JavalinSwaggerPluginExtension

plugins {
    kotlin("jvm") version "1.9.0"
    id("com.github.raymank26.javalin-swagger")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
}

tasks.create("foo") {
    dependsOn("generateSwaggerClient")
}

kotlin {
    jvmToolchain(8)
}

configure<JavalinSwaggerPluginExtension> {
    basePackageName.set("foo")
}

sourceSets {
    main {
        kotlin {
            srcDir("$buildDir/generated/main/kotlin")
        }
    }
}