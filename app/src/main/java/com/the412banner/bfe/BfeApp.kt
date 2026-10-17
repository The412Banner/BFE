package com.the412banner.bfe

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.the412banner.bfe.appicon.AppIcons

/**
 * App-wide setup: Coil's shared image loader also decodes video frames (VideoFrameDecoder, via
 * Android's MediaMetadataRetriever), so file lists show real video thumbnails, and every thumbnail
 * fades in instead of popping. It also lets [AppIcons] finish an app-icon switch once BFE is in the
 * background.
 */
class BfeApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AppIcons.install(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
