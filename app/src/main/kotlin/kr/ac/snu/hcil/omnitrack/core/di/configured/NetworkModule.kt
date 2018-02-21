package kr.ac.snu.hcil.omnitrack.core.di.configured

import android.content.Context
import com.firebase.jobdispatcher.FirebaseJobDispatcher
import com.firebase.jobdispatcher.Job
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.internal.Factory
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.annotations.RealmModule
import kr.ac.snu.hcil.omnitrack.BuildConfig
import kr.ac.snu.hcil.omnitrack.core.auth.OTAuthManager
import kr.ac.snu.hcil.omnitrack.core.configuration.OTConfiguration
import kr.ac.snu.hcil.omnitrack.core.database.configured.models.helpermodels.LocalMediaCacheEntry
import kr.ac.snu.hcil.omnitrack.core.database.configured.models.helpermodels.UploadTaskInfo
import kr.ac.snu.hcil.omnitrack.core.di.Configured
import kr.ac.snu.hcil.omnitrack.core.di.global.DeviceId
import kr.ac.snu.hcil.omnitrack.core.di.global.Sha1FingerPrint
import kr.ac.snu.hcil.omnitrack.core.net.*
import kr.ac.snu.hcil.omnitrack.utils.LocaleHelper
import okhttp3.Cache
import okhttp3.MediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Provider
import javax.inject.Qualifier

/**
 * Created by younghokim on 2017-11-01.
 */
@Module(includes = [ConfiguredModule::class])
class NetworkModule {
    private val rxJava2CallAdapterFactory: RxJava2CallAdapterFactory by lazy {
        RxJava2CallAdapterFactory.create()
    }
    private val gsonConverterFactory: GsonConverterFactory by lazy {
        GsonConverterFactory.create()
    }

    @Provides
    @Configured
    fun provideHttpCache(context: Context): Cache {
        val cacheSize = 10 * 1024 * 1024
        return Cache(context.cacheDir, cacheSize.toLong())
    }

    @Provides
    @Configured
    @Authorized
    fun provideOkHttpClient(context: Context, authManager: Lazy<OTAuthManager>, @DeviceId deviceId: Lazy<String>, @Sha1FingerPrint fingerPrint: String, cache: Cache): OkHttpClient {
        return OkHttpClient.Builder()
                .cache(cache)
                .addInterceptor { chain ->
                    val bearer = "Bearer " + authManager.get().getAuthToken().blockingGet()
                    val newRequest = chain.request().newBuilder()
                            .addHeader("Authorization", bearer)
                            .addHeader("OTDeviceId", deviceId.get())
                            .addHeader("OTFingerPrint", fingerPrint)
                            .addHeader("OTPackageName", BuildConfig.APPLICATION_ID)
                            .addHeader("OTRole", "ServiceUser")
                            .addHeader("OTLocale", LocaleHelper.getLanguageCode(context))
                            .build()
                    println("Provide HTTP client bounding to " + newRequest.url().toString())
                    chain.proceed(newRequest)
                }.build()
    }

    @Provides
    @Configured
    @OkHttpMediaType(MediaTypeValue.IMAGE)
    fun provideImageMediaType(): MediaType {
        return MediaType.parse("image/*")!!
    }

    @Provides
    @Configured
    @OkHttpMediaType(MediaTypeValue.PLAINTEXT)
    fun providePlainTextMediaType(): MediaType {
        return MediaType.parse("text/plain")!!
    }



    @Provides
    @Configured
    @SynchronizationServer
    fun provideSynchronizationRetrofit(config: OTConfiguration, @Authorized client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
                .client(client)
                .baseUrl(config.synchronizationServerUrl)
                .addCallAdapterFactory(rxJava2CallAdapterFactory)
                .addConverterFactory(gsonConverterFactory)
                .build()
    }

    @Provides
    @Configured
    @BinaryStorageServer
    fun provideBinaryStorageRetrofit(config: OTConfiguration, @Authorized client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
                .client(client)
                .baseUrl(config.mediaStorageServerUrl)
                .addCallAdapterFactory(rxJava2CallAdapterFactory)
                .addConverterFactory(gsonConverterFactory)
                .build()
    }

    @Provides
    @Configured
    fun provideOfficialServerController(@SynchronizationServer retrofit: Retrofit): OTOfficialServerApiController {
        return OTOfficialServerApiController(retrofit)
    }

    @Provides
    @Configured
    fun provideBinaryStorageController(
            dispatcher: Lazy<FirebaseJobDispatcher>,
            @BinaryStorageServer jobProvider: Provider<Job>,
            core: IBinaryStorageCore): OTBinaryStorageController {
        return OTBinaryStorageController(dispatcher, jobProvider, core, provideRealm())
    }

    /**
     * Change this to replace binary storage
     */
    @Provides
    @Configured
    fun provideBinaryStorageCore(context: Context, @BinaryStorageServer retrofit: Lazy<Retrofit>): IBinaryStorageCore {
        //return OTFirebaseStorageCore()
        return OTOfficialBinaryStorageCore(context, retrofit)
    }

    @Provides
    @Configured
    fun provideSynchronizationServerController(controller: OTOfficialServerApiController): ISynchronizationServerSideAPI {
        return controller
    }

    @Provides
    @Configured
    fun provideLocalMediaCacheManager(context: Context, authManager: Lazy<OTAuthManager>, storageController: Lazy<OTBinaryStorageController>): OTLocalMediaCacheManager {
        return OTLocalMediaCacheManager(context, authManager, storageController)
    }

    @Provides
    @Configured
    fun provideUserReportServerController(controller: OTOfficialServerApiController): IUserReportServerAPI {
        return controller
    }

    @Provides
    @Configured
    fun provideUsageLogUploadController(controller: OTOfficialServerApiController): IUsageLogUploadAPI {
        return controller
    }

    private val realmConfiguration: RealmConfiguration by lazy {
        RealmConfiguration.Builder().name("media_storage.db").modules(UploadTaskQueueRealmModule()).deleteRealmIfMigrationNeeded().build()
    }

    private fun provideRealm(): Factory<Realm> {
        return object : Factory<Realm> {
            override fun get(): Realm {
                return Realm.getInstance(realmConfiguration)
            }
        }
    }
}

enum class MediaTypeValue {
    IMAGE, BINARY, PLAINTEXT, JSON,
}

@Qualifier
@Retention(AnnotationRetention.RUNTIME) annotation class Authorized

@Qualifier
@Retention(AnnotationRetention.RUNTIME) annotation class SynchronizationServer

@Qualifier
@Retention(AnnotationRetention.RUNTIME) annotation class BinaryStorageServer

@Qualifier
@Retention(AnnotationRetention.RUNTIME) annotation class OkHttpMediaType(val type: MediaTypeValue)

@RealmModule(classes = arrayOf(UploadTaskInfo::class, LocalMediaCacheEntry::class))
class UploadTaskQueueRealmModule