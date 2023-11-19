package com.github.raymank26

data class OperationDescriptor(
    val path: String,
    val method: String,
    val summary: String,
    val operationId: String,
    val paramDescriptors: List<ParamDescriptor>,
    val requestBody: RequestBody?,
    val responseBody: ResponseBody,
)

data class ResponseBody(
    val statusCodeToClsName: Map<String, ResponseBodySealedOption>,
    val clsName: String,
    val type: TypeDescriptor,
    val isSingle: Boolean,
)

sealed class ResponseBodySealedOption(val clsName: String, val headers: TypeDescriptor.Object?) {
    class JustStatus(clsName: String, headers: TypeDescriptor.Object?) : ResponseBodySealedOption(clsName, headers)
    class Parametrized(clsName: String, headers: TypeDescriptor.Object?) : ResponseBodySealedOption(clsName, headers)
}

data class ResponseHeader(
    val typePropertyDescriptor: TypePropertyDescriptor
)

data class ParamDescriptor(
    val name: String,
    val place: String,
    val typePropertyDescriptor: TypePropertyDescriptor
)

data class RequestBody(
    val clsName: String,
    val contentTypeToType: Map<RequestBodyMediaType, TypeDescriptor>,
    val type: TypeDescriptor,
    val required: Boolean
)

sealed class RequestBodyMediaType(val clsName: String, val mediaType: String) {
    data object Json : RequestBodyMediaType("Json", "application/json")
    data object Xml : RequestBodyMediaType("Xml", "application/xml")
    data object FormData : RequestBodyMediaType("Form", "application/x-www-form-urlencoded")
}


sealed interface TypeDescriptor {

    data class Array(val clsName: String?, val itemDescriptor: TypeDescriptor) : TypeDescriptor

    data class Object(val clsName: String?, val properties: List<TypePropertyDescriptor>) : TypeDescriptor

    data class OneOf(val clsName: String, val typeDescriptors: Map<String, List<TypeDescriptor>>) : TypeDescriptor

    data object StringType : TypeDescriptor

    data object BooleanType : TypeDescriptor

    data object Int64Type : TypeDescriptor

    data object IntType : TypeDescriptor

    data class RefType(val name: String) : TypeDescriptor
}

data class TypePropertyDescriptor(
    val name: String,
    val type: TypeDescriptor,
    val format: String?,
    val required: Boolean
)