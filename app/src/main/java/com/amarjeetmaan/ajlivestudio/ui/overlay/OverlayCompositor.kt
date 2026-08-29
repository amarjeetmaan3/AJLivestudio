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

class OverlayCompositor : ISurfaceProcessorInternal {

    class Factory : ISurfaceProcessorInternal.Factory {
        override fun create(
            dynamicRangeProfile: DynamicRangeProfile,
            dispatcherProvider: IVideoDispatcherProvider
        ): ISurfaceProcessorInternal = OverlayCompositor().also { instance = it }

        companion object {
            @Volatile var instance: OverlayCompositor? = null
        }
    }

    override var isMuted: Boolean = false

    private val glThread = HandlerThread("OverlayCompositorGL").apply { start() }
    private val glHandler = Handler(glThread.looper)

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null

    private var cameraTextureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null
    private val texMatrix = FloatArray(16)
    private var frameAvailable = false
    private val frameLock = Object()

    private var overlayTextureId = 0
    @Volatile private var pendingOverlayBitmap: Bitmap? = null
    @Volatile private var overlayBitmapVersion = 0
    private var uploadedOverlayVersion = -1
    private var hasOverlay = false

    private var cameraProgram = 0
    private var overlayProgram = 0

    private data class OutputEntry(val output: ISurfaceOutput, val eglSurface: EGLSurface)
    private val outputs = mutableListOf<OutputEntry>()
    private val outputsLock = Object()

    private var timebase: Timebase = Timebase.UPTIME

    init {
        runOnGlThreadBlocking { initEgl() }
    }

    fun setOverlayBitmap(bitmap: Bitmap?) {
        pendingOverlayBitmap = bitmap
        overlayBitmapVersion++
    }

    override fun createInputSurface(surfaceSize: Size, timebase: Timebase): Surface {
        this.timebase = timebase
        var result: Surface? = null
        runOnGlThreadBlocking {
            if (cameraTextureId == 0) cameraTextureId = createExternalTexture()
            val st = SurfaceTexture(cameraTextureId)
            st.setDefaultBufferSize(surfaceSize.width, surfaceSize.height)
            st.setOnFrameAvailableListener({
                synchronized(frameLock) { frameAvailable = true }
                drawFrame()
            }, glHandler)
            surfaceTexture?.release()
            surfaceTexture = st
            val surface = Surface(st)
            inputSurface?.release()
            inputSurface = surface
            result = surface
        }
        return result!!
    }

    override fun removeInputSurface(surface: Surface) {
        runOnGlThread {
            if (inputSurface == surface) {
                surfaceTexture?.setOnFrameAvailableListener(null)
                surfaceTexture?.release()
                surfaceTexture = null
                inputSurface?.release()
                inputSurface = null
            }
        }
    }

    override fun addOutputSurface(surfaceOutput: ISurfaceOutput) {
        runOnGlThread {
            val eglSurface = createWindowSurface(surfaceOutput.targetSurface)
            synchronized(outputsLock) { outputs.add(OutputEntry(surfaceOutput, eglSurface)) }
        }
    }

    override fun removeOutputSurface(surface: Surface) {
        runOnGlThread {
            synchronized(outputsLock) {
                val it = outputs.iterator()
                while (it.hasNext()) {
                    val entry = it.next()
                    if (entry.output.targetSurface == surface) {
                        destroyWindowSurface(entry.eglSurface)
                        it.remove()
                    }
                }
            }
        }
    }

    override fun removeOutputSurface(surfaceOutput: ISurfaceOutput) {
        runOnGlThread {
            synchronized(outputsLock) {
                val it = outputs.iterator()
                while (it.hasNext()) {
                    val entry = it.next()
                    if (entry.output === surfaceOutput) {
                        destroyWindowSurface(entry.eglSurface)
                        it.remove()
                    }
                }
            }
        }
    }

    override fun removeAllOutputSurfaces() {
        runOnGlThread {
            synchronized(outputsLock) {
                outputs.forEach { destroyWindowSurface(it.eglSurface) }
                outputs.clear()
            }
        }
    }

    override fun setTimebase(surface: Surface, timebase: Timebase) {
        this.timebase = timebase
    }

    override fun release() {
        runOnGlThreadBlocking {
            surfaceTexture?.setOnFrameAvailableListener(null)
            surfaceTexture?.release()
            surfaceTexture = null
            inputSurface?.release()
            inputSurface = null
            synchronized(outputsLock) {
                outputs.forEach { destroyWindowSurface(it.eglSurface) }
                outputs.clear()
            }
            releaseEgl()
        }
        glThread.quitSafely()
        if (Factory.instance === this) Factory.instance = null
    }

    private fun runOnGlThread(block: () -> Unit) {
        if (Thread.currentThread() == glThread) block() else glHandler.post(block)
    }

    private fun runOnGlThreadBlocking(block: () -> Unit) {
        if (Thread.currentThread() == glThread) {
            block()
            return
        }
        val latch = CountDownLatch(1)
        glHandler.post { try { block() } finally { latch.countDown() } }
        latch.await()
    }

    private fun initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL14 display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "Unable to initialize EGL14" }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            0x3142, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
        eglConfig = configs[0] ?: error("Unable to find suitable EGLConfig")

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "Unable to create EGL context" }

        val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        val pbuffer = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs, 0)
        EGL14.eglMakeCurrent(eglDisplay, pbuffer, pbuffer, eglContext)

        cameraProgram = buildProgram(CAMERA_VERTEX_SHADER, CAMERA_FRAGMENT_SHADER)
        overlayProgram = buildProgram(OVERLAY_VERTEX_SHADER, OVERLAY_FRAGMENT_SHADER)
        overlayTextureId = create2DTexture()

        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(eglDisplay, pbuffer)
    }

    private fun releaseEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
    }

    private fun createWindowSurface(surface: Surface): EGLSurface {
        val attribs = intArrayOf(EGL14.EGL_NONE)
        return EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, attribs, 0)
    }

    private fun destroyWindowSurface(eglSurface: EGLSurface) {
        if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
    }

    private fun createExternalTexture(): Int {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return tex[0]
    }

    private fun create2DTexture(): Int {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return tex[0]
    }

    private fun maybeUploadOverlay() {
        if (overlayBitmapVersion == uploadedOverlayVersion) return
        uploadedOverlayVersion = overlayBitmapVersion
        val bmp = pendingOverlayBitmap
        if (bmp != null && !bmp.isRecycled) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            hasOverlay = true
        } else {
            hasOverlay = false
        }
    }

    private fun drawFrame() {
        runOnGlThread {
            val st = surfaceTexture ?: return@runOnGlThread
            synchronized(frameLock) {
                if (!frameAvailable) return@runOnGlThread
                frameAvailable = false
            }

            val entries = synchronized(outputsLock) { outputs.toList() }
            if (entries.isEmpty()) {
                val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
                val pbuffer = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs, 0)
                EGL14.eglMakeCurrent(eglDisplay, pbuffer, pbuffer, eglContext)
                st.updateTexImage()
                st.getTransformMatrix(texMatrix)
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(eglDisplay, pbuffer)
                return@runOnGlThread
            }

            EGL14.eglMakeCurrent(eglDisplay, entries[0].eglSurface, entries[0].eglSurface, eglContext)
            st.updateTexImage()
            st.getTransformMatrix(texMatrix)
            maybeUploadOverlay()
            val timestampNs = st.timestamp

            for (entry in entries) {
                EGL14.eglMakeCurrent(eglDisplay, entry.eglSurface, entry.eglSurface, eglContext)
                val size = entry.output.targetResolution
                GLES20.glViewport(0, 0, size.width, size.height)
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                val outMatrix = FloatArray(16)
                entry.output.updateTransformMatrix(outMatrix, texMatrix)
                drawCamera(outMatrix)
                if (hasOverlay) drawOverlay()

                EGLExt.eglPresentationTimeANDROID(eglDisplay, entry.eglSurface, timestampNs)
                EGL14.eglSwapBuffers(eglDisplay, entry.eglSurface)
            }
        }
    }

    private fun drawCamera(matrix: FloatArray) {
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(cameraProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)

        val posLoc = GLES20.glGetAttribLocation(cameraProgram, "aPosition")
        val texLoc = GLES20.glGetAttribLocation(cameraProgram, "aTextureCoord")
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(cameraProgram, "uTexMatrix"), 1, false, matrix, 0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(cameraProgram, "sTexture"), 0)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(posLoc)
        cameraTexCoordBuffer.position(0)
        GLES20.glVertexAttribPointer(texLoc, 4, GLES20.GL_FLOAT, false, 0, cameraTexCoordBuffer)
        GLES20.glEnableVertexAttribArray(texLoc)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(texLoc)
    }

    private fun drawOverlay() {
        GLES20.glEnable(GLES20.GL_BLEND)
        // Fixed blend function to avoid black fringing around text and web components
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(overlayProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)

        val posLoc = GLES20.glGetAttribLocation(overlayProgram, "aPosition")
        val texLoc = GLES20.glGetAttribLocation(overlayProgram, "aTextureCoord")
        GLES20.glUniform1i(GLES20.glGetUniformLocation(overlayProgram, "sTexture"), 0)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(posLoc)
        overlayTexCoordBuffer.position(0)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, overlayTexCoordBuffer)
        GLES20.glEnableVertexAttribArray(texLoc)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(texLoc)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun loadShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
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

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            error("Program link failed: $log")
        }
        return program
    }

    companion object {
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

        private val FULLSCREEN_VERTS = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        private val CAMERA_TEXCOORDS = floatArrayOf(0f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 1f)
        private val OVERLAY_TEXCOORDS = floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f)

        private fun floatBuffer(data: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(data); position(0)
            }

        private val vertexBuffer = floatBuffer(FULLSCREEN_VERTS)
        private val cameraTexCoordBuffer = floatBuffer(CAMERA_TEXCOORDS)
        private val overlayTexCoordBuffer = floatBuffer(OVERLAY_TEXCOORDS)
    }
}
