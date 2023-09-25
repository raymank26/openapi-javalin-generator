package com.github.raymank26

import com.squareup.kotlinpoet.*
import java.nio.file.Path

class JavalinControllerGenerator(
    private val specMetadata: SpecMetadata,
    private val basePackageName: String,
    private val baseGenerationPath: Path
) {

    fun generate() {
        val serverInterfaceType = ClassName(basePackageName, "Server")
        val typeBuilder = TypeSpec.classBuilder("JavalinController")
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(ParameterSpec("server", serverInterfaceType))
                    .build()
            )

        typeBuilder.addProperty(
            PropertySpec.builder("server", serverInterfaceType)
                .initializer("server")
                .build()
        )

        typeBuilder.addFunction(
            FunSpec
                .builder("bind")
                .addParameter("javalin", ClassName("io.javalin", "Javalin"))
                .addCode(buildCodeBlock {

                    specMetadata.operations.forEach { operationDescriptor ->
                        val parameters = buildCodeBlock {
                            add("(")
                            val paramDescriptors = operationDescriptor.paramDescriptors
                            for ((index, paramDescriptor) in paramDescriptors.withIndex()) {
                                if (paramDescriptor.place == "query") {
                                    add("ctx.queryParam(%S)", paramDescriptor.name)
                                } else {
                                    add("ctx.pathParam(%S)", paramDescriptor.name)
                                }
                                when (paramDescriptor.typePropertyDescriptor.type) {
                                    TypeDescriptor.Int64Type -> add("!!.toLong()")
                                    TypeDescriptor.IntType -> add("!!.toInt()")
                                    TypeDescriptor.StringType -> Unit
                                    else -> error("Cannot get param of complex type")
                                }
                                if (index != paramDescriptors.size - 1) {
                                    add(", ")
                                }
                            }
                            addStatement(")")

                        }
                        addStatement(
                            """javalin.%L(%S) { ctx -> """,
                            operationDescriptor.method,
                            operationDescriptor.path
                        )
                            .withIndent {
                                add("val response = server.%L", operationDescriptor.operationId)
                                add(parameters)
                                add(buildCodeBlock {
                                    addStatement("when (response) {")
                                        .withIndent {
                                            operationDescriptor.responseBody.statusCodeToClsName.forEach { (code, clsName) ->
                                                val sealedClsName = ClassName(
                                                    basePackageName,
                                                    operationDescriptor.responseBody.clsName
                                                )
                                                addStatement("is %T.%L -> {", sealedClsName, clsName)
                                                    .withIndent {
                                                        val statusCode = if (code == "default")
                                                            "response.${clsName.decapitalized()}.code"
                                                        else code

                                                        addStatement("ctx.status(%L)", statusCode)
                                                        addStatement("ctx.json(response.%L)", clsName.decapitalized())
                                                    }
                                                    .addStatement("}")
                                            }
                                        }
                                        .addStatement("}")
                                })
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
}