plugins {
    kotlin("jvm") version "1.9.0"
    `java-gradle-plugin`
    `maven-publish`
}

group = "com.github.raymank26"
version = project.findProperty("version")?.takeIf { it != "unspecified" } ?: "1.1-SNAPSHOT"

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
    val openApiGenerator by plugins.creating {
        id = "com.github.raymank26.javalin-openapi"
        implementationClass = "com.github.raymank26.JavalinOpenApiPlugin"
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
    jvmToolchain(11)
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

configure<PublishingExtension> {
    repositories {
        val customMavenUrl = findProperty("customMavenUrl")
        if (customMavenUrl != null) {
            maven {
                name = "customMaven"
                url = uri(customMavenUrl)
                credentials {
                    username = project.findProperty("customMavenUrlUsername").toString()
                    password = project.findProperty("customMavenUrlPassword").toString()
                }
            }
        }
    }
    publications {
        register<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "javalin-openapi"
            version = project.version.toString()
            artifact(sourcesJar.get())
            from(components["java"])
        }
    }
}

