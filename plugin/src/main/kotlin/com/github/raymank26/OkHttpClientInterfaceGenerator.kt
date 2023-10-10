package com.github.raymank26

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ClassName.Companion.bestGuess
import java.nio.file.Path

class OkHttpClientInterfaceGenerator(
    private val specMetadata: SpecMetadata,
    private val basePackageName: String,
    private val baseGenerationPath: Path
) {
    fun generateClient() {
        val typeSpec = TypeSpec.classBuilder("${specMetadata.namePrefix}Client")
            .addSuperinterface(ClassName(basePackageName, "${specMetadata.namePrefix}Spec"))
        val okHttpClientType = bestGuess("okhttp3.OkHttpClient")
        val objectMapperType = bestGuess("com.fasterxml.jackson.databind.ObjectMapper")
        val okHttpClientBuilder = ClassName("okhttp3", "OkHttpClient", "Builder")
        typeSpec.primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(ParameterSpec("host", ClassName("kotlin", "String")))
                .addParameter(
                    ParameterSpec.builder(
                        "clientConfig", LambdaTypeName.get(
                            receiver = okHttpClientBuilder, returnType = okHttpClientBuilder
                        )
                    )
                        .defaultValue(CodeBlock.of("{ this }"))
                        .build()
                )
                .build()
        )
        typeSpec.addProperty(
            PropertySpec.builder("host", ClassName("kotlin", "String"), KModifier.PRIVATE)
                .initializer("host")
                .build()
        )
        typeSpec.addProperty(
            PropertySpec.builder("httpClient", okHttpClientType, KModifier.PRIVATE)
                .initializer(CodeBlock.of("clientConfig(%T()).build()", okHttpClientBuilder))
                .build()
        )

        typeSpec.addProperty(
            PropertySpec.builder("objectMapper", objectMapperType, KModifier.PRIVATE)
                .initializer(
                    CodeBlock.of(
                        "%T().%M()", objectMapperType,
                        MemberName("com.fasterxml.jackson.module.kotlin", "registerKotlinModule")
                    )
                )
                .build()
        )

        specMetadata.operations.forEach { operation ->
            typeSpec.addFunction(createFunction(operation))
        }
        FileSpec.builder(ClassName(basePackageName, "Client"))
            .addType(typeSpec.build())
            .build()
            .writeTo(baseGenerationPath)
    }

    private fun createFunction(operation: OperationDescriptor): FunSpec {
        val funSpec = FunSpec.builder(operation.operationId)
            .addModifiers(KModifier.OVERRIDE)
        operation.paramDescriptors.forEach { paramDescriptor ->
            funSpec.addParameter(paramDescriptor.name, resolveType(paramDescriptor.typePropertyDescriptor))
        }
        operation.requestBody?.let { requestBody ->
            funSpec.addParameter("requestBody", ClassName(basePackageName, requestBody.clsName))
        }
        funSpec.returns(ClassName(basePackageName, operation.responseBody.clsName))
        val path = operation.path.split("/").joinToString("/") {
            if (it.startsWith("{")) {
                val paramName = it.substring(1 until it.length - 1)
                "\$$paramName"
            } else {
                it
            }
        }

        // url
        funSpec.addCode(buildCodeBlock {
            addStatement(
                """val url = "%L%L".%M().newBuilder()""", "\$host", path,
                MemberName("okhttp3.HttpUrl.Companion", "toHttpUrl")
            )
            indent()
            operation.paramDescriptors.forEach { paramDescriptor ->
                if (paramDescriptor.place == "query") {
                    addStatement(
                        """.addQueryParameter(%S, %L.toString())""",
                        paramDescriptor.name,
                        paramDescriptor.name + if (paramDescriptor.typePropertyDescriptor.required) "" else "?"
                    )
                }
            }
            addStatement(".build()")
                .unindent()
        })

        funSpec.addCode(buildCodeBlock {
            addStatement("val requestBuilder = %T.Builder()", bestGuess("okhttp3.Request"))
                .withIndent {
                    addStatement(".url(url)")
                }
        })

        val toRequestBody = MemberName("okhttp3.RequestBody.Companion", "toRequestBody")
        if (operation.requestBody != null) {
            funSpec.addCode(buildCodeBlock {
                addStatement("when (val body = requestBody) {")
                withIndent {
                    operation.requestBody.contentTypeToType.forEach { (mediaType, type) ->
                        val cls = ClassName(basePackageName, operation.requestBody.clsName + "." + mediaType.clsName)
                        val toMediaType = MemberName("okhttp3.MediaType.Companion", "toMediaType")
                        val propertyName = (type as TypeDescriptor.Object).clsName!!.decapitalized()

                        add("is %T -> requestBuilder.%L(", cls, operation.method)
                        when (mediaType) {
                            RequestBodyMediaType.FormData -> {
                                addStatement("%T.Builder()", ClassName("okhttp3", "FormBody"))
                                withIndent {
                                    type.properties.forEach { property ->
                                        addStatement(
                                            ".add(%S, %L.toString())",
                                            property.name,
                                            "body.$propertyName.${property.name}"
                                        )
                                    }
                                }
                                addStatement(".build()")
                            }

                            RequestBodyMediaType.Json -> {
                                add(
                                    "objectMapper.writeValueAsString(%L).%M(\"application/json\".%M())",
                                    "body.$propertyName",
                                    toRequestBody,
                                    toMediaType
                                )
                            }

                            RequestBodyMediaType.Xml -> add("TODO(\"Not implemented\")")
                        }
                        addStatement(")")
                    }
                }
                addStatement("}")
            })
        } else if (operation.method == "post") {
            funSpec.addStatement("requestBuilder.post(\"\".%M())", toRequestBody)
        }

        funSpec.addCode(buildCodeBlock {
            addStatement("return httpClient.newCall(requestBuilder.build())")
                .indent()
                .addStatement(".execute()")
                .addStatement(".use {")
                .indent()
                .add(generateResponseBody(operation))
                .unindent()
                .addStatement("}")
                .unindent()
        })
        return funSpec.build()
    }

    private fun generateResponseBody(operation: OperationDescriptor): CodeBlock {
        return buildCodeBlock {
            if (!operation.responseBody.isSingle) {
                addStatement("when (it.code) {")
                indent()
                operation.responseBody.statusCodeToClsName.forEach { (status, itemDescriptor) ->
                    val statusCode = if (status == "default") "else" else status
                    val cls = ClassName(
                        basePackageName, operation.responseBody.clsName + "." +
                                itemDescriptor.clsName
                    )
                    val optionCls = ClassName(basePackageName, itemDescriptor.clsName)
                    addStatement("%L -> {", statusCode)
                    withIndent {
                        addResponseConstructor(itemDescriptor, cls, optionCls)
                    }
                    addStatement("}")
                }
                unindent()
                addStatement("}")
            } else {
                TODO()
            }
        }
    }

    private fun CodeBlock.Builder.addResponseConstructor(
        itemDescriptor: ResponseBodySealedOption,
        cls: ClassName,
        optionCls: ClassName
    ) {
        when (itemDescriptor) {
            is ResponseBodySealedOption.JustStatus -> {
                add("%T", cls)
                if (itemDescriptor.headers != null) {
                    add("(")
                    addResponseHeaders(itemDescriptor)
                    addStatement(")")
                }
                addStatement("")
            }

            is ResponseBodySealedOption.Parametrized -> {
                addStatement("if ((it.header(\"Content-Type\")?.indexOf(\"application/json\") ?: -1) < 0) {")
                withIndent {
                    addStatement("error(\"Unexpected content, status = \${it.code}, body = \${it.body?.string()}\")")
                }
                addStatement("}")
                addStatement("%T(objectMapper.readValue(it.body?.byteStream(), %T::class.java)", cls, optionCls)
                if (itemDescriptor.headers != null) {
                    add(", ")
                    addResponseHeaders(itemDescriptor)
                }
                addStatement(")")
            }
        }
    }

    private fun CodeBlock.Builder.addResponseHeaders(itemDescriptor: ResponseBodySealedOption) {
        val headers = itemDescriptor.headers!!

        addStatement("%T(", ClassName(basePackageName, headers.clsName!!))
        withIndent {
            headers.properties.forEach {
                addStatement("it.header(%S)%L,", it.name.lowercase(), if (it.required) "!!" else "")
            }
        }
        add(")")
    }

    private fun resolveType(paramDescriptor: TypePropertyDescriptor): TypeName {
        val baseType = when (paramDescriptor.type) {
            TypeDescriptor.Int64Type -> Long::class.java.asTypeName()
            TypeDescriptor.IntType -> Int::class.java.asTypeName()
            TypeDescriptor.StringType -> ClassName("kotlin", "String")
            else -> error("Unsupported type")
        }
        return baseType.copy(nullable = !paramDescriptor.required)
    }
}