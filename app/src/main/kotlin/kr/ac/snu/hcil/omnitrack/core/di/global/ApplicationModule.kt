package kr.ac.snu.hcil.omnitrack.core.di.global

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import kr.ac.snu.hcil.omnitrack.OTAndroidApp
import kr.ac.snu.hcil.omnitrack.OTApp
import kr.ac.snu.hcil.omnitrack.utils.time.LocalTimeFormats
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import javax.inject.Qualifier
import javax.inject.Singleton


/**
 * Created by Young-Ho on 10/31/2017.
 */
@Module()
class ApplicationModule(private val mApp: OTApp) {

    @Provides
    @Singleton
    fun application(): OTAndroidApp {
        return mApp
    }

    @Provides
    @Singleton
    fun wrappedContext(): Context
    {
        return mApp.contextCompat
    }

    @Provides
    @Singleton
    fun wrappedResources(): Resources
    {
        return mApp.resourcesWrapped
    }

    @Provides
    @Singleton
    @Default
    fun sharedPreferences(): SharedPreferences
    {
        return PreferenceManager.getDefaultSharedPreferences(mApp)
    }

    @Provides
    @Singleton
    @UserInfo
    fun userInfoSharedPreferences(): SharedPreferences {
        return mApp.contextCompat.getSharedPreferences("USER_INFO", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    @DeviceId
    fun deviceId(): String {
        return mApp.deviceId
    }

    @Provides
    @Singleton
    @Sha1FingerPrint
    fun getCertificateSHA1Fingerprint(): String {
        try {
            val pm = mApp.packageManager
            val packageName = mApp.packageName


            val signatures = if (Build.VERSION.SDK_INT < 28) {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
            } else {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo.apkContentsSigners
            }

            val cert = signatures[0].toByteArray()
            val input = ByteArrayInputStream(cert)
            var cf: CertificateFactory? = null
            try {
                cf = CertificateFactory.getInstance("X509")
            } catch (e: CertificateException) {
                e.printStackTrace()
            }

            var c: X509Certificate? = null
            try {
                c = cf!!.generateCertificate(input) as X509Certificate
            } catch (e: CertificateException) {
                e.printStackTrace()
            }

            var hexString: String? = null
            try {
                val md = MessageDigest.getInstance("SHA1")
                val publicKey = md.digest(c!!.encoded)
                hexString = byte2HexFormatted(publicKey)
            } catch (e1: NoSuchAlgorithmException) {
                e1.printStackTrace()
            } catch (e: CertificateEncodingException) {
                e.printStackTrace()
            }

            return hexString ?: ""
        } catch (ex: Exception) {
            return ""
        }
    }

    @Provides
    @Singleton
    fun providesPreferredTimeZone(): TimeZone {
        return TimeZone.getDefault()
    }

    @Provides
    @Singleton
    fun providesLocalTimeFormats(context: Context): LocalTimeFormats {
        return LocalTimeFormats(context)
    }

    private fun byte2HexFormatted(arr: ByteArray): String {
        val str = StringBuilder(arr.size * 2)
        for (i in arr.indices) {
            var h = Integer.toHexString(arr[i].toInt())
            val l = h.length
            if (l == 1) h = "0" + h
            if (l > 2) h = h.substring(l - 2, l)
            str.append(h.toUpperCase())
            if (i < arr.size - 1) str.append(':')
        }
        return str.toString()
    }
}

@Qualifier
@Retention(AnnotationRetention.RUNTIME) annotation class DeviceId

@Qualifier
@Retention(AnnotationRetention.RUNTIME) annotation class Sha1FingerPrint

@Qualifier
@Retention(AnnotationRetention.RUNTIME) annotation class Default

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class UserInfo