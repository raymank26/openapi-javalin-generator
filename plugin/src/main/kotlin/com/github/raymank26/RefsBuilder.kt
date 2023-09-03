package com.github.raymank26

class RefsBuilder {

    private val schemaRefs = mutableMapOf<String, TypeDescriptor>()

    fun addRef(name: String, typeDescriptor: TypeDescriptor) {
        schemaRefs[name] = typeDescriptor
    }

    fun build(): Map<String, TypeDescriptor> {
        return schemaRefs
    }
}