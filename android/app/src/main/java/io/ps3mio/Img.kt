package io.ps3mio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView

/** Minimal async image loader: down-samples while decoding so posters stay small in RAM. */
object Img {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(view: ImageView, url: String?, maxW: Int = 400) {
        view.tag = url
        if (url.isNullOrEmpty()) {
            view.setImageDrawable(null)
            return
        }
        val hit = cache.get(url)
        if (hit != null) {
            view.setImageBitmap(hit)
            return
        }
        view.setImageDrawable(null)
        Net.bg {
            val data = Net.bytes(url, 6_000_000)
            val bmp = if (data != null) decode(data, maxW) else null
            if (bmp != null) {
                cache.put(url, bmp)
                Net.ui { if (view.tag == url) view.setImageBitmap(bmp) }
            }
        }
    }

    private fun decode(data: ByteArray, maxW: Int): Bitmap? {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxW) sample *= 2
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        return BitmapFactory.decodeByteArray(data, 0, data.size, opts)
    }
}
