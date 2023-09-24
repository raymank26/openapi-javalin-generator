package com.github.raymank26

import io.swagger.parser.OpenAPIParser
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.nio.file.Paths

class JavalinSwaggerPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions.create("javalinSwagger", JavalinSwaggerPluginExtension::class.java)

        target.tasks.create("generateSwaggerClient") {
            it.doLast {
                val basePackageName = extension.basePackageName.get()
                val result = File("${target.projectDir}/swagger/spec.yml")
                    .reader()
                    .use {
                        OpenAPIParser().readContents(it.readText(), null, null)
                    }

                val spec = result.openAPI
                val operationsParser = OperationsParser(spec)
                val specMetadata = operationsParser.parseSpec()
                val typesGenerator = TypesGenerator(
                    specMetadata, basePackageName, target.buildDir.toPath()
                        .resolve(Paths.get("generated", "main", "kotlin"))
                )
                typesGenerator.generateTypes()
                val okHttpClientInterfaceGenerator = OkHttpClientInterfaceGenerator()
//            okHttpClientInterfaceGenerator.generate("spec", operations)
            }
        }
    }
}