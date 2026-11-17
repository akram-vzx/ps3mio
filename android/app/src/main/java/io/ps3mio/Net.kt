package io.ps3mio

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Tiny networking + threading helpers (no external libraries, light on memory). */
object Net {
    private const val UA = "ps3mio/0.1 (Stremio client)"
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(6)

    fun bg(block: () -> Unit) {
        pool.execute {
            try {
                block()
            } catch (e: Throwable) {
                Log.e("ps3mio", "background task failed", e)
            }
        }
    }

    fun ui(block: () -> Unit) {
        main.post { block() }
    }

    fun bytes(url: String, limit: Int = 8_000_000, timeoutMs: Int = 15000): ByteArray? {
        var current = url
        for (hop in 0..6) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(current).openConnection() as HttpURLConnection
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", UA)
                conn.setRequestProperty("Accept", "application/json, */*")
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return null
                    current = URL(URL(current), loc).toString()
                    continue
                }
                if (code >= 400) return null
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                conn.inputStream.use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > limit) return null
                    }
                }
                return out.toByteArray()
            } catch (e: Exception) {
                Log.w("ps3mio", "GET failed: $current ${e.message}")
                return null
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    fun get(url: String, timeoutMs: Int = 15000): String? =
        bytes(url, 8_000_000, timeoutMs)?.toString(Charsets.UTF_8)

    fun getJson(url: String): JSONObject? {
        val txt = get(url) ?: return null
        return try {
            JSONObject(txt)
        } catch (e: Exception) {
            null
        }
    }

    fun postJson(url: String, body: JSONObject): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode >= 400) conn.errorStream else conn.inputStream
            val txt = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return null
            JSONObject(txt)
        } catch (e: Exception) {
            Log.w("ps3mio", "POST failed: $url ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }
}
