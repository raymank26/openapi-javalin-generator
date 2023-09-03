plugins {
    kotlin("jvm") version "1.9.0"
    `java-gradle-plugin`
}

group = "com.github.raymank26"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val functionalTest: SourceSet = sourceSets.create("functionalTest")

val functionalTestTask = tasks.register<Test>("functionalTest") {
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.check {
    dependsOn(functionalTestTask)
}

gradlePlugin {
    // Define the plugin
    val swaggerGenerator by plugins.creating {
        id = "com.github.raymank26.javalin-swagger"
        implementationClass = "com.github.raymank26.JavalinSwaggerPlugin"
    }
    testSourceSets(functionalTest)

}

dependencies {
    implementation("io.swagger.parser.v3:swagger-parser:2.1.16")
    implementation("com.squareup:kotlinpoet:1.14.2")


    "functionalTestImplementation"("org.junit.jupiter:junit-jupiter:5.10.0")
    "functionalTestImplementation"("org.junit.jupiter:junit-jupiter-api:5.10.0")
    "functionalTestImplementation"("org.junit.jupiter:junit-jupiter-params:5.10.0")
    "functionalTestImplementation"(project)
    "functionalTestImplementation"(gradleTestKit())
    "functionalTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(8)
}
