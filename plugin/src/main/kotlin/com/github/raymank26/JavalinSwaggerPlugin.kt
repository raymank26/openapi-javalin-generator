package com.github.raymank26

import org.gradle.api.Plugin
import org.gradle.api.Project

class JavalinSwaggerPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.tasks.create("generateSwaggerClient") {
            println("HERE")
        }
    }
}