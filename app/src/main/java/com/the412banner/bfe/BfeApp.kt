package com.the412banner.bfe

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.the412banner.bfe.appicon.AppIcons
import com.the412banner.bfe.security.CredentialMigration
import com.the412banner.bfe.util.ScreenRotation

/**
 * App-wide setup: Coil's shared image loader also decodes video frames (VideoFrameDecoder, via
 * Android's MediaMetadataRetriever), so file lists show real video thumbnails, and every thumbnail
 * fades in instead of popping. It also lets [AppIcons] finish an app-icon switch once BFE is in the
 * background, moves any secret an older BFE kept in plain text into the credential vault, and
 * applies the Screen rotation setting to every screen.
 */
class BfeApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AppIcons.install(this)
        CredentialMigration.start(this)
        ScreenRotation.install(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
