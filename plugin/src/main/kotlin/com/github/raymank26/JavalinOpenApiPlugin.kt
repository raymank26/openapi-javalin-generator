package com.github.raymank26

import io.swagger.parser.OpenAPIParser
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.nio.file.Paths

@Suppress("unused")
class JavalinOpenApiPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions.create("javalinSwagger", JavalinOpenApiPluginExtension::class.java)

        target.tasks.create("generateOpenApiClasses") {
            it.doLast {
                val basePackageName = extension.basePackageName.get()
                val result = File("${target.projectDir}/openapi/spec.yml")
                    .reader()
                    .use {
                        OpenAPIParser().readContents(it.readText(), null, null)
                    }

                val spec = result.openAPI
                val operationsParser = OperationsParser(spec)
                val specMetadata = operationsParser.parseSpec()
                val baseGenerationPath = target.buildDir.toPath()
                    .resolve(Paths.get("generated", "main", "kotlin"))

                val typesGenerator = TypesGenerator(
                    specMetadata = specMetadata,
                    basePackageName = basePackageName,
                    baseGenPath = baseGenerationPath
                )
                typesGenerator.generateTypes()

                val okHttpClientInterfaceGenerator = OkHttpClientInterfaceGenerator(
                    specMetadata = specMetadata,
                    basePackageName = basePackageName,
                    baseGenerationPath = baseGenerationPath
                )
                okHttpClientInterfaceGenerator.generateClient()

                val serverInterfaceGenerator = ServerInterfaceGenerator(
                    specMetadata = specMetadata,
                    basePackageName = basePackageName,
                    baseGenerationPath = baseGenerationPath
                )
                serverInterfaceGenerator.generate()

                val javalinControllerGenerator = JavalinControllerGenerator(
                    specMetadata = specMetadata,
                    basePackageName = basePackageName,
                    baseGenerationPath = baseGenerationPath
                )
                javalinControllerGenerator.generate()
            }
        }
    }
}