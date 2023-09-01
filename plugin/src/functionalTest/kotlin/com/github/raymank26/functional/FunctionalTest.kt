package com.github.raymank26.functional

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FunctionalTest {

    @TempDir
    lateinit var projectDir: File

    @Test
    fun shouldGenerateServerInterfaces() {
        File(projectDir, "build.gradle").writer().use { writer ->
            writer.write(
                """
                plugins {
                    id 'com.github.raymank26.javalin-swagger'
                }
            """.trimIndent()
            )

        }
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generateSwaggerClient")
            .forwardOutput()
            .withPluginClasspath()
            .build()
    }
}