package com.github.raymank26

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ClassName.Companion.bestGuess
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.nio.file.Path

class TypesGenerator(
    private val specMetadata: SpecMetadata,
    private val basePackageName: String,
    private val baseGenPath: Path,
) {

    private val alreadyGenerated: MutableMap<String, TypeName> = mutableMapOf()

    fun generateTypes() {
        specMetadata.refs.forEach { (_, value: TypeDescriptor) ->
            generateTypeDescriptor(value, true)
        }
        specMetadata.operations.forEach { operationDescriptor ->
            generateTypeDescriptor(operationDescriptor.responseBody.type, true)
            if (operationDescriptor.requestBody != null) {
                generateTypeDescriptor(operationDescriptor.requestBody.type, true)
            }
        }
    }

    private fun generateTypeDescriptor(
        value: TypeDescriptor,
        required: Boolean,
    ): TypeName {
        val basicType = when (value) {
            is TypeDescriptor.Array -> {
                val innerTypeName = generateTypeDescriptor(value.itemDescriptor, true)
                val name = value.clsName

                alreadyGenerated[name]?.let {
                    return it
                }
                val clsBuilder = TypeSpec.classBuilder(name)
                    .addModifiers(KModifier.DATA)
                val listType = bestGuess("kotlin.collections.List").parameterizedBy(innerTypeName)
                clsBuilder.primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter(ParameterSpec(name.decapitalized(), listType))
                        .build()
                )
                clsBuilder.addProperty(
                    PropertySpec
                        .builder(name.decapitalized(), listType)
                        .initializer(name.decapitalized())
                        .build()
                )
                val typeSpec = clsBuilder.build()
                FileSpec.builder(basePackageName, value.clsName)
                    .addType(typeSpec)
                    .build()
                    .writeTo(baseGenPath)
                val typeName = bestGuess("$basePackageName.$name")
                alreadyGenerated[value.clsName] = typeName
                typeName
            }

            is TypeDescriptor.RefType -> bestGuess(basePackageName + "." + value.name.split("/").last())
            is TypeDescriptor.Object -> {
                val name = value.clsName
                alreadyGenerated[name]?.let {
                    return it
                }
                val clsBuilder = TypeSpec.classBuilder(name)
                    .addModifiers(KModifier.DATA)
                clsBuilder.primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameters(value.properties
                            .map { ParameterSpec(it.name, generateTypeDescriptor(it.type, it.required)) })
                        .build()
                )
                value.properties.forEach { property ->
                    clsBuilder.addProperty(
                        PropertySpec
                            .builder(property.name, generateTypeDescriptor(property.type, property.required))
                            .initializer(property.name)
                            .build()
                    )
                }
                val typeSpec = clsBuilder.build()
                FileSpec.builder(basePackageName, value.clsName)
                    .addType(typeSpec)
                    .build()
                    .writeTo(baseGenPath)
                val typeName = bestGuess("$basePackageName.$name")
                alreadyGenerated[value.clsName] = typeName
                typeName
            }

            is TypeDescriptor.OneOf -> {
                val name = value.clsName
                val responseTypeBuilder = TypeSpec.interfaceBuilder(name)
                    .addModifiers(KModifier.SEALED)
                val typeName = bestGuess("$basePackageName.$name")

                value.typeDescriptors.forEach { (name, description: TypeDescriptor?) ->
                    val subType = description?.let { generateTypeDescriptor(it, true) }
                    if (subType != null) {
                        val simpleName = (subType as ClassName).simpleName

                        val subTypeSpec = TypeSpec.classBuilder(name)
                            .addModifiers(KModifier.DATA)
                            .addSuperinterface(typeName)
                            .primaryConstructor(
                                FunSpec.constructorBuilder()
                                    .addParameter(
                                        ParameterSpec
                                            .builder(simpleName.decapitalized(), subType)
                                            .build()
                                    )
                                    .build()
                            )
                            .addProperty(
                                PropertySpec.builder(simpleName.decapitalized(), subType)
                                    .initializer(simpleName.decapitalized())
                                    .build()
                            )

                            .build()

                        responseTypeBuilder.addType(subTypeSpec)
                    } else {
                        responseTypeBuilder.addType(
                            TypeSpec.objectBuilder(name)
                                .addModifiers(KModifier.DATA)
                                .addSuperinterface(typeName)
                                .build()
                        )
                    }
                }
                FileSpec.builder(basePackageName, value.clsName)
                    .addType(responseTypeBuilder.build())
                    .build()
                    .writeTo(baseGenPath)
                typeName
            }
            TypeDescriptor.Int64Type -> Long::class.java.asTypeName()
            TypeDescriptor.IntType -> Int::class.java.asTypeName()
            TypeDescriptor.StringType -> ClassName("kotlin", "String")
        }
        return basicType.copy(nullable = !required)
    }
}