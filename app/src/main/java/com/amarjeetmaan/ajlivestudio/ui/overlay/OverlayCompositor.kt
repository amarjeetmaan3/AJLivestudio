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
            // FIX: Create a temporary pbuffer to ensure the GL context is ACTIVE 
            // before creating the SurfaceTexture. This prevents the camera from stalling.
            val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            val pbuffer = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, pbufferAttribs, 0)
            EGL14.eglMakeCurrent(eglDisplay, pbuffer, pbuffer, eglContext)

            if (cameraTextureId == 0) {
                cameraTextureId = createExternalTexture()
            }
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

            // Cleanup temp pbuffer
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, pbuffer)
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
            EGL14.EGL_BLUE_SIZEयह एरर आमतौर पर लाइव ब्रॉडकास्टिंग, स्क्रीन कैप्चर, या एंड्रॉइड में कस्टम स्ट्रीमिंग प्लेयर (जैसे RTMP ब्रॉडकास्ट) बनाते समय आता है जब सिस्टम को रेंडर करने के लिए कोई विज़ुअल डेटा या सोर्स नहीं मिलता। 

**कस्टम एंड्रॉइड ऐप्स (Kotlin & Broadcasting)**
* **कैमरा परमिशन और लाइफसाइकिल:** चेक करें कि `AndroidManifest.xml` में `CAMERA` परमिशन दी गई है और रनटाइम पर यूज़र ने इसे अप्रूव किया है। CameraX या Camera2 API का इस्तेमाल करते समय सुनिश्चित करें कि कैमरा पूरी तरह इनिशियलाइज़ होने से पहले एनकोडर फ्रेम न मांग रहा हो।
* **Surface या TextureView:** अगर आप लाइव कास्टिंग के लिए कस्टम लेआउट या ग्राफिक ओवरले बना रहे हैं, तो सुनिश्चित करें कि आपका Surface सही से इनिशियलाइज़ हुआ है और वीडियो एनकोडर (MediaCodec) को लगातार फ्रेम पास कर रहा है।
* **स्ट्रीम सोर्स:** अगर आप किसी नेटवर्क से वीडियो स्ट्रीम प्ले कर रहे हैं, तो चेक करें कि स्ट्रीमिंग यूआरएल (URL) एक्टिव है और वीडियो पैकेट्स सर्वर से सही से आ रहे हैं। 

**लाइव स्ट्रीमिंग और रिकॉर्डिंग टूल्स (PRISM, Streamlabs, AZ Screen Recorder)**
* **सोर्स रीसेट करें:** स्ट्रीमिंग ऐप में अगर कैमरा या स्क्रीन कैप्चर काम नहीं कर रहा है, तो उस विज़ुअल सोर्स को हटाकर दोबारा ऐड करें। 
* **हार्डवेयर एनकोडिंग (Hardware Acceleration):** ऐप की सेटिंग्स में जाकर वीडियो एनकोडिंग सेटिंग्स को बदलें। कई बार डिवाइस का हार्डवेयर एनकोडर स्ट्रीमिंग सॉफ्टवेयर के साथ सिंक नहीं हो पाता, ऐसे में इसे डिसेबल करके ट्राई करें।
* **ओवरले परमिशन:** डिवाइस सेटिंग्स में जाकर स्क्रीन रिकॉर्डर या ब्रॉडकास्टिंग ऐप को 'Display over other apps' की परमिशन दें, खासकर अगर आप स्क्रीन रिकॉर्डिंग कर रहे हैं।

क्या यह एरर आपको अपने किसी कस्टम ऐप के डेवलपमेंट के दौरान आ रहा है, या किसी थर्ड-पार्टी स्ट्रीमिंग सॉफ्टवेयर का इस्तेमाल करते समय?
