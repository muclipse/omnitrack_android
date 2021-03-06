package kr.ac.snu.hcil.omnitrack.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.annotations.RealmModule
import kr.ac.snu.hcil.android.common.view.applyTint
import org.jetbrains.anko.dip
import java.io.File
import java.io.FileOutputStream

/**
 * Created by Young-Ho on 5/23/2017.
 */

@RealmModule(classes = [VectorIconBitmapCache::class])
class VectorIconCacheRealmModule

object VectorIconHelper {

    private val realmConfiguration: RealmConfiguration by lazy {
        RealmConfiguration.Builder().name("vectorIconCache").modules(VectorIconCacheRealmModule()).deleteRealmIfMigrationNeeded().build()
    }

    private val memCache = object : LruCache<String, Bitmap>(20) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bitmap?, newValue: Bitmap?) {
            super.entryRemoved(evicted, key, oldValue, newValue)
            oldValue?.recycle()
        }

    }

    private fun makeKey(resourceId: Int, sizeDp: Int?, tint: Int?): String {
        val builder = StringBuilder(resourceId.toString())
        if (sizeDp == null) {
            builder.append("|null")
        } else {
            builder.append("|").append(sizeDp)
        }

        if (tint == null) {
            builder.append("|null")
        } else {
            builder.append("|").append(tint)
        }

        return builder.toString()
    }

    fun getConvertedBitmap(context: Context, @DrawableRes vectorDrawableRes: Int, sizeDp: Int? = 24, tint: Int? = null): Bitmap {
        val cacheKey = makeKey(vectorDrawableRes, sizeDp, tint)
        val memCachedBitmap = memCache.get(cacheKey)
        if (memCachedBitmap == null) {
            val realm = Realm.getInstance(realmConfiguration)
            val cache = realm.where(VectorIconBitmapCache::class.java)
                    .equalTo("resourceId", vectorDrawableRes)
                    .equalTo("sizeDp", sizeDp)
                    .equalTo("tint", tint)
                    .findAll().toTypedArray()

            val cachedBitmap: Bitmap? = if (cache.isNotEmpty()) {
                BitmapFactory.decodeFile(cache[0].uri)
            } else null

            val finalBitmap = if (cachedBitmap != null) {
                cachedBitmap
            } else {
                var drawable = AppCompatResources.getDrawable(context, vectorDrawableRes)!!
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    drawable = if (tint != null) {
                        applyTint(drawable, tint)
                    } else (DrawableCompat.wrap(drawable)).mutate()
                } else if (tint != null) {
                    drawable.setTint(tint)
                }

                val width: Int = if (sizeDp != null) {
                    context.dip(sizeDp)
                } else drawable.intrinsicWidth
                val height: Int = if (sizeDp != null) {
                    context.dip(sizeDp)
                } else drawable.intrinsicHeight

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)

                var outputStream: FileOutputStream? = null
                try {
                    val cacheDirectory = File(context.externalCacheDir, "converted_vector_icons")
                    if (!cacheDirectory.exists())
                        cacheDirectory.mkdirs()

                    val output = File.createTempFile("cache_", "png", cacheDirectory)
                    outputStream = output.outputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)

                    if (cache.isNotEmpty()) {
                        //cache information is in DB, but its uri is broken. Update the existing one.
                        realm.executeTransaction { rlm ->
                            cache[0].uri = output.absolutePath
                        }
                    } else {
                        val newCache = VectorIconBitmapCache()
                        newCache.resourceId = vectorDrawableRes
                        newCache.sizeDp = sizeDp
                        newCache.tint = tint
                        newCache.uri = output.absolutePath

                        realm.executeTransaction { rlm ->
                            rlm.insert(newCache)
                        }
                    }

                } catch(ex: Exception) {
                    ex.printStackTrace()
                } finally {
                    try {
                        outputStream?.close()
                    } catch(ex: Exception) {
                        ex.printStackTrace()
                    }
                }
                bitmap
            }

            realm.close()

            memCache.put(cacheKey, finalBitmap)

            return finalBitmap
        } else {
            return memCachedBitmap
        }
    }
}