package kr.ac.snu.hcil.omnitrack.core.di.configured

import dagger.Module
import dagger.Provides
import dagger.internal.Factory
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.annotations.RealmModule
import kr.ac.snu.hcil.omnitrack.BuildConfig
import kr.ac.snu.hcil.omnitrack.core.database.configured.models.*
import kr.ac.snu.hcil.omnitrack.core.database.configured.models.helpermodels.*
import kr.ac.snu.hcil.omnitrack.core.di.Configured
import java.io.File
import javax.inject.Qualifier

/**
 * Created by Young-Ho on 11/3/2017.
 */

@Qualifier
@Retention(AnnotationRetention.RUNTIME) annotation class Backend

@Module(includes = [ConfiguredModule::class, AuthModule::class, NetworkModule::class, DaoSerializationModule::class])
class BackendDatabaseModule {

    @Provides
    @Configured
    @Backend
    fun backendDatabaseConfiguration(@ConfigurationDirectory configDirectory: File): RealmConfiguration {
        return RealmConfiguration.Builder()
                .directory(configDirectory)
                .name("backend.db")
                .modules(BackendRealmModule())
                .run {
                    if (BuildConfig.DEBUG) {
                        this.deleteRealmIfMigrationNeeded()
                    } else this
                }
                .build()
    }

    @Provides
    @Configured
    @Backend
    fun makeBackendDbRealmProvider(@Backend configuration: RealmConfiguration): Factory<Realm> {
        return Factory<Realm> { Realm.getInstance(configuration) }
    }
}

@RealmModule(classes = [
        OTUserDAO::class,
        OTTrackerDAO::class,
        OTAttributeDAO::class,
        OTTriggerDAO::class,
        OTItemDAO::class,
        OTItemValueEntryDAO::class,
        OTItemBuilderFieldValueEntry::class,
        OTItemBuilderDAO::class,
        OTItemBuilderFieldValueEntry::class,
        OTStringStringEntryDAO::class,
        OTIntegerStringEntryDAO::class,
        OTTriggerAlarmInstance::class,
        OTTriggerReminderEntry::class,
        OTTriggerSchedule::class,
        UploadTaskInfo::class
])
class BackendRealmModule
