package com.scaso.drclawapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SessionTemplate(
    val name: String,
    val role: String? = null,
    val model: String? = null,
    val project: String? = null,
    val cwd: String = ".",
    val backend: String = "ironjaw",
)
