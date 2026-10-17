package com.the412banner.bfe.util

import android.content.Context
import android.os.Build
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.Date

/**
 * How BFE writes dates: the locale's medium date plus the time in the phone's own 12/24-hour setting,
 * e.g. "Oct 6, 2026 · 3:42 PM" or "6 Oct 2026 · 15:42".
 */
object DateText {
    private const val SEP = " · "

    fun date(context: Context, millis: Long): String =
        android.text.format.DateFormat.getMediumDateFormat(context).format(Date(millis))

    fun time(context: Context, millis: Long): String =
        android.text.format.DateFormat.getTimeFormat(context).format(Date(millis))

    fun dateTime(context: Context, millis: Long): String = date(context, millis) + SEP + time(context, millis)

    /**
     * When [file] was created, if the filesystem records it separately from the last change. Android's
     * shared storage usually doesn't (creation time then just repeats the modification time), so this
     * returns null unless it's a genuinely different, earlier moment.
     */
    fun createdMillis(file: File): Long? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return runCatching {
            val created = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java).creationTime().toMillis()
            created.takeIf { it > 0 && it < file.lastModified() - 1_000 }
        }.getOrNull()
    }
}
