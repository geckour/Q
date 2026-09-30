package com.geckour.q.buildlogic

import org.gradle.api.Project
import java.util.Properties

fun Project.secretProperty(key: String): String? =
    Properties()
        .apply { rootProject.file("secret.properties").inputStream().use { load(it) } }
        .getProperty(key)
