package io.ps3mio

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private var sp: SharedPreferences? = null

    fun init(c: Context) {
        if (sp == null) sp = c.applicationContext.getSharedPreferences("ps3mio", Context.MODE_PRIVATE)
    }

    private fun s(): SharedPreferences = sp ?: throw IllegalStateException("Prefs.init not called")

    var authKey: String
        get() = s().getString("authKey", "") ?: ""
        set(v) { s().edit().putString("authKey", v).apply() }

    var addonsJson: String
        get() = s().getString("addons", "[]") ?: "[]"
        set(v) { s().edit().putString("addons", v).apply() }

    var syncedAt: Long
        get() = s().getLong("syncedAt", 0L)
        set(v) { s().edit().putLong("syncedAt", v).apply() }

    fun getStr(key: String, def: String): String = s().getString(key, def) ?: def

    fun setStr(key: String, v: String) {
        s().edit().putString(key, v).apply()
    }

    fun getPos(id: String): Long = s().getLong("pos:$id", 0L)

    fun setPos(id: String, ms: Long) {
        s().edit().putLong("pos:$id", ms).apply()
    }
}
