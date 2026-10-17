package com.the412banner.bfe

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

/**
 * App-wide setup: Coil's shared image loader also decodes video frames (VideoFrameDecoder, via
 * Android's MediaMetadataRetriever), so file lists show real video thumbnails, and every thumbnail
 * fades in instead of popping.
 */
class BfeApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
