package com.enverolab.comandas.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.io.InputStream
import com.enverolab.comandas.domain.*
import org.json.JSONArray
import org.json.JSONObject

class PosDatabase(val context: Context) : SQLiteOpenHelper(context, "restaurant.db", null, 12) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        context.assets
            .open("schema_v10.sql")
            .bufferedReader()
            .readText()
            .split(';')
            .filter { it.isNotBlank() }
            .forEach(db::execSQL)
        val seed = JSONArray(context.assets.open("seed.json").bufferedReader().readText())
        for (i in 0 until seed.length()) {
            val entry = seed.getJSONObject(i)
            val obj = entry.getJSONObject("values")
            val values = obj.keys().asSequence().associateWith { obj.get(it) }.toMutableMap()
            if (entry.getString("table") == "users")
                values["password"] = Passwords.hash(values["password"].toString())
            db.insertOrThrow(entry.getString("table"), null, content(values))
        }
        db.insertOrThrow(
            "Folios",
            null,
            content(mapOf("folio_comanda" to 0, "folio_venta" to 0, "folio_cierre" to 0)),
        )
        nativeTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        require(old in 10..11 && new == 12) { "Versión $old sin migración validada" }
        nativeTables(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        nativeTables(db)
    }

    private fun nativeTables(db: SQLiteDatabase) {
        com.enverolab.comandas.corporate.CorporateStore.create(db)
        val fields =
            db.rawQuery("PRAGMA table_info(Empresa)", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(1)) }
            }
        if ("logo" !in fields) db.execSQL("ALTER TABLE Empresa ADD COLUMN logo TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS native_inventory_moves (id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER, product_name TEXT, quantity REAL NOT NULL, direction TEXT NOT NULL, fecha TEXT NOT NULL, hora TEXT NOT NULL, sale_id INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS native_payment_meta (payment_id INTEGER PRIMARY KEY, factor TEXT NOT NULL, received TEXT NOT NULL, tip_received TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS native_item_quantities (payment_id INTEGER NOT NULL, order_item_id INTEGER NOT NULL, quantity TEXT NOT NULL, PRIMARY KEY(payment_id, order_item_id))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS native_cash_meta (closure_id INTEGER PRIMARY KEY, user_id INTEGER, counted TEXT, difference TEXT)"
        )
    }

    @Synchronized
    fun rows(sql: String, args: List<Any?> = emptyList(), db: SQLiteDatabase? = null): List<Row> =
        (db ?: readableDatabase)
            .rawQuery(sql, args.map { it?.toString().orEmpty() }.toTypedArray())
            .use { c ->
                buildList {
                    while (c.moveToNext()) add(
                        c.columnNames
                            .mapIndexed { i, name ->
                                name to
                                    when (c.getType(i)) {
                                        Cursor.FIELD_TYPE_NULL -> null
                                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                                        Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
                                        else -> c.getString(i)
                                    }
                            }
                            .toMap()
                    )
                }
            }

    @Synchronized
    fun insert(table: String, values: Row, db: SQLiteDatabase? = null) =
        (db ?: writableDatabase).insertOrThrow(table, null, content(values))

    @Synchronized
    fun update(table: String, id: Long, values: Row, db: SQLiteDatabase? = null) =
        (db ?: writableDatabase).update(table, content(values), "id=?", arrayOf(id.toString()))

    @Synchronized
    fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val result = block(db)
            db.setTransactionSuccessful()
            result
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun exportDatabase(): File {
        val db = writableDatabase
        db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use {
            it.moveToFirst()
            check(it.getInt(0) == 0) { "Respaldo ocupado" }
        }
        val out = File(context.cacheDir, "exports/restaurant.db")
        out.parentFile!!.mkdirs()
        close()
        context.getDatabasePath("restaurant.db").copyTo(out, overwrite = true)
        writableDatabase
        return out
    }

    @Synchronized
    fun importDatabase(input: InputStream): File {
        val candidate = File(context.cacheDir, "candidate.db")
        input.use { source -> candidate.outputStream().use { target -> source.copyTo(target) } }
        SQLiteDatabase.openDatabase(candidate.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val version = db.version
            require(version in 10..12) { "Se requiere base versión 10 a 12; recibida $version" }
            require(rows("PRAGMA integrity_check", db = db).first().values.first() == "ok") {
                "Base dañada"
            }
            val columns =
                JSONObject(context.assets.open("legacy_columns.json").bufferedReader().readText())
            columns.keys().forEach { table ->
                require(
                    rows(
                            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                            listOf(table),
                            db,
                        )
                        .isNotEmpty()
                ) {
                    "Falta tabla $table"
                }
                val array = columns.getJSONArray(table)
                val expected = List(array.length()) { array.getString(it) }
                val actual = rows("PRAGMA table_xinfo($table)", db = db).map { it.text("name") }
                require(expected.all { it in actual }) { "Esquema incompatible en $table" }
            }
        }
        val backup =
            exportDatabase()
                .copyTo(File(context.filesDir, "pre-import-${System.currentTimeMillis()}.db"))
        close()
        val target = context.getDatabasePath("restaurant.db")
        File(target.path + "-wal").delete()
        File(target.path + "-shm").delete()
        try {
            candidate.copyTo(target, overwrite = true)
            writableDatabase
        } catch (e: Exception) {
            close()
            backup.copyTo(target, overwrite = true)
            writableDatabase
            throw e
        } finally {
            candidate.delete()
        }
        return backup
    }

    companion object {
        fun content(values: Row) =
            ContentValues().apply {
                values.forEach { (key, value) ->
                    when (value) {
                        null -> putNull(key)
                        is ByteArray -> put(key, value)
                        is Boolean -> put(key, if (value) 1 else 0)
                        is Number -> put(key, value.toString())
                        else -> put(key, value.toString())
                    }
                }
            }
    }
}
