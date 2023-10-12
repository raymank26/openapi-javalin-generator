package com.github.raymank26

import com.squareup.kotlinpoet.*
import java.nio.file.Path

class JavalinControllerGenerator(
    private val specMetadata: SpecMetadata,
    private val basePackageName: String,
    private val baseGenerationPath: Path
) {

    fun generate() {
        val serverInterfaceType = ClassName(basePackageName, "${specMetadata.namePrefix}Spec")
        val typeBuilder = TypeSpec.classBuilder("${specMetadata.namePrefix}JavalinController")
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(
                        ParameterSpec.builder("server", serverInterfaceType)
                            .build()
                    )
                    .build()
            )

        typeBuilder.addProperty(
            PropertySpec.builder("server", serverInterfaceType)
                .addModifiers(KModifier.PRIVATE)
                .initializer("server")
                .build()
        )

        typeBuilder.addFunction(
            FunSpec
                .builder("bind")
                .addParameter("javalin", ClassName("io.javalin", "Javalin"))
                .addCode(buildCodeBlock {

                    specMetadata.operations.forEach { operationDescriptor ->
                        val requestBody: RequestBody? = operationDescriptor.requestBody
                        val parameters = buildCodeBlock {
                            add("(")
                            val paramDescriptors = operationDescriptor.paramDescriptors
                            for ((index, paramDescriptor) in paramDescriptors.withIndex()) {
                                when (paramDescriptor.place) {
                                    "query" -> add("ctx.queryParam(%S)", paramDescriptor.name)
                                    "path" -> add("ctx.pathParam(%S)", paramDescriptor.name)
                                    "header" -> add("ctx.header(%S)", paramDescriptor.name)
                                }
                                when (paramDescriptor.typePropertyDescriptor.type) {
                                    TypeDescriptor.Int64Type -> add("?.toLong()")
                                    TypeDescriptor.IntType -> add("?.toInt()")
                                    TypeDescriptor.StringType -> Unit
                                    else -> error("Cannot get param of complex type")
                                }
                                if (paramDescriptor.typePropertyDescriptor.required && paramDescriptor.place != "path") {
                                    add("!!")
                                }
                                if (index != paramDescriptors.size - 1) {
                                    add(", ")
                                }
                            }
                            if (requestBody != null) {
                                if (paramDescriptors.isNotEmpty()) {
                                    add(", ")
                                }
                                add("body")
                            }
                            addStatement(")")

                        }
                        addStatement(
                            """javalin.%L(%S) { ctx -> """,
                            operationDescriptor.method,
                            operationDescriptor.path
                        )
                            .withIndent {
                                addResponseProcessingCode(requestBody, operationDescriptor, parameters)
                            }
                            .addStatement("}")
                    }
                })
                .build()
        )

        FileSpec.builder(basePackageName, "JavalinController")
            .addType(typeBuilder.build())
            .build()
            .writeTo(baseGenerationPath)
    }

    private fun CodeBlock.Builder.addResponseProcessingCode(
        requestBody: RequestBody?,
        operationDescriptor: OperationDescriptor,
        parameters: CodeBlock
    ) {
        val requestBodyBlock = if (requestBody != null) {
            buildCodeBlock {
                addStatement("val body = when (ctx.contentType()?.split(\";\")?.first()) {")
                withIndent {
                    requestBody.contentTypeToType.forEach { (key, value): Map.Entry<RequestBodyMediaType, TypeDescriptor> ->
                        val parser = when (key) {
                            RequestBodyMediaType.FormData -> buildCodeBlock {
                                val targetObj = (value as TypeDescriptor.Object)
                                add(
                                    "%T(%T(",
                                    ClassName(
                                        basePackageName,
                                        requestBody.clsName,
                                        key.clsName
                                    ),
                                    ClassName(basePackageName, targetObj.clsName!!)
                                )
                                withIndent(3) {
                                    targetObj.properties.forEach { property ->
                                        add("ctx.formParam(%S)", property.name)
                                        add(if (property.required) "!!" else "?")
                                        when (property.type) {
                                            TypeDescriptor.Int64Type -> addStatement(".toLong(),")
                                            TypeDescriptor.IntType -> addStatement(".toInt(),")
                                            TypeDescriptor.StringType -> addStatement(".toString(),")
                                            else -> error("Not supported type = ${property.type}")
                                        }
                                    }
                                }
                                addStatement("))")
                            }

                            RequestBodyMediaType.Json -> buildCodeBlock {
                                addStatement(
                                    "%T(ctx.bodyAsClass(%L::class.java))",
                                    ClassName(
                                        basePackageName,
                                        requestBody.clsName,
                                        key.clsName
                                    ),
                                    ClassName(
                                        basePackageName,
                                        (value as TypeDescriptor.Object).clsName!!
                                    )
                                )
                            }

                            RequestBodyMediaType.Xml -> buildCodeBlock {
                                addStatement("TODO(\"Not implemented\")")
                            }
                        }
                        add("%S -> ", key.mediaType)
                        add(parser)
                    }
                    addStatement("else -> error(\"No request found\")")
                }
                addStatement("}")
            }
        } else {
            CodeBlock.builder().build()
        }
        add(requestBodyBlock)
        add("val response = server.%L", operationDescriptor.operationId)
        add(parameters)

        add(buildCodeBlock {
            addStatement("when (response) {")
                .withIndent {
                    operationDescriptor.responseBody.statusCodeToClsName.forEach { (code, option) ->
                        val sealedClsName = ClassName(
                            basePackageName,
                            operationDescriptor.responseBody.clsName
                        )
                        addStatement("is %T.%L -> {", sealedClsName, option.clsName)
                            .withIndent {
                                val statusCode = if (code == "default")
                                    "response.${option.clsName.decapitalized()}.code"
                                else code
                                addHeaders(option)
                                addStatement("ctx.status(%L)", statusCode)
                                if (option is ResponseBodySealedOption.Parametrized) {
                                    addStatement(
                                        "ctx.json(response.%L)",
                                        option.clsName.decapitalized()
                                    )
                                }
                            }
                            .addStatement("}")
                    }
                }
                .addStatement("}")
        })
    }
}

private fun CodeBlock.Builder.addHeaders(option: ResponseBodySealedOption) {
    if (option.headers == null) {
        return
    }
    option.headers.properties.forEach { header ->
        val headerName = if (header.name.contains("-")) {
            "`${header.name}`"
        } else {
            header.name
        }
        val propertyRef = "response.${option.headers.clsName!!.decapitalized()}.${headerName}"
        if (header.required) {
            addStatement("ctx.header(%S, %L)", header.name, propertyRef)
        } else {
            addStatement("if (%L != null) {", propertyRef)
            withIndent {
                addStatement("ctx.header(%S, %L)", header.name, propertyRef)
            }
            addStatement("}")
        }
    }
}

inline fun CodeBlock.Builder.withIndent(
    steps: Int = 1,
    builderAction: CodeBlock.Builder.() -> Unit
): CodeBlock.Builder {
    for (i in 0..steps) {
        indent()
    }
    builderAction(this)
    for (i in 0..steps) {
        unindent()
    }
    return this
}
