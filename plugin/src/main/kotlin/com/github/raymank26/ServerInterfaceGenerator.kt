package com.github.raymank26

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.TypeSpec.Companion.interfaceBuilder
import java.nio.file.Path

class ServerInterfaceGenerator(
    private val specMetadata: SpecMetadata,
    private val basePackageName: String,
    private val baseGenerationPath: Path
) {

    fun generate() {
        val typeSpecBuilder = interfaceBuilder("Server")

        specMetadata.operations.forEach { operationDescriptor ->
            val funBuilder = FunSpec.builder(operationDescriptor.operationId)
            funBuilder.addModifiers(KModifier.ABSTRACT)
            for (paramDescriptor in operationDescriptor.paramDescriptors) {
                funBuilder.addParameter(
                    ParameterSpec(
                        paramDescriptor.name,
                        getParamTypeName(
                            paramDescriptor.typePropertyDescriptor.name,
                            paramDescriptor.typePropertyDescriptor.type
                        )
                    )
                )
            }
            funBuilder.returns(
                getParamTypeName(
                    operationDescriptor.responseBody.clsName,
                    operationDescriptor.responseBody.type
                )
            )
            typeSpecBuilder.addFunction(
                funBuilder
                    .build()
            )
        }
        FileSpec.builder(basePackageName, "Server")
            .addType(typeSpecBuilder.build())
            .build()
            .writeTo(baseGenerationPath)
    }

    private fun getParamTypeName(name: String, descriptor: TypeDescriptor): TypeName {
        return when (descriptor) {
            is TypeDescriptor.Array -> ClassName(basePackageName, name)
            is TypeDescriptor.Object -> ClassName(basePackageName, name)
            is TypeDescriptor.OneOf -> ClassName(basePackageName, name)
            is TypeDescriptor.RefType -> ClassName(basePackageName, descriptor.name)
            TypeDescriptor.Int64Type -> Long::class.java.asTypeName()
            TypeDescriptor.IntType -> Int::class.java.asTypeName()
            TypeDescriptor.StringType -> ClassName("kotlin", "String")
        }
    }
}