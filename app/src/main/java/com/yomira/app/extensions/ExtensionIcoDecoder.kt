package com.yomira.app.extensions

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.fetch.SourceResult
import coil.request.Options

/** Request-local: favicon support does not alter manga/reader image decoding. */
internal class ExtensionIcoDecoder(private val result: SourceResult, private val options: Options) : Decoder {
    override suspend fun decode(): DecodeResult {
        val source = result.source.source()
        require(!source.request(IcoImage.MAX_BYTES + 1)) { "Favicon too large" }
        val bitmap = when (val frame = IcoImage.read(source.readByteArray())) {
            is IcoImage.Frame.Png -> {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(frame.bytes, 0, frame.bytes.size, bounds)
                require(bounds.outWidth in 1..256 && bounds.outHeight in 1..256) { "Invalid favicon dimensions" }
                requireNotNull(BitmapFactory.decodeByteArray(frame.bytes, 0, frame.bytes.size))
            }
            is IcoImage.Frame.Argb -> Bitmap.createBitmap(frame.pixels, frame.width, frame.height, Bitmap.Config.ARGB_8888)
            null -> error("Unsupported favicon")
        }
        return DecodeResult(BitmapDrawable(options.context.resources, bitmap), isSampled = false)
    }

    object Factory : Decoder.Factory {
        override fun create(result: SourceResult, options: Options, imageLoader: ImageLoader): Decoder? {
            val peek = result.source.source().peek()
            if (!peek.request(4) || peek.readByte().toInt() != 0 || peek.readByte().toInt() != 0 ||
                peek.readByte().toInt() != 1 || peek.readByte().toInt() != 0) return null
            return ExtensionIcoDecoder(result, options)
        }
    }
}
