package com.geckour.q.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.geckour.q.data.db.model.EqualizerLevelRatio
import com.geckour.q.data.db.model.EqualizerPreset
import kotlinx.coroutines.flow.Flow

@Dao
interface EqualizerPresetDao {

    @Query("select * from equalizerPreset inner join equalizerLevelRatio on equalizerPreset.id = equalizerLevelRatio.presetId")
    fun getEqualizerPresets(): Flow<Map<EqualizerPreset, List<EqualizerLevelRatio>>>

    @Query("select * from equalizerPreset inner join equalizerLevelRatio on equalizerPreset.id = equalizerLevelRatio.presetId where equalizerPreset.id = :id")
    suspend fun getEqualizerPreset(id: Long): Map<EqualizerPreset, List<EqualizerLevelRatio>>

    @Query("select * from equalizerLevelRatio where presetId = :presetId")
    suspend fun getEqualizerLevelRatiosByPresetId(presetId: Long): List<EqualizerLevelRatio>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEqualizerPreset(equalizerPreset: EqualizerPreset): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEqualizerLevelRatio(equalizerLevelRatio: EqualizerLevelRatio)

    @Transaction
    suspend fun addEqualizerPreset(
        equalizerPreset: EqualizerPreset,
        equalizerLevelRatios: List<EqualizerLevelRatio>,
        overrideLabelById: ((id: Long) -> String)? = null
    ) {
        val id = upsertEqualizerPreset(equalizerPreset)
        overrideLabelById?.let {
            upsertEqualizerPreset(equalizerPreset.copy(id = id, label = it(id)))
        }
        equalizerLevelRatios.forEach {
            upsertEqualizerLevelRatio(it.copy(id = 0, presetId = id))
        }
    }

    @Delete
    suspend fun deleteEqualizerPreset(equalizerPreset: EqualizerPreset)

    @Delete
    suspend fun deleteEqualizerLevelRatio(equalizerLevelRatio: EqualizerLevelRatio)

    @Transaction
    suspend fun deleteEqualizerPresetRecursively(equalizerPreset: EqualizerPreset) {
        getEqualizerLevelRatiosByPresetId(equalizerPreset.id).forEach {
            deleteEqualizerLevelRatio(it)
        }
        deleteEqualizerPreset(equalizerPreset)
    }
}