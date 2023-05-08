/*
 * Copyright (C) 2014 Yuya Tanaka
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.otaliastudios.transcoder

import android.os.Build
import androidx.annotation.RequiresApi
import com.otaliastudios.transcoder.internal.transcode.TranscodeEngine
import com.otaliastudios.transcoder.internal.utils.ThreadPool.executor
import com.otaliastudios.transcoder.sink.DataSink
import java.io.FileDescriptor
import java.util.concurrent.Future

class Transcoder private constructor() {

    /**
     * Transcodes video file asynchronously.
     *
     * @param options The transcoder options.
     * @return a Future that completes when transcoding is completed
     */
    fun transcode(options: TranscoderOptions): Future<Void> {
        return executor.submit<Void> {
            TranscodeEngine.transcode(options)
            null
        }
    }

    companion object {

        /**
         * Constant for [TranscoderListener.onTranscodeCompleted].
         * Transcoding was executed successfully.
         */
        const val SUCCESS_TRANSCODED = 0

        /**
         * Constant for [TranscoderListener.onTranscodeCompleted]:
         * transcoding was not executed because it was considered
         * not necessary by the [Validator].
         */
        const val SUCCESS_NOT_NEEDED = 1
        @JvmStatic val instance: Transcoder
            get() = Transcoder()

        /**
         * Starts building transcoder options.
         * Requires a non null absolute path to the output file.
         *
         * @param outPath path to output file
         * @return an options builder
         */
        @JvmStatic
        fun into(outPath: String): TranscoderOptions.Builder {
            return TranscoderOptions.Builder(outPath)
        }

        /**
         * Starts building transcoder options.
         * Requires a non null fileDescriptor to the output file or stream
         *
         * @param fileDescriptor descriptor of the output file or stream
         * @return an options builder
         */
        @RequiresApi(api = Build.VERSION_CODES.O)
        fun into(fileDescriptor: FileDescriptor): TranscoderOptions.Builder {
            return TranscoderOptions.Builder(fileDescriptor)
        }

        /**
         * Starts building transcoder options.
         * Requires a non null sink.
         *
         * @param dataSink the output sink
         * @return an options builder
         */
        fun into(dataSink: DataSink): TranscoderOptions.Builder {
            return TranscoderOptions.Builder(dataSink)
        }
    }
}