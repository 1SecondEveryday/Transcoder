package com.otaliastudios.transcoder.internal.video

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.annotation.GuardedBy
import com.otaliastudios.opengl.draw.GlRect
import com.otaliastudios.opengl.extensions.clear
import com.otaliastudios.opengl.extensions.rotateZ
import com.otaliastudios.opengl.extensions.scale
import com.otaliastudios.opengl.extensions.translate
import com.otaliastudios.opengl.program.GlTextureProgram
import com.otaliastudios.opengl.texture.GlTexture
import com.otaliastudios.transcoder.internal.utils.Logger

/**
 * The purpose of this class is to create a [Surface] associated to a certain GL texture.
 *
 * The Surface is exposed through [.getSurface] and we expect someone to draw there.
 * Typically this will be a [android.media.MediaCodec] instance, using this surface as output.
 *
 * When [.drawFrame] is called, this class will wait for a new frame from MediaCodec,
 * and draw it on the current EGL surface. The class itself does no GL initialization, and will
 * draw on whatever surface is current.
 *
 * NOTE: By default, the Surface will be using a BufferQueue in asynchronous mode, so we
 * can potentially drop frames.
 */
internal class FrameDrawer() {

    private var surfaceTexture: SurfaceTexture?

    /**
     * Returns a Surface to draw onto.
     * @return the output surface
     */
    var surface: Surface?
        private set
    private var textureProgram: GlTextureProgram?
    private var textureRect: GlRect?
    private var scaleX = 1f
    private var scaleY = 1f
    private var translateY: Float = 0f
    private var rotation = 0
    private var flipY = false

    @GuardedBy("frameAvailableLock")
    private var frameAvailable = false
    private val frameAvailableLock = Object()

    /**
     * Creates an VideoDecoderOutput using the current EGL context (rather than establishing a
     * new one). Creates a Surface that can be passed to MediaCodec.configure().
     */
    init {
        val texture = GlTexture()
        textureProgram = GlTextureProgram()
        textureProgram!!.texture = texture
        textureRect = GlRect()

        // Even if we don't access the SurfaceTexture after the constructor returns, we
        // still need to keep a reference to it.  The Surface doesn't retain a reference
        // at the Java level, so if we don't either then the object can get GCed, which
        // causes the native finalizer to run.
        surfaceTexture = SurfaceTexture(texture.id)
        surfaceTexture!!.setOnFrameAvailableListener {
            LOG.v("New frame available")
            synchronized(frameAvailableLock) {
                if (frameAvailable) {
                    throw RuntimeException("mFrameAvailable already set, frame could be dropped")
                }
                frameAvailable = true
                frameAvailableLock.notifyAll()
            }
        }
        surface = Surface(surfaceTexture)
    }

    /**
     * Sets the frame scale along the two axes.
     * @param scaleX x scale
     * @param scaleY y scale
     */
    fun setScale(
        scaleX: Float,
        scaleY: Float,
        translateY: Float,
    ) {
        this.scaleX = scaleX
        this.scaleY = scaleY
        this.translateY = translateY
    }

    /**
     * Sets the desired frame rotation with respect
     * to its natural orientation.
     * @param rotation rotation
     */
    fun setRotation(rotation: Int) {
        this.rotation = rotation
    }

    fun setFlipY(flipY: Boolean) {
        this.flipY = flipY
    }

    /**
     * Discard all resources held by this class, notably the EGL context.
     */
    fun release() {
        textureProgram!!.release()
        surface!!.release()
        // this causes a bunch of warnings that appear harmless but might confuse someone:
        // W BufferQueue: [unnamed-3997-2] cancelBuffer: BufferQueue has been abandoned!
        // mSurfaceTexture.release();
        surface = null
        surfaceTexture = null
        textureRect = null
        textureProgram = null
    }

    /**
     * Waits for a new frame drawn into our surface (see [.getSurface]),
     * then draws it using OpenGL.
     */
    fun drawFrame() {
        awaitNewFrame()
        drawNewFrame()
    }

    /**
     * Latches the next buffer into the texture.  Must be called from the thread that created
     * the VideoDecoderOutput object, after the onFrameAvailable callback has signaled that new
     * data is available.
     */
    private fun awaitNewFrame() {
        synchronized(frameAvailableLock) {
            while (!frameAvailable) {
                try {
                    // Wait for onFrameAvailable() to signal us. Use a timeout to avoid
                    // stalling the test if it doesn't arrive.
                    frameAvailableLock.wait(NEW_IMAGE_TIMEOUT_MILLIS)
                    if (!frameAvailable) {
                        // TODO: if "spurious wakeup", continue while loop
                        // TODO: what does this mean? ^
                        throw RuntimeException("Surface frame wait timed out")
                    }
                } catch (ie: InterruptedException) {
                    throw RuntimeException(ie)
                }
            }
            frameAvailable = false
        }
        // Latch the data.
        surfaceTexture!!.updateTexImage()
    }

    /**
     * Draws the data from SurfaceTexture onto the current EGL surface.
     */
    private fun drawNewFrame() {
        surfaceTexture!!.getTransformMatrix(textureProgram!!.textureTransform)

        // We must invert the rotation.
        val invertedRotation = rotation.toFloat() * -1f

        textureRect!!.modelMatrix.apply {
            clear()

            // Move the texture (frame).
            translate(x = 0f, y = translateY)

            // Scale the texture (frame).
            scale(x = scaleX, y = scaleY)

            // Apply rotation and flip.
            translate(x = 0.5f, y = 0.5f)
            rotateZ(angle = invertedRotation)
            if (flipY) {
                scale(x = 1f, y = -1f, z = 1f)
            }

            // Move the texture back
            translate(x = -0.5f, y = -0.5f)
        }

        // Draw the texture (frame).
        textureProgram!!.draw(textureRect!!)
    }

    companion object {

        private val LOG = Logger("FrameDrawer")
        private const val NEW_IMAGE_TIMEOUT_MILLIS: Long = 10000
    }
}