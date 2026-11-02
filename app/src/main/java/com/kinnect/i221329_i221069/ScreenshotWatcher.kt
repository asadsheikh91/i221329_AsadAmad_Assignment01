package com.kinnect.i221329_i221069

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import androidx.core.content.ContextCompat

// Notices when the user takes a screenshot while an Activity (the chat) is on screen.
//  - Android 14 and later: the system tells us directly (registerScreenCaptureCallback, permission DETECT_SCREEN_CAPTURE).
//  - Older Android: there is no such callback, so we watch the phone's picture library. A new picture whose file name
//    or folder contains "screenshot" and that is only a few seconds old is a screenshot (needs access to photos).
// [onScreenshot] is called at most once every 3 seconds.
class ScreenshotWatcher(private val activity: Activity, private val onScreenshot: () -> Unit) {

    private var callback: Activity.ScreenCaptureCallback? = null
    private var observer: ContentObserver? = null
    private var lastReport = 0L

    fun start() {
        if (Build.VERSION.SDK_INT >= 34) {
            val screenCallback = Activity.ScreenCaptureCallback { report() }
            callback = screenCallback
            activity.registerScreenCaptureCallback(activity.mainExecutor, screenCallback)
        } else if (hasPhotoAccess()) {
            val watcher = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    if (newestPictureIsScreenshot()) {
                        report()
                    }
                }
            }
            observer = watcher
            activity.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, watcher)
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= 34) {
            callback?.let { activity.unregisterScreenCaptureCallback(it) }
        }
        observer?.let { activity.contentResolver.unregisterContentObserver(it) }
        callback = null
        observer = null
    }

    private fun report() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastReport < 3000) {
            return
        }
        lastReport = now
        onScreenshot()
    }

    private fun hasPhotoAccess(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    /** Is the newest picture of the phone a screenshot that was taken just now? */
    private fun newestPictureIsScreenshot(): Boolean {
        val columns = arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.DATA)
        val cursor = activity.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, null, null,
            MediaStore.Images.Media.DATE_ADDED + " DESC"
        ) ?: return false
        cursor.use {
            if (!it.moveToFirst()) {
                return false
            }
            val name = (it.getString(0) ?: "").lowercase()
            val added = it.getLong(1)
            val path = (it.getString(2) ?: "").lowercase()
            val recent = System.currentTimeMillis() / 1000 - added < 10
            return recent && (name.contains("screenshot") || path.contains("screenshot"))
        }
    }
}
