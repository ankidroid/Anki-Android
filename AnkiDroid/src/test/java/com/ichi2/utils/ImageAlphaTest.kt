// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.InputStream

class ImageAlphaTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun emptyStreamIsTreatedAsHavingAlpha() {
        assertThat(ImageAlpha.hasAlpha(ByteArrayInputStream(byteArrayOf())), equalTo(true))
    }

    @Test
    fun jpegHasNoAlpha() {
        assertThat(ImageAlpha.hasAlpha(stream(jpeg())), equalTo(false))
    }

    @Test
    fun gifIsTreatedAsHavingAlpha() {
        assertThat(ImageAlpha.hasAlpha(stream("GIF89a!!".toByteArray())), equalTo(true))
    }

    @Test
    fun unknownMagicIsTreatedAsHavingAlpha() {
        assertThat(ImageAlpha.hasAlpha(stream("notimage".toByteArray())), equalTo(true))
    }

    @Test
    fun pngColorTypesWithAlpha() {
        assertThat(ImageAlpha.hasAlpha(stream(png(colorType = 4))), equalTo(true))
        assertThat(ImageAlpha.hasAlpha(stream(png(colorType = 6))), equalTo(true))
    }

    @Test
    fun pngOpaqueColorTypesWithoutTrns() {
        assertThat(ImageAlpha.hasAlpha(stream(png(colorType = 0))), equalTo(false))
        assertThat(ImageAlpha.hasAlpha(stream(png(colorType = 2))), equalTo(false))
        assertThat(ImageAlpha.hasAlpha(stream(png(colorType = 3))), equalTo(false))
    }

    @Test
    fun pngTrnsMeansAlpha() {
        val trns = chunk("tRNS", byteArrayOf(0))
        assertThat(ImageAlpha.hasAlpha(stream(png(colorType = 2, extra = trns))), equalTo(true))
    }

    @Test
    fun pngIendWithoutIdatIsOpaque() {
        val bytes = pngSignature() + ihdrChunk(colorType = 2) + chunk("IEND", byteArrayOf())
        assertThat(ImageAlpha.hasAlpha(stream(bytes)), equalTo(false))
    }

    @Test
    fun pngAncillaryChunkIsSkipped() {
        val gamma = chunk("gAMA", ByteArray(4))
        assertThat(ImageAlpha.hasAlpha(stream(png(colorType = 2, extra = gamma))), equalTo(false))
    }

    @Test
    fun pngLargeAncillaryChunkIsSkipped() {
        val large = chunk("gAMA", ByteArray(9000))
        assertThat(ImageAlpha.hasAlpha(stream(png(colorType = 2, extra = large))), equalTo(false))
    }

    @Test
    fun pngTruncatedChunksAreTreatedAsHavingAlpha() {
        assertThat(ImageAlpha.hasAlpha(stream(pngSignature())), equalTo(true))
        assertThat(ImageAlpha.hasAlpha(stream(pngSignature() + byteArrayOf(0, 0))), equalTo(true))
        val shortIhdr = pngSignature() + intBe(5) + "IHDR".toByteArray() + ByteArray(5)
        assertThat(ImageAlpha.hasAlpha(stream(shortIhdr)), equalTo(true))
        val shortIhdrLen = pngSignature() + intBe(5) + "IHDR".toByteArray() + ByteArray(9)
        assertThat(ImageAlpha.hasAlpha(stream(shortIhdrLen)), equalTo(true))
        val truncatedIhdrData = pngSignature() + intBe(13) + "IHDR".toByteArray() + ByteArray(3)
        assertThat(ImageAlpha.hasAlpha(stream(truncatedIhdrData)), equalTo(true))
        val truncatedCrc =
            pngSignature() + intBe(13) + "IHDR".toByteArray() +
                ByteArray(13).also {
                    it[8] = 8
                    it[9] = 2
                } + byteArrayOf(0, 0)
        assertThat(ImageAlpha.hasAlpha(stream(truncatedCrc)), equalTo(true))
        val truncatedAncillary = pngSignature() + ihdrChunk(colorType = 2) + intBe(10) + "gAMA".toByteArray() + byteArrayOf(1)
        assertThat(ImageAlpha.hasAlpha(stream(truncatedAncillary)), equalTo(true))
    }

    @Test
    fun pngNegativeLengthIsTreatedAsHavingAlpha() {
        val bytes = pngSignature() + intBe(Int.MIN_VALUE) + "IHDR".toByteArray()
        assertThat(ImageAlpha.hasAlpha(stream(bytes)), equalTo(true))
    }

    @Test
    fun webpVp8xAlphaFlag() {
        assertThat(ImageAlpha.hasAlpha(stream(webp("VP8X", byteArrayOf(0x10)))), equalTo(true))
        assertThat(ImageAlpha.hasAlpha(stream(webp("VP8X", byteArrayOf(0x00)))), equalTo(false))
    }

    @Test
    fun webpVp8lAlphaBit() {
        assertThat(ImageAlpha.hasAlpha(stream(webp("VP8L", vp8lPayload(hasAlpha = true)))), equalTo(true))
        assertThat(ImageAlpha.hasAlpha(stream(webp("VP8L", vp8lPayload(hasAlpha = false)))), equalTo(false))
        assertThat(ImageAlpha.hasAlpha(stream(webp("VP8L", byteArrayOf(0x00) + ByteArray(4)))), equalTo(true))
    }

    @Test
    fun webpLossyHasNoAlpha() {
        assertThat(ImageAlpha.hasAlpha(stream(webp("VP8 ", byteArrayOf(1)))), equalTo(false))
    }

    @Test
    fun webpUnknownOrInvalidIsTreatedAsHavingAlpha() {
        assertThat(ImageAlpha.hasAlpha(stream(webp("ANIM", byteArrayOf(1)))), equalTo(true))
        val riffWave = "RIFF".toByteArray() + intLe(4) + "WAVE".toByteArray()
        assertThat(ImageAlpha.hasAlpha(stream(riffWave)), equalTo(true))
        val truncatedWebp = "RIFF".toByteArray() + intLe(4)
        assertThat(ImageAlpha.hasAlpha(stream(truncatedWebp)), equalTo(true))
        val noChunk = "RIFF".toByteArray() + intLe(4) + "WEBP".toByteArray()
        assertThat(ImageAlpha.hasAlpha(stream(noChunk)), equalTo(true))
        val noSize = "RIFF".toByteArray() + intLe(4) + "WEBP".toByteArray() + "VP8X".toByteArray()
        assertThat(ImageAlpha.hasAlpha(stream(noSize)), equalTo(true))
        val negativeSize = "RIFF".toByteArray() + intLe(4) + "WEBP".toByteArray() + "VP8X".toByteArray() + intLe(Int.MIN_VALUE)
        assertThat(ImageAlpha.hasAlpha(stream(negativeSize)), equalTo(true))
        val vp8xNoFlags = "RIFF".toByteArray() + intLe(4) + "WEBP".toByteArray() + "VP8X".toByteArray() + intLe(1)
        assertThat(ImageAlpha.hasAlpha(stream(vp8xNoFlags)), equalTo(true))
        val vp8lShort = "RIFF".toByteArray() + intLe(4) + "WEBP".toByteArray() + "VP8L".toByteArray() + intLe(1)
        assertThat(ImageAlpha.hasAlpha(stream(vp8lShort)), equalTo(true))
    }

    @Test
    fun fileOverloadAndIoFailure() {
        val file = temporaryFolder.newFile("bg.jpg")
        file.writeBytes(jpeg())
        assertThat(ImageAlpha.hasAlpha(file), equalTo(false))

        val dir = temporaryFolder.newFolder("not-a-file")
        assertThat(ImageAlpha.hasAlpha(dir), equalTo(true))
    }

    @Test
    fun oneByteReadsStillParseJpeg() {
        assertThat(ImageAlpha.hasAlpha(OneByteAtATimeInputStream(jpeg())), equalTo(false))
    }

    private fun stream(bytes: ByteArray) = ByteArrayInputStream(bytes)

    private fun jpeg() = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)

    private fun pngSignature() = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private fun png(
        colorType: Int,
        extra: ByteArray = byteArrayOf(),
    ): ByteArray = pngSignature() + ihdrChunk(colorType) + extra + chunk("IDAT", byteArrayOf(0))

    private fun ihdrChunk(colorType: Int): ByteArray {
        val data = ByteArray(13)
        data[3] = 1
        data[7] = 1
        data[8] = 8
        data[9] = colorType.toByte()
        return chunk("IHDR", data)
    }

    private fun chunk(
        type: String,
        data: ByteArray,
    ): ByteArray = intBe(data.size) + type.toByteArray(Charsets.US_ASCII) + data + ByteArray(4)

    private fun webp(
        chunkType: String,
        payload: ByteArray,
    ): ByteArray =
        "RIFF".toByteArray(Charsets.US_ASCII) +
            intLe(4) +
            "WEBP".toByteArray(Charsets.US_ASCII) +
            chunkType.toByteArray(Charsets.US_ASCII) +
            intLe(payload.size) +
            payload

    private fun vp8lPayload(hasAlpha: Boolean): ByteArray {
        val bits = if (hasAlpha) 1 shl 28 else 0
        return byteArrayOf(0x2F) + intLe(bits)
    }

    private fun intBe(value: Int) =
        byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte(),
        )

    private fun intLe(value: Int) =
        byteArrayOf(
            value.toByte(),
            (value ushr 8).toByte(),
            (value ushr 16).toByte(),
            (value ushr 24).toByte(),
        )

    private class OneByteAtATimeInputStream(
        bytes: ByteArray,
    ) : InputStream() {
        private val data = bytes
        private var index = 0

        override fun read(): Int {
            if (index >= data.size) return -1
            return data[index++].toInt() and 0xFF
        }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            if (index >= data.size) return -1
            b[off] = data[index++]
            return 1
        }
    }
}
