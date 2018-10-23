package com.geckour.q.util

import androidx.room.TypeConverter
import com.geckour.q.data.db.model.Bool

class BoolConverter {
    @TypeConverter
    fun fromBool(bool: Bool): Int = when (bool) {
        Bool.TRUE -> 1
        Bool.FALSE -> 0
        Bool.UNDEFINED -> -1
    }

    @TypeConverter
    fun toBool(value: Int): Bool = when (value) {
        1 -> Bool.TRUE
        0 -> Bool.FALSE
        else -> Bool.UNDEFINED
    }

    fun toBoolean(value: Int) = toBoolean(toBool(value))

    fun toBoolean(bool: Bool): Boolean? = when (bool) {
        Bool.TRUE -> true
        Bool.FALSE -> false
        Bool.UNDEFINED -> null
    }

    fun fromBoolean(boolean: Boolean?): Bool = when (boolean) {
        true -> Bool.TRUE
        false -> Bool.FALSE
        null -> Bool.UNDEFINED
    }
}