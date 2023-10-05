package com.github.raymank26

import io.swagger.parser.OpenAPIParser
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.nio.file.Paths

@Suppress("unused")
class JavalinOpenApiPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("javalinOpenApi", JavalinOpenApiPluginExtension::class.java)

        project.tasks.create("generateOpenApiClasses") {
            it.doLast {
                for (outputTarget in extension.targets) {
                    processOutputTarget(project, outputTarget)
                }
            }
        }
    }

    private fun processOutputTarget(project: Project, outputTarget: OutputTarget) {
        val basePackageName = outputTarget.basePackageName.get()
        val specName = outputTarget.specName.getOrElse("spec.yml")
        val result = File("${project.projectDir}/openapi/$specName")
            .reader()
            .use {
                OpenAPIParser().readContents(it.readText(), null, null)
            }

        val spec = result.openAPI
        val operationsParser = OperationsParser(spec)
        val specMetadata = operationsParser.parseSpec()

        val generateBasePath = Paths.get("generated", "main", "kotlin")
        val baseGenerationPath = project.buildDir.toPath()
            .resolve(generateBasePath)

        val packagePath = outputTarget.basePackageName.get().replace('.', '/')
        project.delete(baseGenerationPath.resolve(packagePath))

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

        if (outputTarget.generateServerCode.getOrElse(true)) {
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