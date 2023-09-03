package com.github.raymank26

data class OperationDescriptor(
    val path: String,
    val method: String,
    val summary: String,
    val operationId: String?,
    val paramDescriptors: List<ParamDescriptor>,
    val requestBody: RequestBody?,
    val responseBody: List<TypeDescriptor>,
)

data class ParamDescriptor(
    val name: String,
    val place: String,
    val typePropertyDescriptor: TypePropertyDescriptor
)

interface RequestBody {

}

sealed interface TypeDescriptor {

    data class Array(val itemDescriptor: TypeDescriptor) : TypeDescriptor

    data class Object(val properties: List<TypePropertyDescriptor>) : TypeDescriptor

    data class OneOf(val typeDescriptors: List<TypeDescriptor>) : TypeDescriptor

    data object StringType : TypeDescriptor

    data object Int64Type : TypeDescriptor

    data object IntType : TypeDescriptor
}

data class TypePropertyDescriptor(
    val name: String,
    val type: TypeDescriptor,
    val format: String?,
    val required: Boolean
)