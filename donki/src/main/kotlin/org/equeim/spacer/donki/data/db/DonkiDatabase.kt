package org.equeim.spacer.donki.data.db

import androidx.room.RoomDatabase
import androidx.room.TypeConverters

//@Database(entities = [], exportSchema = false, version = 1)
@TypeConverters(Converters::class)
internal abstract class DonkiDatabase : RoomDatabase() {
    companion object {
        const val NAME = "nasa-donki"
    }
}
