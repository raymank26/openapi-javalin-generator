package com.github.raymank26

class TypesGenerator(private val specMetadata: SpecMetadata, basePackageName: String) {

    fun generateTypes() {
        specMetadata.refs.forEach { (_, value) ->
            println(value)
        }
    }
}