package com.github.raymank26

import org.gradle.api.provider.Property

interface JavalinOpenApiPluginExtension {
    val basePackageName: Property<String>
}