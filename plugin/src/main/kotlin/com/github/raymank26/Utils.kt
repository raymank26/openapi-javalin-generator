package com.github.raymank26

import java.util.*

fun String.decapitalized(): String {
    return replaceFirstChar { it.lowercase(Locale.getDefault()) }
}