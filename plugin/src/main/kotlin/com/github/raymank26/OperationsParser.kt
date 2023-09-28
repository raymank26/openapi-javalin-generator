package com.github.raymank26

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.responses.ApiResponses
import org.gradle.configurationcache.extensions.capitalized

class OperationsParser(private val spec: OpenAPI) {

    private val refsBuilder = RefsBuilder()

    fun parseSpec(): SpecMetadata {
        val operations = spec.paths.flatMap { path ->
            val pathOperations = mutableListOf<OperationDescriptor>()
            if (path.value.get != null) {
                pathOperations.add(parseOperation(path.key, path.value.get, "get"))
            }
            if (path.value.post != null) {
                pathOperations.add(parseOperation(path.key, path.value.post, "post"))
            }
            if (path.value.put != null) {
                pathOperations.add(parseOperation(path.key, path.value.put, "put"))
            }
            if (path.value.delete != null) {
                pathOperations.add(parseOperation(path.key, path.value.put, "delete"))
            }
            pathOperations
        }
        return SpecMetadata(spec.info.title, operations, refsBuilder.build())
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
            paramDescriptors = parseParameters(operation.parameters ?: emptyList()),
            requestBody = parseRequestBody(operation),
            responseBody = parseResponses(operation, operation.responses),
        )
    }

    private fun parseRequestBody(operation: Operation): RequestBody? {
        val requestBody = operation.requestBody ?: return null
        val required = requestBody.required ?: false
        val definitions = requestBody.content.mapNotNull { (mediaType, definition) ->
            val ref = definition.schema.`$ref`
            if (definition.schema.`$ref` == null) {
                // TODO: Need to support non ref types"
                return@mapNotNull null
            }
            val clsName = definition.schema.`$ref`.split("/").last()
            val schema = spec.components.schemas[clsName]!!
            val optionName = when (mediaType) {
                "application/json" -> "Json"
                "application/xml" -> "Xml"
                "application/x-www-form-urlencoded" -> "Form"
                else -> error("Not implemented")
            }
            optionName to parseTypeDescriptor(ref, clsName, schema)
        }.toMap()
        val type = TypeDescriptor.OneOf(operation.operationId.capitalized() + "Request", definitions)

        return RequestBody(definitions, type, required)
    }

    private fun parseResponses(operation: Operation, responses: ApiResponses): ResponseBody {
        val codeToDescriptor: Map<String, TypeDescriptor?> = responses.map { (code, response) ->
            val responseCls = response.content?.get("application/json")
            val descriptor = if (responseCls != null) {
                val ref = responseCls.schema.`$ref`
                val clsName = ref.split("/").last()
                val schema = spec.components.schemas[clsName]!!
                parseTypeDescriptor(ref, clsName, schema)
            } else null

            code to descriptor
        }.toMap()

        val codeToSealedOption = mutableMapOf<String, ResponseBodySealedOption>()
        val clsNameToTypeDescriptor = mutableMapOf<String, TypeDescriptor?>()
        for ((code: String, typeDescriptor: TypeDescriptor?) in codeToDescriptor) {
            val itemOption = when (typeDescriptor) {
                is TypeDescriptor.Object -> ResponseBodySealedOption.Parametrized(typeDescriptor.clsName)
                is TypeDescriptor.Array -> ResponseBodySealedOption.Parametrized(typeDescriptor.clsName)
                null -> when (code) {
                    "200" -> ResponseBodySealedOption.JustStatus("Ok")
                    "201" -> ResponseBodySealedOption.JustStatus("Created")
                    "404" -> ResponseBodySealedOption.JustStatus("NotFound")
                    else -> error("Cannot infer name from code = $code")
                }
                else -> error("Cannot infer name")
            }
            codeToSealedOption[code] = itemOption
            clsNameToTypeDescriptor[itemOption.clsName] = typeDescriptor
        }
        return if (codeToDescriptor.size > 1) {
            val clsName = operation.operationId.capitalized() + "Response"
            ResponseBody(codeToSealedOption, clsName, TypeDescriptor.OneOf(clsName, clsNameToTypeDescriptor), false)
        } else {
            val clsOption = codeToSealedOption[codeToSealedOption.keys.first()]!!
            ResponseBody(codeToSealedOption, clsOption.clsName, clsNameToTypeDescriptor[clsOption.clsName]!!, true)
        }
    }

    private fun parseTypeDescriptor(
        ref: String,
        clsName: String,
        schema: Schema<Any>
    ): TypeDescriptor {
        val result = when (schema.type) {
            "array" -> {
                val itemRef = schema.items.`$ref`
                val itemClsName = itemRef.split("/").last()
                val itemSchema = spec.components.schemas[itemClsName]!!
                refsBuilder.addRef(itemRef, parseTypeDescriptor(itemRef, itemClsName, itemSchema))
                TypeDescriptor.Array(clsName, TypeDescriptor.RefType(itemRef!!))
            }

            "object" -> {
                val requiredProperties = schema.required.toSet()
                val properties = schema.properties.map { (name, property) ->
                    getTypePropertyDescriptor(name, property, requiredProperties.contains(name))
                }
                TypeDescriptor.Object(clsName, properties)
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
        type = getPropertyType(property),
        format = property.format,
        required = required ?: false
    )

    private fun getPropertyType(property: Schema<Any>): TypeDescriptor {
        if (property.`$ref` != null) {
            return TypeDescriptor.RefType(property.`$ref`)
        }
        return when (val type = property.type) {
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
    val name: String,
    val operations: List<OperationDescriptor>,
    val refs: Map<String, TypeDescriptor>,
)