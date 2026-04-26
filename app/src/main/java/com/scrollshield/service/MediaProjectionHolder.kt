package com.scrollshield.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Parcel
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.scrollshield.classification.ScreenCaptureManager
import dagger.hilt.android.qualifiers.ApplicationContext

class MediaProjectionHolder(
    @ApplicationContext private val context: Context,
    private val mediaProjectionManager: MediaProjectionManager,
    private val screenCaptureManager: ScreenCaptureManager
) {
    private var mediaProjection: MediaProjection? = null

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "scrollshield_mp_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("MPH", "EncryptedSharedPreferences init failed, falling back to plain prefs", e)
            context.getSharedPreferences("scrollshield_mp_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    private val revocationCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            mediaProjection = null
            screenCaptureManager.stop()
        }
    }

    fun setMediaProjection(resultCode: Int, data: Intent) {
        mediaProjection?.stop()
        val projection = try {
            mediaProjectionManager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Log.e("MPH", "getMediaProjection failed", e)
            return
        }
        if (projection == null) {
            Log.e("MPH", "getMediaProjection returned null")
            return
        }
        projection.registerCallback(revocationCallback, null)
        mediaProjection = projection
        persistIntent(data, resultCode)
        screenCaptureManager.start(projection)
    }

    fun getMediaProjection(): MediaProjection? = mediaProjection

    fun stop() {
        mediaProjection?.stop()
        mediaProjection = null
        screenCaptureManager.stop()
    }

    fun getStoredResultIntent(): Intent? {
        val encoded = prefs.getString("mp_result_intent", null) ?: return null
        return try {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val parcel = Parcel.obtain()
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            val intent = Intent.CREATOR.createFromParcel(parcel)
            parcel.recycle()
            intent
        } catch (e: Exception) {
            Log.e("MPH", "Failed to restore result intent", e)
            null
        }
    }

    fun getStoredResultCode(): Int =
        prefs.getInt("mp_result_code", android.app.Activity.RESULT_CANCELED)

    private fun persistIntent(data: Intent, resultCode: Int) {
        try {
            val parcel = Parcel.obtain()
            data.writeToParcel(parcel, 0)
            val bytes = parcel.marshall()
            parcel.recycle()
            val encoded = Base64.encodeToString(bytes, Base64.DEFAULT)
            prefs.edit()
                .putString("mp_result_intent", encoded)
                .putInt("mp_result_code", resultCode)
                .apply()
        } catch (e: Exception) {
            Log.e("MPH", "Failed to persist result intent", e)
        }
    }
}
