package com.github.raymank26

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ClassName.Companion.bestGuess
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.nio.file.Path
import java.util.*

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
    }

    private fun generateTypeDescriptor(value: TypeDescriptor, required: Boolean): TypeName {
        val basicType = when (value) {
            is TypeDescriptor.Array -> {
                val innerTypeName = generateTypeDescriptor(value.itemDescriptor, true)
                val name = value.clsName

                alreadyGenerated[name]?.let {
                    return it
                }
                val clsBuilder = TypeSpec.classBuilder(name)
                clsBuilder.addProperty(
                    name.replaceFirstChar { it.lowercase(Locale.getDefault()) },
                    bestGuess("java.util.List").parameterizedBy(innerTypeName)
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
                value.properties.forEach { property ->
                    clsBuilder.addProperty(
                        PropertySpec.builder(
                            property.name,
                            generateTypeDescriptor(property.type, property.required)
                        )
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

            is TypeDescriptor.OneOf -> TODO()
            TypeDescriptor.Int64Type -> Long::class.java.asTypeName()
            TypeDescriptor.IntType -> Int::class.java.asTypeName()
            TypeDescriptor.StringType -> String::class.java.asTypeName()
        }
        return basicType.copy(nullable = !required)
    }
}