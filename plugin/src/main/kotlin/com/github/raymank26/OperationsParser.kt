package com.github.raymank26

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses

class OperationsParser(private val spec: OpenAPI) {

    private val refsBuilder = RefsBuilder()

    fun parseSpec(): SpecMetadata {
        val operations = spec.paths.map { path ->
            val (method, operationGetter) = when {
                path.value.get != null -> "get" to { path.value.get }
                path.value.post != null -> "post" to { path.value.post }
                else -> error("Not supported")
            }
            parseOperation(path.key, operationGetter.invoke(), method)
        }
        return SpecMetadata(operations, refsBuilder.build())
    }

    private fun parseOperation(
        path: String,
        operation: Operation,
        method: String,
    ): OperationDescriptor {
        return OperationDescriptor(
            path = path,
            method = method,
            summary = operation.summary,
            operationId = operation.operationId,
            paramDescriptors = parseParameters(operation.parameters),
            requestBody = parseRequestBody(operation.requestBody),
            responseBody = parseResponses(operation.responses),
        )
    }

    private fun parseRequestBody(requestBody: io.swagger.v3.oas.models.parameters.RequestBody?): RequestBody? {
        if (requestBody == null) {
            return null
        }
        TODO("Not yet implemented")
    }

    private fun parseResponses(responses: ApiResponses): List<TypeDescriptor> {
        return responses.map { (_, response) ->
            val ref = response.content["application/json"]!!.schema.`$ref`
            val schema = spec.components.schemas[ref.split("/").last()]!!
            val descriptor = parseTypeDescriptor(ref, response, schema)
            descriptor
        }
    }

    private fun parseTypeDescriptor(ref: String, response: ApiResponse, schema: Schema<Any>): TypeDescriptor {
        val result = when (schema.type) {
            "array" -> {
                val itemRef = schema.items.`$ref`!!
                val arraySchema = spec.components.schemas[itemRef.split("/").last()]!!
                TypeDescriptor.Array(parseTypeDescriptor(itemRef, response, arraySchema))
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
        refsBuilder.addRef(ref, result)
        return result
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
                typePropertyDescriptor = getTypePropertyDescriptor(
                    parameter.name,
                    parameter.schema,
                    parameter.required
                )
            )
        }
    }
}

data class SpecMetadata(
    val operations: List<OperationDescriptor>,
    val refs: Map<String, TypeDescriptor>,
)