package com.example.arcarcustomizer.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * The app's on-device SQLite database. Plain [SQLiteOpenHelper] rather than Room: the state is a
 * handful of rows, and Room's annotation processor (KSP) would have to track this project's
 * bleeding-edge Kotlin version.
 *
 * Tables:
 * - `app_state`: key/value rows for single values (selected car, paint, open tab, open screen).
 * - `part_selections`: one row per [com.example.arcarcustomizer.customization.PartSlot] id the
 *   user has picked an option for.
 *
 * Everything is stored by stable id/label, never by list index, so reordering or extending the
 * catalog in code doesn't scramble a saved build — unknown values are simply ignored on load.
 */
class AppDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE_STATE (" +
                "$COL_KEY TEXT PRIMARY KEY NOT NULL, " +
                "$COL_VALUE TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE $TABLE_PARTS (" +
                "$COL_SLOT_ID TEXT PRIMARY KEY NOT NULL, " +
                "$COL_OPTION_LABEL TEXT NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Add migrations here when VERSION is bumped.
    }

    /** All `app_state` rows. */
    fun readState(): Map<String, String> = readPairs(TABLE_STATE, COL_KEY, COL_VALUE)

    /** All `part_selections` rows, slot id → option label. */
    fun readPartSelections(): Map<String, String> =
        readPairs(TABLE_PARTS, COL_SLOT_ID, COL_OPTION_LABEL)

    /** Replaces the whole saved state with [state] and [partSelections] in one transaction. */
    fun writeAll(state: Map<String, String>, partSelections: Map<String, String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_STATE, null, null)
            state.forEach { (key, value) ->
                db.insertOrThrow(TABLE_STATE, null, ContentValues().apply {
                    put(COL_KEY, key)
                    put(COL_VALUE, value)
                })
            }
            db.delete(TABLE_PARTS, null, null)
            partSelections.forEach { (slotId, label) ->
                db.insertOrThrow(TABLE_PARTS, null, ContentValues().apply {
                    put(COL_SLOT_ID, slotId)
                    put(COL_OPTION_LABEL, label)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun readPairs(table: String, keyCol: String, valueCol: String): Map<String, String> =
        readableDatabase.query(table, arrayOf(keyCol, valueCol), null, null, null, null, null)
            .use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1))
                }
            }

    companion object {
        private const val NAME = "ar_car_customizer.db"
        private const val VERSION = 1

        private const val TABLE_STATE = "app_state"
        private const val COL_KEY = "key"
        private const val COL_VALUE = "value"

        private const val TABLE_PARTS = "part_selections"
        private const val COL_SLOT_ID = "slot_id"
        private const val COL_OPTION_LABEL = "option_label"

        @Volatile
        private var instance: AppDatabase? = null

        /** App-wide instance; SQLiteOpenHelper is meant to be shared, not reopened per screen. */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: AppDatabase(context.applicationContext).also { instance = it }
            }
    }
}
