package kr.ac.snu.hcil.omnitrack.core.di.global

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import kr.ac.snu.hcil.omnitrack.core.di.configured.DaoSerializationComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Created by younghokim on 2017. 12. 19..
 */
@Module(subcomponents = [DaoSerializationComponent::class])
class SerializationModule {
    @Provides
    @Singleton
    @ForGeneric
    fun provideGenericGson(): Gson {
        return Gson()
    }

}

@Qualifier
@Retention(AnnotationRetention.RUNTIME) annotation class ForGeneric