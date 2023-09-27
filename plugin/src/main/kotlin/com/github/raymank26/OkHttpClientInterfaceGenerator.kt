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
        val typeSpec = TypeSpec.classBuilder("Client")
        val okHttpClientType = bestGuess("okhttp3.OkHttpClient")
        val objectMapperType = bestGuess("com.fasterxml.jackson.databind.ObjectMapper")
        typeSpec.primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter(ParameterSpec("host", ClassName("kotlin", "String")))
                .build()
        )
        typeSpec.addProperty(
            PropertySpec.builder("host", ClassName("kotlin", "String"), KModifier.PRIVATE)
                .initializer("host")
                .build()
        )
        typeSpec.addProperty(
            PropertySpec.builder("httpClient", okHttpClientType, KModifier.PRIVATE)
                .initializer(CodeBlock.of("%T()", okHttpClientType))
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
        operation.paramDescriptors.forEach { paramDescriptor ->
            funSpec.addParameter(paramDescriptor.name, resolveType(paramDescriptor.typePropertyDescriptor))
        }
        funSpec.returns(bestGuess("$basePackageName.${operation.responseBody.clsName}"))
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
            addStatement("return httpClient.newCall(%T.Builder()", bestGuess("okhttp3.Request"))
                .indent()
                .addStatement(".url(url)")
                .addStatement(".build())")
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
                    when (itemDescriptor) {
                        is ResponseBodySealedOption.JustStatus -> {
                            addStatement("%L -> %T", statusCode, cls)
                        }

                        is ResponseBodySealedOption.Parametrized -> {
                            addStatement(
                                "%L -> %T(objectMapper.readValue(it.body?.byteStream(), %T::class.java))",
                                statusCode, cls, optionCls
                            )
                        }
                    }
                }
                unindent()
                addStatement("}")
            } else {
                TODO()
            }
        }
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