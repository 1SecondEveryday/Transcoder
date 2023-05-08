package com.otaliastudios.transcoder

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.otaliastudios.transcoder.Transcoder.Companion.instance
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.resample.AudioResampler
import com.otaliastudios.transcoder.resample.DefaultAudioResampler
import com.otaliastudios.transcoder.sink.DataSink
import com.otaliastudios.transcoder.sink.DefaultDataSink
import com.otaliastudios.transcoder.source.AssetFileDescriptorDataSource
import com.otaliastudios.transcoder.source.DataSource
import com.otaliastudios.transcoder.source.FileDescriptorDataSource
import com.otaliastudios.transcoder.source.FilePathDataSource
import com.otaliastudios.transcoder.source.UriDataSource
import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategies
import com.otaliastudios.transcoder.strategy.TrackStrategy
import com.otaliastudios.transcoder.stretch.AudioStretcher
import com.otaliastudios.transcoder.stretch.DefaultAudioStretcher
import com.otaliastudios.transcoder.time.DefaultTimeInterpolator
import com.otaliastudios.transcoder.time.SpeedTimeInterpolator
import com.otaliastudios.transcoder.time.TimeInterpolator
import com.otaliastudios.transcoder.validator.DefaultValidator
import com.otaliastudios.transcoder.validator.Validator
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.FileDescriptor
import java.util.concurrent.Future
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Collects transcoding options consumed by [Transcoder].
 */
data class TranscoderOptions private constructor(
    val dataSink: DataSink,
    val videoDataSources: List<DataSource>,
    val audioDataSources: List<DataSource>,
    val audioTrackStrategy: TrackStrategy,
    val videoTrackStrategy: TrackStrategy,
    val validator: Validator,
    val videoRotation: Int = 0,
    val timeInterpolator: TimeInterpolator,
    val audioStretcher: AudioStretcher,
    val audioResampler: AudioResampler,
    val listener: TranscoderListener,
    val listenerHandler: Handler,
) {

    class Builder internal constructor(private val dataSink: DataSink) {

        internal constructor(outPath: String) : this(DefaultDataSink(outPath))

        @RequiresApi(api = Build.VERSION_CODES.O)
        internal constructor(fileDescriptor: FileDescriptor) : this(DefaultDataSink(fileDescriptor))

        private val audioDataSources: MutableList<DataSource> = mutableListOf()
        private val videoDataSources: MutableList<DataSource> = mutableListOf()
        private var listener: TranscoderListener? = null
        private var listenerHandler: Handler? = null
        private var audioTrackStrategy: TrackStrategy? = null
        private var videoTrackStrategy: TrackStrategy? = null
        private var validator: Validator? = null
        private var videoRotation = 0
        private var timeInterpolator: TimeInterpolator? = null
        private var audioStretcher: AudioStretcher? = null
        private var audioResampler: AudioResampler? = null


        fun addDataSource(dataSource: DataSource): Builder = apply {
            audioDataSources.add(dataSource)
            videoDataSources.add(dataSource)
        }

        fun addDataSource(type: TrackType, dataSource: DataSource): Builder = apply {
            if (type === TrackType.AUDIO) {
                audioDataSources.add(dataSource)
            } else if (type === TrackType.VIDEO) {
                videoDataSources.add(dataSource)
            }
        }

        fun addDataSource(fileDescriptor: FileDescriptor): Builder {
            return addDataSource(FileDescriptorDataSource(fileDescriptor))
        }

        fun addDataSource(type: TrackType, fileDescriptor: FileDescriptor): Builder {
            return addDataSource(type, FileDescriptorDataSource(fileDescriptor))
        }

        fun addDataSource(assetFileDescriptor: AssetFileDescriptor): Builder {
            return addDataSource(AssetFileDescriptorDataSource(assetFileDescriptor))
        }

        fun addDataSource(type: TrackType, assetFileDescriptor: AssetFileDescriptor): Builder {
            return addDataSource(type, AssetFileDescriptorDataSource(assetFileDescriptor))
        }

        fun addDataSource(inPath: String): Builder {
            return addDataSource(FilePathDataSource(inPath))
        }

        fun addDataSource(type: TrackType, inPath: String): Builder {
            return addDataSource(type, FilePathDataSource(inPath))
        }

        fun addDataSource(context: Context, uri: Uri): Builder {
            return addDataSource(UriDataSource(context, uri))
        }

        fun addDataSource(type: TrackType, context: Context, uri: Uri): Builder {
            return addDataSource(type, UriDataSource(context, uri))
        }

        /**
         * Sets the audio output strategy. If absent, this defaults to
         * [DefaultAudioStrategy].
         *
         * @param trackStrategy the desired strategy
         * @return this for chaining
         */
        fun setAudioTrackStrategy(trackStrategy: TrackStrategy?): Builder = apply {
            audioTrackStrategy = trackStrategy
        }

        /**
         * Sets the video output strategy. If absent, this defaults to the 16:9
         * strategy returned by [DefaultVideoStrategies.for720x1280].
         *
         * @param trackStrategy the desired strategy
         * @return this for chaining
         */
        fun setVideoTrackStrategy(trackStrategy: TrackStrategy?): Builder = apply {
            videoTrackStrategy = trackStrategy
        }

        fun setListener(listener: TranscoderListener): Builder = apply {
            this.listener = listener
        }

        /**
         * Sets an handler for [TranscoderListener] callbacks.
         * If null, this will default to the thread that starts the transcoding, if it
         * has a looper, or the UI thread otherwise.
         *
         * @param listenerHandler the thread to receive callbacks
         * @return this for chaining
         */
        fun setListenerHandler(listenerHandler: Handler?): Builder = apply {
            this.listenerHandler = listenerHandler
        }

        /**
         * Sets a validator to understand whether the transcoding process should
         * stop before being started, based on the tracks status. Will default to
         * [com.otaliastudios.transcoder.validator.DefaultValidator].
         *
         * @param validator the validator
         * @return this for chaining
         */
        fun setValidator(validator: Validator?): Builder = apply {
            this.validator = validator
        }

        /**
         * The clockwise rotation to be applied to the input video frames.
         * Defaults to 0, which leaves the input rotation unchanged.
         *
         * @param rotation either 0, 90, 180 or 270
         * @return this for chaining
         */
        fun setVideoRotation(rotation: Int): Builder = apply {
            videoRotation = rotation
        }

        /**
         * Sets a [TimeInterpolator] to change the frames timestamps - either video or
         * audio or both - before they are written into the output file.
         * Defaults to [com.otaliastudios.transcoder.time.DefaultTimeInterpolator].
         *
         * @param timeInterpolator a time interpolator
         * @return this for chaining
         */
        fun setTimeInterpolator(timeInterpolator: TimeInterpolator): Builder = apply {
            this.timeInterpolator = timeInterpolator
        }

        /**
         * Shorthand for calling [.setTimeInterpolator] and passing a [SpeedTimeInterpolator].
         * This interpolator can modify the video speed by the given factor.
         *
         * @param speedFactor a factor, greater than 0
         * @return this for chaining
         */
        fun setSpeed(speedFactor: Float): Builder {
            return setTimeInterpolator(SpeedTimeInterpolator(speedFactor))
        }

        /**
         * Sets an [AudioStretcher] to perform stretching of audio samples
         * as a consequence of speed and time interpolator changes.
         * Defaults to [DefaultAudioStretcher].
         *
         * @param audioStretcher an audio stretcher
         * @return this for chaining
         */
        fun setAudioStretcher(audioStretcher: AudioStretcher): Builder = apply {
            this.audioStretcher = audioStretcher
        }

        /**
         * Sets an [AudioResampler] to change the sample rate of audio
         * frames when sample rate conversion is needed. Upsampling is discouraged.
         * Defaults to [DefaultAudioResampler].
         *
         * @param audioResampler an audio resampler
         * @return this for chaining
         */
        fun setAudioResampler(audioResampler: AudioResampler): Builder = apply {
            this.audioResampler = audioResampler
        }

        fun build(): TranscoderOptions {
            val listener = this.listener
            checkNotNull(listener) { "listener can't be null" }
            check(!(audioDataSources.isEmpty() && videoDataSources.isEmpty())) {
                "we need at least one data source"
            }
            require(videoRotations.contains(videoRotation)) {
                "Accepted values for rotation are ${videoRotations.joinToString(", ")}"
            }

            val listenerHandler = this.listenerHandler ?: Handler(
                Looper.myLooper() ?: Looper.getMainLooper()
            )

            return TranscoderOptions(
                dataSink = dataSink,
                videoDataSources = videoDataSources.toList(),
                audioDataSources = audioDataSources.toList(),
                audioTrackStrategy = audioTrackStrategy ?: DefaultAudioStrategy.builder().build(),
                videoTrackStrategy = videoTrackStrategy ?: DefaultVideoStrategies.for720x1280(),
                validator = validator ?: DefaultValidator(),
                videoRotation = 0,
                timeInterpolator = timeInterpolator ?: DefaultTimeInterpolator(),
                audioStretcher = audioStretcher ?: DefaultAudioStretcher(),
                audioResampler = audioResampler ?: DefaultAudioResampler(),
                listener = listener,
                listenerHandler = listenerHandler,
            )
        }

        fun transcode(): Future<Void> {
            return instance.transcode(build())
        }

        suspend fun transcode(
            onCancelled: () -> Unit = {},
            onProgress: (Double) -> Unit,
        ): Int {
            return suspendCancellableCoroutine { continuation ->
                val listener = object : TranscoderListener {
                    override fun onTranscodeProgress(progress: Double) {
                        onProgress(progress.coerceIn(0.0, 1.0))
                    }

                    override fun onTranscodeCompleted(successCode: Int) {
                        continuation.resume(successCode)
                    }

                    override fun onTranscodeCanceled() {
                        onCancelled()
                    }

                    override fun onTranscodeFailed(exception: Throwable) {
                        continuation.resumeWithException(exception)
                    }
                }

                this.listener = listener
                val future = transcode()
                continuation.invokeOnCancellation { future.cancel(true) }
            }

        }

        companion object {

            private val videoRotations = listOf(0, 90, 180, 270)
        }
    }
}