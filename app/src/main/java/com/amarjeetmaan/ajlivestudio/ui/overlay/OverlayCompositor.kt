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
import io.github.thibaultbee.streampack.core.elements.utils.time.Timebase
import io.github.thibaultbee.streampack.core.pipelines.IVideoDispatcherProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch

/**
 * GPU compositor used by StreamPack between the camera source and its outputs.
 *
 * Camera frames are received through an OES SurfaceTexture. The frame is drawn
 * directly into every StreamPack output surface and the SurfaceTexture timestamp
 * is preserved for the encoder. No MediaProjection or screen capture is used.
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
    private var eglPbuffer: EGLSurface = EGL14.EGL_NO_SURFACE

    private var cameraTextureId = 0
    private var overlayTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null

    private var cameraProgram = 0
    private var overlayProgram = 0

    private var framePending = false
    private var released = false

    private var pendingOverlayBitmap: Bitmap? = null
    private var overlayVersion = 0L
    private var uploadedOverlayVersion = -1L
    private var hasOverlay = false

    private val texMatrix = FloatArray(16)

    private data class OutputEntry(
        val output: ISurfaceOutput,
        val eglSurface: EGLSurface
    )

    private val outputs = mutableListOf<OutputEntry>()

    private val vertexBuffer = floatBuffer(FULLSCREEN_VERTS)
    private val cameraTexCoordBuffer = floatBuffer(CAMERA_TEXCOORDS)
    private val overlayTexCoordBuffer = floatBuffer(OVERLAY_TEXCOORDS)

    init {
        runOnGlThreadBlocking { initEgl() }
    }

    fun setOverlayBitmap(bitmap: Bitmap?) {
        if (released) return

        val copy = bitmap?.let {
            if (it.isRecycled) null else it.copy(Bitmap.Config.ARGB_8888, false)
        }

        runOnGlThread {
            if (released) {
                copy?.recycle()
                return@runOnGlThread
            }

            pendingOverlayBitmap?.recycle()
            pendingOverlayBitmap = copy
            overlayVersion++

            if (framePending && outputs.isNotEmpty()) {
                renderPendingFrame()
            }
        }
    }

    override fun createInputSurface(surfaceSize: Size, timebase: Timebase): Surface {
        check(!released) { "OverlayCompositor is released" }

        var result: Surface? = null
        runOnGlThreadBlocking {
            checkEglCurrent()

            if (cameraTextureId == 0) {
                cameraTextureId = createExternalTexture()
            }

            surfaceTexture?.setOnFrameAvailableListener(null)
            surfaceTexture?.release()
            inputSurface?.release()

            val st = SurfaceTexture(cameraTextureId)
            st.setDefaultBufferSize(surfaceSize.width, surfaceSize.height)
            st.setOnFrameAvailableListener(
                {
                    if (!released) {
                        framePending = true
                        renderPendingFrame()
                    }
                },
                glHandler
            )

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

            checkEglCurrent()

            val target = surfaceOutput.targetSurface
            val eglSurface = createWindowSurface(target)
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                Log.e(TAG, "eglCreateWindowSurface failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
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

    override fun setTimebase(surface: Surface, timebase: Timebase) {
        // The input SurfaceTexture timestamp is already the source frame timestamp.
        // StreamPack owns the selected timebase; we intentionally do not generate a
        // second timestamp here.
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

            pendingOverlayBitmap?.recycle()
            pendingOverlayBitmap = null

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

            releaseEgl()
        }

        glThread.quitSafely()
    }

    private fun renderPendingFrame() {
        if (released || !framePending || outputs.isEmpty()) return

        val st = surfaceTexture ?: return

        checkEglCurrent()

        // Do not consume the frame until an output exists. This prevents the camera
        // buffer queue from being drained while StreamPack is still attaching its
        // preview/encoder surface.
        st.updateTexImage()
        st.getTransformMatrix(texMatrix)
        val timestampNs = st.timestamp
        framePending = false

        if (timestampNs <= 0L) {
            Log.w(TAG, "SurfaceTexture returned invalid timestamp; dropping frame")
            return
        }

        maybeUploadOverlay()

        val snapshot = outputs.toList()
        for (entry in snapshot) {
            if (EGL14.eglMakeCurrent(
                    eglDisplay,
                    entry.eglSurface,
                    entry.eglSurface,
                    eglContext
                ).not()) {
                Log.e(TAG, "eglMakeCurrent(output) failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
                continue
            }

            val size = entry.output.targetResolution
            GLES20.glViewport(0, 0, size.width, size.height)
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            val outputMatrix = FloatArray(16)
            entry.output.updateTransformMatrix(outputMatrix, texMatrix)

            drawCamera(outputMatrix)
            if (hasOverlay) {
                drawOverlay()
            }

            EGLExt.eglPresentationTimeANDROID(
                eglDisplay,
                entry.eglSurface,
                timestampNs
            )

            if (!EGL14.eglSwapBuffers(eglDisplay, entry.eglSurface)) {
                Log.e(TAG, "eglSwapBuffers failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
            }
        }

        // Keep the EGL context current on the GL thread for the next callback.
        if (outputs.isNotEmpty()) {
            val last = outputs.last().eglSurface
            EGL14.eglMakeCurrent(eglDisplay, last, last, eglContext)
        }
    }

    private fun maybeUploadOverlay() {
        if (overlayVersion == uploadedOverlayVersion) return

        val bitmap = pendingOverlayBitmap
        if (bitmap == null || bitmap.isRecycled) {
            hasOverlay = false
            uploadedOverlayVersion = overlayVersion
            return
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        hasOverlay = true
        uploadedOverlayVersion = overlayVersion
    }

    private fun drawCamera(matrix: FloatArray) {
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(cameraProgram)

        val positionLoc = GLES20.glGetAttribLocation(cameraProgram, "aPosition")
        val texCoordLoc = GLES20.glGetAttribLocation(cameraProgram, "aTextureCoord")
        val matrixLoc = GLES20.glGetUniformLocation(cameraProgram, "uTexMatrix")
        val samplerLoc = GLES20.glGetUniformLocation(cameraProgram, "sTexture")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glUniform1i(samplerLoc, 0)
        GLES20.glUniformMatrix4fv(matrixLoc, 1, false, matrix, 0)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(
            positionLoc,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            vertexBuffer
        )
        GLES20.glEnableVertexAttribArray(positionLoc)

        cameraTexCoordBuffer.position(0)
        GLES20.glVertexAttribPointer(
            texCoordLoc,
            4,
            GLES20.GL_FLOAT,
            false,
            0,
            cameraTexCoordBuffer
        )
        GLES20.glEnableVertexAttribArray(texCoordLoc)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionLoc)
        GLES20.glDisableVertexAttribArray(texCoordLoc)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    private fun drawOverlay() {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(overlayProgram)

        val positionLoc = GLES20.glGetAttribLocation(overlayProgram, "aPosition")
        val texCoordLoc = GLES20.glGetAttribLocation(overlayProgram, "aTextureCoord")
        val samplerLoc = GLES20.glGetUniformLocation(overlayProgram, "sTexture")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
        GLES20.glUniform1i(samplerLoc, 0)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(
            positionLoc,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            vertexBuffer
        )
        GLES20.glEnableVertexAttribArray(positionLoc)

        overlayTexCoordBuffer.position(0)
        GLES20.glVertexAttribPointer(
            texCoordLoc,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            overlayTexCoordBuffer
        )
        GLES20.glEnableVertexAttribArray(texCoordLoc)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionLoc)
        GLES20.glDisableVertexAttribArray(texCoordLoc)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glDisable(GLES20.GL_BLEND)
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
        check(EGL14.eglChooseConfig(
            eglDisplay,
            configAttributes,
            0,
            configs,
            0,
            1,
            count,
            0
        ) && count[0] > 0) {
            "Unable to choose EGL config"
        }

        eglConfig = configs[0] ?: error("EGL config is null")

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
        eglPbuffer = EGL14.eglCreatePbufferSurface(
            eglDisplay,
            eglConfig,
            pbufferAttributes,
            0
        )
        check(eglPbuffer != EGL14.EGL_NO_SURFACE) { "Unable to create EGL pbuffer" }

        check(EGL14.eglMakeCurrent(eglDisplay, eglPbuffer, eglPbuffer, eglContext)) {
            "Unable to make EGL context current"
        }

        cameraProgram = buildProgram(CAMERA_VERTEX_SHADER, CAMERA_FRAGMENT_SHADER)
        overlayProgram = buildProgram(OVERLAY_VERTEX_SHADER, OVERLAY_FRAGMENT_SHADER)
        overlayTextureId = create2DTexture()

        EGL14.eglMakeCurrent(
            eglDisplay,
            eglPbuffer,
            eglPbuffer,
            eglContext
        )
    }

    private fun checkEglCurrent() {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglContext == EGL14.EGL_NO_CONTEXT) {
            error("EGL has already been released")
        }

        if (EGL14.eglGetCurrentContext() != eglContext ||
            EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW) == EGL14.EGL_NO_SURFACE
        ) {
            check(eglPbuffer != EGL14.EGL_NO_SURFACE) { "EGL pbuffer is not available" }
            check(EGL14.eglMakeCurrent(eglDisplay, eglPbuffer, eglPbuffer, eglContext)) {
                "Unable to restore EGL context"
            }
        }
    }

    private fun createWindowSurface(surface: Surface): EGLSurface {
        val config = eglConfig ?: error("EGL config is not initialized")
        return EGL14.eglCreateWindowSurface(
            eglDisplay,
            config,
            surface,
            intArrayOf(EGL14.EGL_NONE),
            0
        )
    }

    private fun destroyWindowSurface(surface: EGLSurface) {
        if (surface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(eglDisplay, surface)
        }
    }

    private fun createExternalTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        return textures[0]
    }

    private fun create2DTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return textures[0]
    }

    private fun buildProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        if (linked[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            error("GL program link failed: $log")
        }
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("GL shader compile failed: $log")
        }
        return shader
    }

    private fun releaseEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                eglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            if (eglPbuffer != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglPbuffer)
                eglPbuffer = EGL14.EGL_NO_SURFACE
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
            }
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglConfig = null
    }

    private fun runOnGlThread(block: () -> Unit) {
        if (Thread.currentThread() == glThread) {
            block()
        } else {
            glHandler.post {
                try {
                    block()
                } catch (t: Throwable) {
                    Log.e(TAG, "GL operation failed", t)
                }
            }
        }
    }

    private fun runOnGlThreadBlocking(block: () -> Unit) {
        if (Thread.currentThread() == glThread) {
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

    private fun floatBuffer(values: FloatArray): FloatBuffer {
        return ByteBuffer
            .allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }
    }

    companion object {
        private const val TAG = "AJLiveOverlay"
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        private val FULLSCREEN_VERTS = floatArrayOf(
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f
        )

        // vec4 coordinates required by the SurfaceTexture matrix.
        private val CAMERA_TEXCOORDS = floatArrayOf(
            0f, 0f, 0f, 1f,
            1f, 0f, 0f, 1f,
            0f, 1f, 0f, 1f,
            1f, 1f, 0f, 1f
        )

        private val OVERLAY_TEXCOORDS = floatArrayOf(
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f
        )

        private const val CAMERA_VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = (uTexMatrix * aTextureCoord).xy;
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
