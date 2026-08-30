package com.amarjeetmaan.ajlivestudio.streaming

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import io.github.thibaultbee.streampack.core.elements.processing.video.ISurfaceProcessorInternal
import io.github.thibaultbee.streampack.core.elements.processing.video.outputs.ISurfaceOutput
import io.github.thibaultbee.streampack.core.elements.utils.av.video.DynamicRangeProfile
import io.github.thibaultbee.streampack.core.pipelines.IVideoDispatcherProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch

/**
 * StreamPack SurfaceProcessor that composites the camera frame and the latest overlay bitmap
 * before the frame reaches the encoder.
 *
 * There is deliberately no MediaProjection / screen-capture path here.
 */
class OverlayCompositor : ISurfaceProcessorInternal {

    class Factory : ISurfaceProcessorInternal.Factory {
        @Volatile
        private var processor: OverlayCompositor? = null

        override fun create(
            dynamicRangeProfile: DynamicRangeProfile,
            dispatcherProvider: IVideoDispatcherProvider
        ): ISurfaceProcessorInternal {
            return OverlayCompositor().also { processor = it }
        }

        fun setOverlayBitmap(bitmap: Bitmap?) {
            processor?.setOverlayBitmap(bitmap)
        }
    }

    override var isMuted: Boolean = false

    private val glThread = HandlerThread("AJLiveStudio-OverlayGL").apply { start() }
    private val glHandler = Handler(glThread.looper)

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null

    private var cameraTextureId = 0
    private var overlayTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null

    private var cameraProgram = 0
    private var overlayProgram = 0

    private val textureMatrix = FloatArray(16)
    private var framePending = false
    private var released = false

    private var overlayVersion = 0L
    private var uploadedOverlayVersion = -1L
    private var pendingOverlay: Bitmap? = null
    private var uploadedOverlay: Bitmap? = null
    private var hasOverlay = false

    private data class OutputEntry(
        val output: ISurfaceOutput,
        val eglSurface: EGLSurface
    )

    private val outputs = mutableListOf<OutputEntry>()

    init {
        runOnGlThreadBlocking { initEgl() }
    }

    fun setOverlayBitmap(bitmap: Bitmap?) {
        if (released) return

        synchronized(this) {
            pendingOverlay = bitmap
            overlayVersion++
        }

        glHandler.post {
            if (!released && framePending && outputs.isNotEmpty()) {
                renderPendingFrame()
            }
        }
    }

    override fun createInputSurface(surfaceSize: Size, timebase: io.github.thibaultbee.streampack.core.elements.utils.time.Timebase): Surface {
        check(!released) { "OverlayCompositor is released" }

        var result: Surface? = null
        runOnGlThreadBlocking {
            check(eglContext != EGL14.EGL_NO_CONTEXT) { "EGL context is not initialized" }

            if (cameraTextureId == 0) {
                cameraTextureId = createExternalTexture()
            }

            surfaceTexture?.setOnFrameAvailableListener(null)
            surfaceTexture?.release()
            inputSurface?.release()

            val st = SurfaceTexture(cameraTextureId)
            st.setDefaultBufferSize(surfaceSize.width, surfaceSize.height)
            st.setOnFrameAvailableListener({
                if (released) return@setOnFrameAvailableListener
                framePending = true
                renderPendingFrame()
            }, glHandler)

            surfaceTexture = st
            inputSurface = Surface(st)
            result = inputSurface
        }

        return checkNotNull(result)
    }

    override fun removeInputSurface(surface: Surface) {
        runOnGlThread {
            if (inputSurface === surface || inputSurface == surface) {
                surfaceTexture?.setOnFrameAvailableListener(null)
                surfaceTexture?.release()
                surfaceTexture = null
                inputSurface?.release()
                inputSurface = null
                framePending = false
            }
        }
    }

    override fun addOutputSurface(surfaceOutput: ISurfaceOutput) {
        runOnGlThread {
            if (released) return@runOnGlThread

            val targetSurface = surfaceOutput.targetSurface
            val eglSurface = createWindowSurface(targetSurface)

            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                Log.e(TAG, "Unable to create EGL output surface: 0x${Integer.toHexString(EGL14.eglGetError())}")
                return@runOnGlThread
            }

            outputs.removeAll { old ->
                if (old.output === surfaceOutput) {
                    destroyWindowSurface(old.eglSurface)
                    true
                } else {
                    false
                }
            }

            outputs += OutputEntry(surfaceOutput, eglSurface)

            if (framePending) {
                renderPendingFrame()
            }
        }
    }

    override fun removeOutputSurface(surface: Surface) {
        runOnGlThread {
            val iterator = outputs.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.output.targetSurface == surface) {
                    destroyWindowSurface(entry.eglSurface)
                    iterator.remove()
                }
            }
        }
    }

    override fun removeOutputSurface(surfaceOutput: ISurfaceOutput) {
        runOnGlThread {
            val iterator = outputs.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.output === surfaceOutput) {
                    destroyWindowSurface(entry.eglSurface)
                    iterator.remove()
                }
            }
        }
    }

    override fun removeAllOutputSurfaces() {
        runOnGlThread {
            outputs.forEach { destroyWindowSurface(it.eglSurface) }
            outputs.clear()
        }
    }

    override fun setTimebase(
        surface: Surface,
        timebase: io.github.thibaultbee.streampack.core.elements.utils.time.Timebase
    ) {
        // StreamPack owns the timebase. The camera SurfaceTexture timestamp is preserved per frame.
    }

    override fun release() {
        if (released) return
        released = true

        runOnGlThreadBlocking {
            surfaceTexture?.setOnFrameAvailableListener(null)
            surfaceTexture?.release()
            surfaceTexture = null

            inputSurface?.release()
            inputSurface = null

            outputs.forEach { destroyWindowSurface(it.eglSurface) }
            outputs.clear()

            if (cameraTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(cameraTextureId), 0)
                cameraTextureId = 0
            }
            if (overlayTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(overlayTextureId), 0)
                overlayTextureId = 0
            }
            if (cameraProgram != 0) GLES20.glDeleteProgram(cameraProgram)
            if (overlayProgram != 0) GLES20.glDeleteProgram(overlayProgram)

            cameraProgram = 0
            overlayProgram = 0
            pendingOverlay = null
            uploadedOverlay = null
            releaseEgl()
        }

        glThread.quitSafely()
    }

    private fun renderPendingFrame() {
        if (released || !framePending) return
        val st = surfaceTexture ?: return
        if (outputs.isEmpty()) return

        try {
            // Consume exactly one latest camera buffer on the GL thread.
            st.updateTexImage()
            st.getTransformMatrix(textureMatrix)
            val timestampNs = st.timestamp

            framePending = false
            uploadLatestOverlay()

            val snapshot = outputs.toList()
            for (entry in snapshot) {
                if (released) return

                if (!EGL14.eglMakeCurrent(
                        eglDisplay,
                        entry.eglSurface,
                        entry.eglSurface,
                        eglContext
                    )
                ) {
                    Log.e(TAG, "eglMakeCurrent failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
                    continue
                }

                val size = entry.output.targetResolution
                GLES20.glViewport(0, 0, size.width, size.height)
                GLES20.glDisable(GLES20.GL_BLEND)
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                val outputMatrix = FloatArray(16)
                entry.output.updateTransformMatrix(outputMatrix, textureMatrix)

                drawCamera(outputMatrix)
                if (hasOverlay) drawOverlay()

                // SurfaceTexture.timestamp is the timestamp belonging to this camera frame.
                // Pass it unchanged to the encoder surface.
                if (timestampNs > 0L) {
                    EGLExt.eglPresentationTimeANDROID(
                        eglDisplay,
                        entry.eglSurface,
                        timestampNs
                    )
                }

                if (!EGL14.eglSwapBuffers(eglDisplay, entry.eglSurface)) {
                    val error = EGL14.eglGetError()
                    if (error != EGL14.EGL_SUCCESS && error != EGL14.EGL_BAD_NATIVE_WINDOW) {
                        Log.e(TAG, "eglSwapBuffers failed: 0x${Integer.toHexString(error)}")
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "GPU frame processing failed", t)
            // Do not kill the GL thread. StreamPack can continue delivering later frames.
        }
    }

    private fun uploadLatestOverlay() {
        val version: Long
        val bitmap: Bitmap?
        synchronized(this) {
            version = overlayVersion
            bitmap = pendingOverlay
        }

        if (version == uploadedOverlayVersion) return
        uploadedOverlayVersion = version

        if (bitmap == null || bitmap.isRecycled) {
            hasOverlay = false
            uploadedOverlay?.let { if (!it.isRecycled) it.recycle() }
            uploadedOverlay = null
            return
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        hasOverlay = true

        val old = uploadedOverlay
        uploadedOverlay = bitmap
        if (old != null && old !== bitmap && !old.isRecycled) {
            old.recycle()
        }
    }

    private fun drawCamera(matrix: FloatArray) {
        GLES20.glUseProgram(cameraProgram)
        GLES20.glDisable(GLES20.GL_BLEND)

        val positionLocation = GLES20.glGetAttribLocation(cameraProgram, "aPosition")
        val textureLocation = GLES20.glGetAttribLocation(cameraProgram, "aTextureCoord")
        val matrixLocation = GLES20.glGetUniformLocation(cameraProgram, "uTexMatrix")
        val samplerLocation = GLES20.glGetUniformLocation(cameraProgram, "sTexture")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glUniform1i(samplerLocation, 0)
        GLES20.glUniformMatrix4fv(matrixLocation, 1, false, matrix, 0)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionLocation)

        cameraTexCoordBuffer.position(0)
        GLES20.glVertexAttribPointer(textureLocation, 2, GLES20.GL_FLOAT, false, 0, cameraTexCoordBuffer)
        GLES20.glEnableVertexAttribArray(textureLocation)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionLocation)
        GLES20.glDisableVertexAttribArray(textureLocation)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    private fun drawOverlay() {
        GLES20.glUseProgram(overlayProgram)
        GLES20.glEnable(GLES20.GL_BLEND)
        // Android Bitmap uses straight alpha.
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val positionLocation = GLES20.glGetAttribLocation(overlayProgram, "aPosition")
        val textureLocation = GLES20.glGetAttribLocation(overlayProgram, "aTextureCoord")
        val samplerLocation = GLES20.glGetUniformLocation(overlayProgram, "sTexture")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
        GLES20.glUniform1i(samplerLocation, 0)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionLocation)

        overlayTexCoordBuffer.position(0)
        GLES20.glVertexAttribPointer(textureLocation, 2, GLES20.GL_FLOAT, false, 0, overlayTexCoordBuffer)
        GLES20.glEnableVertexAttribArray(textureLocation)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionLocation)
        GLES20.glDisableVertexAttribArray(textureLocation)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL display" }

        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            "Unable to initialize EGL"
        }

        val configAttributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(eglDisplay, configAttributes, 0, configs, 0, 1, count, 0)) {
            "eglChooseConfig failed"
        }
        eglConfig = configs[0] ?: error("No EGLConfig available")

        val contextAttributes = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            eglConfig,
            EGL14.EGL_NO_CONTEXT,
            contextAttributes,
            0
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "Unable to create EGL context" }

        val pbufferAttributes = intArrayOf(
            EGL14.EGL_WIDTH, 1,
            EGL14.EGL_HEIGHT, 1,
            EGL14.EGL_NONE
        )
        val pbuffer = EGL14.eglCreatePbufferSurface(
            eglDisplay,
            eglConfig,
            pbufferAttributes,
            0
        )
        check(pbuffer != EGL14.EGL_NO_SURFACE) { "Unable to create EGL pbuffer" }

        check(EGL14.eglMakeCurrent(eglDisplay, pbuffer, pbuffer, eglContext)) {
            "Unable to make EGL context current"
        }

        cameraProgram = buildProgram(CAMERA_VERTEX_SHADER, CAMERA_FRAGMENT_SHADER)
        overlayProgram = buildProgram(OVERLAY_VERTEX_SHADER, OVERLAY_FRAGMENT_SHADER)
        overlayTextureId = create2DTexture()

        EGL14.eglMakeCurrent(
            eglDisplay,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT
        )
        EGL14.eglDestroySurface(eglDisplay, pbuffer)
    }

    private fun createWindowSurface(surface: Surface): EGLSurface {
        val result = EGL14.eglCreateWindowSurface(
            eglDisplay,
            checkNotNull(eglConfig),
            surface,
            intArrayOf(EGL14.EGL_NONE),
            0
        )
        return result
    }

    private fun destroyWindowSurface(surface: EGLSurface) {
        if (surface != EGL14.EGL_NO_SURFACE && eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglDestroySurface(eglDisplay, surface)
        }
    }

    private fun createExternalTexture(): Int {
        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        return texture[0]
    }

    private fun create2DTexture(): Int {
        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return texture[0]
    }

    private fun releaseEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                eglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
            }
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglConfig = null
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("Shader compile failed: $log")
        }
        return shader
    }

    private fun buildProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            error("Program link failed: $log")
        }
        return program
    }

    private fun runOnGlThread(block: () -> Unit) {
        if (Thread.currentThread() === glThread) {
            block()
        } else {
            glHandler.post(block)
        }
    }

    private fun runOnGlThreadBlocking(block: () -> Unit) {
        if (Thread.currentThread() === glThread) {
            block()
            return
        }

        val latch = CountDownLatch(1)
        var failure: Throwable? = null
        glHandler.post {
            try {
                block()
            } catch (t: Throwable) {
                failure = t
            } finally {
                latch.countDown()
            }
        }
        latch.await()
        failure?.let { throw RuntimeException(it) }
    }

    companion object {
        private const val TAG = "OverlayCompositor"
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        private val FULLSCREEN_VERTICES = floatBuffer(
            floatArrayOf(
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                 1f,  1f
            )
        )

        // Exactly four vec2 coordinates. The previous implementation used vec4 data here,
        // which produced invalid OES sampling coordinates.
        private val CAMERA_TEXTURE_COORDS = floatBuffer(
            floatArrayOf(
                0f, 1f,
                1f, 1f,
                0f, 0f,
                1f, 0f
            )
        )

        private val OVERLAY_TEXTURE_COORDS = floatBuffer(
            floatArrayOf(
                0f, 1f,
                1f, 1f,
                0f, 0f,
                1f, 0f
            )
        )

        private fun floatBuffer(data: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(data.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(data)
                    position(0)
                }

        private const val CAMERA_VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTextureCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = (uTexMatrix * vec4(aTextureCoord, 0.0, 1.0)).xy;
            }
        """

        private const val CAMERA_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """

        private const val OVERLAY_VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = aTextureCoord;
            }
        """

        private const val OVERLAY_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """
    }
}
