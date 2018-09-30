package com.geckour.q.domain.model

data class LicenseItem(
        val name: String,
        val text: String,
        var stateOpen: Boolean = false
)