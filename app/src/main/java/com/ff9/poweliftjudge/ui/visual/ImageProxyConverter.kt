package com.ff9.poweliftjudge.ui.visual

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Converts CameraX ImageProxy (YUV_420_888) to a software Bitmap.
 * Handles plane stride correctly so the result is artifact-free at all
 * camera resolutions.
 */
object ImageProxyConverter {

    fun toBitmap(proxy: ImageProxy): Bitmap? {
        if (proxy.format != ImageFormat.YUV_420_888) return null
        val nv21 = yuv420ToNv21(proxy)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, proxy.width, proxy.height, null)
        val baos = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, proxy.width, proxy.height), 90, baos)
        val bytes = baos.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /** YUV_420_888 → NV21 byte array, respecting plane row & pixel strides. */
    private fun yuv420ToNv21(proxy: ImageProxy): ByteArray {
        val w = proxy.width
        val h = proxy.height
        val ySize = w * h
        val uvSize = w * h / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yPlane = proxy.planes[0]
        val uPlane = proxy.planes[1]
        val vPlane = proxy.planes[2]

        // Y plane
        copyPlane(yPlane.buffer, yPlane.rowStride, yPlane.pixelStride, w, h, nv21, 0)

        // VU interleaved (NV21 = YYYY...VUVU)
        val chromaW = w / 2
        val chromaH = h / 2
        val vBuf = vPlane.buffer
        val uBuf = uPlane.buffer
        val vRow = vPlane.rowStride; val vPx = vPlane.pixelStride
        val uRow = uPlane.rowStride; val uPx = uPlane.pixelStride

        var dst = ySize
        for (row in 0 until chromaH) {
            for (col in 0 until chromaW) {
                val vIdx = row * vRow + col * vPx
                val uIdx = row * uRow + col * uPx
                nv21[dst++] = vBuf.get(vIdx)
                nv21[dst++] = uBuf.get(uIdx)
            }
        }
        return nv21
    }

    private fun copyPlane(
        buffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        out: ByteArray,
        outOffset: Int
    ) {
        var dst = outOffset
        if (pixelStride == 1 && rowStride == width) {
            // Tight packing — single bulk read.
            buffer.position(0)
            buffer.get(out, dst, width * height)
            return
        }
        val rowBuf = ByteArray(rowStride)
        for (row in 0 until height) {
            buffer.position(row * rowStride)
            val toRead = minOf(rowStride, buffer.remaining())
            buffer.get(rowBuf, 0, toRead)
            if (pixelStride == 1) {
                System.arraycopy(rowBuf, 0, out, dst, width)
            } else {
                for (col in 0 until width) out[dst + col] = rowBuf[col * pixelStride]
            }
            dst += width
        }
    }
}
