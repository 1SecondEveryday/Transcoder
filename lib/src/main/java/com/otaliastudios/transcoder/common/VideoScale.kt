package com.otaliastudios.transcoder.common

import android.media.MediaFormat
import com.otaliastudios.transcoder.internal.media.MediaFormatConstants

sealed class VideoScale {

    object CenterCrop : VideoScale()
    object Fit : VideoScale()

    override fun toString(): String = when (this) {
        CenterCrop -> CENTER_CROP
        Fit -> FIT
    }

    companion object {

        private const val CENTER_CROP = "center_crop"
        private const val FIT = "fit"

        @JvmField
        val Default = CenterCrop

        fun from(value: String?): VideoScale? = when (value) {
            CENTER_CROP -> CenterCrop
            FIT -> Fit
            else -> null
        }
    }
}

fun MediaFormat.videoScale(): VideoScale {
    val value = getString(MediaFormatConstants.KEY_VIDEO_SCALE_TYPE)
    return VideoScale.from(value) ?: VideoScale.Default
}