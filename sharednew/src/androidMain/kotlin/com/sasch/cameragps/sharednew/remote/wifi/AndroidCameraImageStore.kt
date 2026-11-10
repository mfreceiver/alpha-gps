package com.sasch.cameragps.sharednew.remote.wifi

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class AndroidCameraImageStore(private val context: Context) : CameraImageStore {
    override val canConvertHeif: Boolean get() = true

    override suspend fun createJpeg(info: SonyImageInfo): CameraImageDestination {
        check(canConvertHeif)
        require(info.mimeType == "image/heif")
        return AndroidCameraJpegDestination.create(context.cacheDir, info, this, ::convertAndroidHeifToJpeg)
    }

    // Creation returns ownership even if cancelled during blocking storage I/O.
    override suspend fun create(info: SonyImageInfo): CameraImageDestination =
        withContext(NonCancellable) {
            withContext(Dispatchers.IO) { createMediaStoreImage(info) }
        }

    private fun createMediaStoreImage(info: SonyImageInfo): CameraImageDestination {
        val resolver = context.contentResolver
        val uri = checkNotNull(
            resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, info.filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, info.mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Alpha GPS")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                })
        )
        val stream = try {
            checkNotNull(resolver.openOutputStream(uri, "w"))
        } catch (failure: Throwable) {
            resolver.delete(uri, null, null); throw failure
        }
        return object : CameraImageDestination {
            private var committed = false
            override suspend fun write(bytes: ByteArray) =
                withContext(Dispatchers.IO) { stream.write(bytes) }

            override suspend fun commit() = withContext(NonCancellable + Dispatchers.IO) {
                stream.close()
                check(
                    resolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                        null,
                        null
                    ) == 1
                )
                committed = true
            }

            override suspend fun abort() = withContext(NonCancellable + Dispatchers.IO) {
                if (!committed) try {
                    stream.close()
                } finally {
                    resolver.delete(uri, null, null)
                }
                Unit
            }
        }
    }
}
