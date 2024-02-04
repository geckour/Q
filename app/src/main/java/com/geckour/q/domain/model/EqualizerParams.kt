package com.geckour.q.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class EqualizerParams(
    val levelRange: Pair<Int, Int>,
    val bands: List<Band>
) {

    fun normalizedLevel(ratio: Float): Int =
        levelRange.first + ((levelRange.second - levelRange.first) * ratio).toInt()

    @Serializable
    data class Band(
        val freqRange: Pair<Int, Int>,
        val centerFreq: Int
    )
}
