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
        val namePrefix = spec.info.extensions["x-name"]?.toString() ?: ""
        return SpecMetadata(namePrefix, operations, refsBuilder.build())
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
                "application/json" -> RequestBodyMediaType.Json
                "application/xml" -> RequestBodyMediaType.Xml
                "application/x-www-form-urlencoded" -> RequestBodyMediaType.FormData
                else -> error("Not implemented")
            }
            optionName to parseTypeDescriptor(ref, clsName, schema)
        }.toMap()
        val clsName = operation.operationId.capitalized() + "Request"
        val type = TypeDescriptor.OneOf(
            clsName, definitions
                .map { entry -> entry.key.clsName to listOf(entry.value) }.toMap()
        )

        return RequestBody(clsName, definitions, type, required)
    }

    private fun parseResponses(operation: Operation, responses: ApiResponses): ResponseBody {

        val clsName = operation.operationId.capitalized() + "Response"
        val codeToSealedOption = mutableMapOf<String, ResponseBodySealedOption>()
        val clsNameToTypeDescriptor = mutableMapOf<String, List<TypeDescriptor>>()

        responses.forEach { (code, response) ->
            val responseCls = response.content?.get("application/json")
            val headers = response.headers?.map {
                ResponseHeader(getTypePropertyDescriptor(it.key.decapitalized(), it.value.schema, it.value.required))
            } ?: emptyList()

            val descriptor = if (responseCls != null) {
                val ref = responseCls.schema.`$ref`
                val optionClsName = ref.split("/").last()
                val schema = spec.components.schemas[optionClsName]!!
                parseTypeDescriptor(ref, optionClsName, schema)
            } else null
            val headersDescriptorProvider = { optionClsName: String ->
                if (headers.isNotEmpty()) {
                    TypeDescriptor.Object(
                        clsName + optionClsName + "Headers",
                        headers.map { it.typePropertyDescriptor })
                } else null
            }

            val option = createResponseOption(code, descriptor, headersDescriptorProvider)
            codeToSealedOption[code] = option
            clsNameToTypeDescriptor[option.clsName] = listOfNotNull(descriptor, option.headers)
        }
        return ResponseBody(codeToSealedOption, clsName, TypeDescriptor.OneOf(clsName, clsNameToTypeDescriptor), false)
    }

    private fun createResponseOption(
        code: String,
        descriptor: TypeDescriptor?,
        headersProvider: (String) -> TypeDescriptor.Object?
    ): ResponseBodySealedOption {
        return when (descriptor) {
            is TypeDescriptor.Object -> ResponseBodySealedOption.Parametrized(
                descriptor.clsName,
                headersProvider(descriptor.clsName)
            )

            is TypeDescriptor.Array -> ResponseBodySealedOption.Parametrized(
                descriptor.clsName,
                headersProvider(descriptor.clsName)
            )

            else -> {
                val clsName = when (code) {
                    "200" -> "Ok"
                    "201" -> "Created"
                    "404" -> "NotFound"
                    "302" -> "Redirect"
                    else -> error("Cannot infer name from code = $code")
                }
                ResponseBodySealedOption.JustStatus(clsName, headersProvider(clsName))
            }
        }
    }

    private fun parseTypeDescriptor(
        ref: String,
        clsName: String,
        schema: Schema<Any>?
    ): TypeDescriptor {
        val result = when (schema?.type) {
            "array" -> {
                val itemRef = schema.items.`$ref`
                val itemClsName = itemRef.split("/").last()
                val itemSchema = spec.components.schemas[itemClsName]!!
                refsBuilder.addRef(itemRef, parseTypeDescriptor(itemRef, itemClsName, itemSchema))
                TypeDescriptor.Array(clsName, TypeDescriptor.RefType(itemRef!!))
            }

            "object" -> {
                val requiredProperties = schema.required?.toSet() ?: emptySet()
                val properties = schema.properties.map { (name, property) ->
                    getTypePropertyDescriptor(name, property, requiredProperties.contains(name))
                }
                TypeDescriptor.Object(clsName, properties)
            }

            null -> TypeDescriptor.Object(clsName, emptyList())
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
        type = getPropertyType(name, property),
        format = property.format,
        required = required ?: false
    )

    private fun getPropertyType(name: String, property: Schema<Any>): TypeDescriptor {
        if (property.`$ref` != null) {
            return TypeDescriptor.RefType(property.`$ref`)
        }
        return when (val type = property.type) {
            "integer" -> TypeDescriptor.IntType
            "string" -> TypeDescriptor.StringType
            "object" -> {
                val clsName = "${name.capitalized()}Properties"
                parseTypeDescriptor(clsName, clsName, property.contentSchema)
            }
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
    val namePrefix: String,
    val operations: List<OperationDescriptor>,
    val refs: Map<String, TypeDescriptor>,
)