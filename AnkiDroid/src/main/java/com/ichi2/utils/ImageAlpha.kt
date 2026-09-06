// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

/** Detects whether an image file has an alpha channel, from headers only. */
object ImageAlpha {
    fun hasAlpha(file: File): Boolean =
        try {
            file.inputStream().buffered().use { hasAlpha(it) }
        } catch (_: IOException) {
            true
        }

    fun hasAlpha(stream: InputStream): Boolean {
        val header = readExact(stream, 8) ?: return true
        return when {
            isJpeg(header) -> false
            isPng(header) -> pngHasAlpha(stream)
            isGif(header) -> true
            isRiff(header) -> webpHasAlpha(stream)
            else -> true
        }
    }

    private fun isJpeg(header: ByteArray): Boolean = header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()

    private fun isPng(header: ByteArray): Boolean =
        header[0] == 0x89.toByte() &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() &&
            header[3] == 0x47.toByte() &&
            header[4] == 0x0D.toByte() &&
            header[5] == 0x0A.toByte() &&
            header[6] == 0x1A.toByte() &&
            header[7] == 0x0A.toByte()

    private fun isGif(header: ByteArray): Boolean =
        header[0] == 0x47.toByte() &&
            header[1] == 0x49.toByte() &&
            header[2] == 0x46.toByte() &&
            header[3] == 0x38.toByte()

    private fun isRiff(header: ByteArray): Boolean =
        header[0] == 0x52.toByte() &&
            header[1] == 0x49.toByte() &&
            header[2] == 0x46.toByte() &&
            header[3] == 0x46.toByte()

    private fun pngHasAlpha(stream: InputStream): Boolean {
        while (true) {
            val lenBytes = readExact(stream, 4) ?: return true
            val typeBytes = readExact(stream, 4) ?: return true
            val length = beInt(lenBytes, 0)
            if (length < 0) return true
            val type = String(typeBytes, Charsets.US_ASCII)
            when (type) {
                "IHDR" -> {
                    val data = readExact(stream, length) ?: return true
                    if (length < 10) return true
                    val colorType = data[9].toInt() and 0xFF
                    if (colorType == 4 || colorType == 6) {
                        return true
                    }
                    if (!skipExact(stream, 4)) return true
                }
                "tRNS" -> return true
                "IDAT" -> return false
                "IEND" -> return false
                else -> {
                    if (!skipExact(stream, length.toLong() + 4)) return true
                }
            }
        }
    }

    private fun webpHasAlpha(stream: InputStream): Boolean {
        val fourCc = readExact(stream, 4) ?: return true
        if (!fourCc.contentEquals("WEBP".toByteArray(Charsets.US_ASCII))) {
            return true
        }
        val chunkType = readExact(stream, 4) ?: return true
        val sizeBytes = readExact(stream, 4) ?: return true
        val size = leInt(sizeBytes, 0)
        if (size < 0) return true
        return when (String(chunkType, Charsets.US_ASCII)) {
            "VP8X" -> {
                val flags = readExact(stream, 1) ?: return true
                flags[0].toInt() and 0x10 != 0
            }
            "VP8L" -> {
                val payload = readExact(stream, 5) ?: return true
                if (payload[0] != 0x2F.toByte()) return true
                val bits = leInt(payload, 1)
                bits and (1 shl 28) != 0
            }
            "VP8 " -> false
            else -> true
        }
    }

    private fun readExact(
        stream: InputStream,
        count: Int,
    ): ByteArray? {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = stream.read(buffer, offset, count - offset)
            if (read < 0) return null
            offset += read
        }
        return buffer
    }

    private fun skipExact(
        stream: InputStream,
        count: Long,
    ): Boolean {
        var remaining = count
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val toRead = min(buf.size.toLong(), remaining).toInt()
            val read = stream.read(buf, 0, toRead)
            if (read < 0) return false
            remaining -= read
        }
        return true
    }

    private fun beInt(
        bytes: ByteArray,
        offset: Int,
    ): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun leInt(
        bytes: ByteArray,
        offset: Int,
    ): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
