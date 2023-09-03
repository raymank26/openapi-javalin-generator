package com.github.raymank26

import io.swagger.parser.OpenAPIParser
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

class JavalinSwaggerPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.tasks.create("generateSwaggerClient") {

            val result = File("${target.projectDir}/swagger/spec.yml")
                .reader()
                .use {
                    OpenAPIParser().readContents(it.readText(), null, null)
                }

            val spec = result.openAPI
            val operations = spec.paths.map { path ->
                val (method, operationGetter) = when {
                    path.value.get != null -> "get" to { path.value.get }
                    path.value.post != null -> "post" to { path.value.post }
                    else -> error("Not supported")
                }
                parseOperation(path.key, operationGetter.invoke(), method, spec)

            }
            println("HERE")
        }
    }

    private fun parseOperation(path: String, operation: Operation, method: String, spec: OpenAPI): OperationDescriptor {
        return OperationDescriptor(
            path = path,
            method = method,
            summary = operation.summary,
            operationId = operation.operationId,
            paramDescriptors = parseParameters(operation.parameters),
            requestBody = parseRequestBody(operation.requestBody),
            responseBody = parseResponses(operation.responses, spec),
        )
    }

    private fun parseRequestBody(requestBody: io.swagger.v3.oas.models.parameters.RequestBody?): RequestBody? {
        if (requestBody == null) {
            return null
        }
        TODO("Not yet implemented")
    }

    private fun parseResponses(responses: ApiResponses, spec: OpenAPI): List<TypeDescriptor> {
        return responses.map { (_, response) ->
            val ref = response.content["application/json"]!!.schema.`$ref`
            val schema = spec.components.schemas[ref.split("/").last()]!!
            parseTypeDescriptor(response, spec, schema)
        }
    }

    private fun parseTypeDescriptor(response: ApiResponse, spec: OpenAPI, schema: Schema<Any>): TypeDescriptor {
        return when (schema.type) {
            "array" -> {
                val arraySchema = spec.components.schemas[schema.items.`$ref`.split("/").last()]!!
                TypeDescriptor.Array(parseTypeDescriptor(response, spec, arraySchema))
            }

            "object" -> {
                val requiredProperties = schema.required.toSet()
                val properties = schema.properties.map { (name, property) ->
                    getTypePropertyDescriptor(name, property, requiredProperties.contains(name))
                }
                TypeDescriptor.Object(properties)
            }

            else -> error("not supported type = " + schema.type)
        }
    }

    private fun getTypePropertyDescriptor(
        name: String,
        property: Schema<Any>,
        required: Boolean?,
    ) = TypePropertyDescriptor(
        name = name,
        type = getPropertyType(property.type),
        format = property.format,
        required = required ?: false
    )

    private fun getPropertyType(type: String): TypeDescriptor {
        return when (type) {
            "integer" -> TypeDescriptor.IntType
            "string" -> TypeDescriptor.StringType
            else -> error("not supported type = $type")
        }
    }

    private fun parseParameters(parameters: List<Parameter>): List<ParamDescriptor> {
        return parameters.map { parameter ->
            ParamDescriptor(
                name = parameter.name,
                place = parameter.`in`,
                typePropertyDescriptor = getTypePropertyDescriptor(parameter.name, parameter.schema, parameter.required)
            )
        }
    }
}