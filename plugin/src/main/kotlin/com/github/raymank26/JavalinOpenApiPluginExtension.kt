package com.github.raymank26

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.provider.Property

interface JavalinOpenApiPluginExtension {

    val targets: NamedDomainObjectContainer<OutputTarget>
}

interface OutputTarget {

    val name: String

    val basePackageName: Property<String>

    val specName: Property<String>

    val generateServerCode: Property<Boolean>
}