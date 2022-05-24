package org.equeim.spacer.donki.data.db

import android.content.Context
import androidx.room.Room

internal class DonkiDataSourceDb(context: Context) {
    private val db =
        Room.databaseBuilder(context, DonkiDatabase::class.java, DonkiDatabase.NAME).build()
}
