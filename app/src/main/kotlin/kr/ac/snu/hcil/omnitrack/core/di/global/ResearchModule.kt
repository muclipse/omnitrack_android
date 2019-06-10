package kr.ac.snu.hcil.omnitrack.core.di.global

import dagger.Module
import dagger.Provides
import dagger.internal.Factory
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.annotations.RealmModule
import kr.ac.snu.hcil.omnitrack.BuildConfig
import kr.ac.snu.hcil.omnitrack.core.database.models.research.OTExperimentDAO
import kr.ac.snu.hcil.omnitrack.core.database.models.research.OTExperimentInvitationDAO
import kr.ac.snu.hcil.omnitrack.core.net.IResearchServerAPI
import kr.ac.snu.hcil.omnitrack.core.net.OTOfficialServerApiController
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Created by younghokim on 2018. 1. 3..
 */

@Qualifier
@Retention(AnnotationRetention.RUNTIME) annotation class Research

@Module(includes = [NetworkModule::class, UsageLoggingModule::class, FirebaseModule::class])
class ResearchModule {

    @Provides
    @Singleton
    @Research
    fun researchDatabaseConfiguration(): RealmConfiguration {
        return RealmConfiguration.Builder()
                .name("research.db")
                .modules(ResearchRealmModule())
                .run {
                    if (BuildConfig.DEBUG) {
                        this.deleteRealmIfMigrationNeeded()
                    } else this
                }
                .build()
    }

    @Provides
    @Singleton
    @Research
    fun makeResearchDbRealmProvider(@Research configuration: RealmConfiguration): Factory<Realm> {
        return Factory { Realm.getInstance(configuration) }
    }

    @Provides
    @Singleton
    fun provideResearchServerController(controller: OTOfficialServerApiController): IResearchServerAPI {
        return controller
    }

}

@RealmModule(classes = [
    OTExperimentDAO::class,
    OTExperimentInvitationDAO::class
])
class ResearchRealmModule