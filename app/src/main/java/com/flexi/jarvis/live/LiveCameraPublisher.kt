package com.flexi.jarvis.live

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LiveCameraPublisher(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onJpegFrame: (ByteArray) -> Unit,
    private val onError: (String) -> Unit
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val stopped = AtomicBoolean(false)
    @Volatile private var lastFrameAt = 0L
    @Volatile private var selector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var provider: ProcessCameraProvider? = null

    fun start() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (stopped.get()) return@addListener
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(android.util.Size(640, 480))
                    .build()
                analysis.setAnalyzer(executor) { image ->
                    try {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastFrameAt >= 350L) {
                            lastFrameAt = now
                            yuvToJpeg(image)?.let(onJpegFrame)
                        }
                    } catch (_: Exception) {
                    } finally {
                        image.close()
                    }
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (error: Exception) {
                onError(error.message ?: "Could not start the camera.")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun switchCamera() {
        selector = if (selector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else CameraSelector.DEFAULT_FRONT_CAMERA
        start()
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        provider?.unbindAll()
        executor.shutdownNow()
    }

    private fun yuvToJpeg(image: androidx.camera.core.ImageProxy): ByteArray? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val nv21 = ByteArray(width * height * 3 / 2)
        var output = 0
        val yBuffer = yPlane.buffer
        for (row in 0 until height) {
            val rowStart = row * yPlane.rowStride
            for (col in 0 until width) nv21[output++] = yBuffer.get(rowStart + col * yPlane.pixelStride)
        }
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                nv21[output++] = vBuffer.get(row * vPlane.rowStride + col * vPlane.pixelStride)
                nv21[output++] = uBuffer.get(row * uPlane.rowStride + col * uPlane.pixelStride)
            }
        }
        val stream = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, width, height, null)
            .compressToJpeg(Rect(0, 0, width, height), 58, stream)
        return stream.toByteArray()
    }
}
