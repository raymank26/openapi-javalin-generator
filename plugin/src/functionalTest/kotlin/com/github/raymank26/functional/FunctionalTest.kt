package com.github.raymank26.functional

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.outputStream

class FunctionalTest {

    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun before() {
        Files.createDirectory(projectDir.toPath().resolve("openapi"))
        projectDir.toPath().resolve(Paths.get("openapi", "spec.yml")).outputStream().use { os ->
            FunctionalTest::class.java.getResourceAsStream("/spec.yml")!!.use {
                it.transferTo(os)
            }
        }
    }

    @Test
    fun shouldGenerateClasses() {
        File(projectDir, "build.gradle").writer().use { writer ->
            writer.write(
                """
                plugins {
                    id 'com.github.raymank26.javalin-openapi'
                }
                
                javalinOpenApi {
                    targets {
                        sampleTarget {
                            basePackageName = "foo"
                        }
                    }
                    
                }
            """.trimIndent()
            )
        }
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generateOpenApiClasses")
            .forwardOutput()
            .withPluginClasspath()
            .build()
    }
}