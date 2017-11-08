package kr.ac.snu.hcil.omnitrack.core

import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import kr.ac.snu.hcil.omnitrack.OTApp
import kr.ac.snu.hcil.omnitrack.core.database.local.OTAttributeDAO
import kr.ac.snu.hcil.omnitrack.core.database.local.OTItemBuilderDAO
import kr.ac.snu.hcil.omnitrack.core.database.local.OTItemDAO
import kr.ac.snu.hcil.omnitrack.utils.Nullable
import kr.ac.snu.hcil.omnitrack.utils.ValueWithTimestamp
import kr.ac.snu.hcil.omnitrack.utils.serialization.TypeStringSerializationHelper
import javax.inject.Provider

/**
 * Created by Young-Ho Kim on 16. 7. 25
 */
class OTItemBuilderWrapperBase(val dao: OTItemBuilderDAO, val realm: Realm) {

    enum class EAttributeValueState {
        Processing, GettingExternalValue, Idle
    }

    interface AttributeStateChangedListener {
        fun onAttributeStateChanged(attributeLocalId: String, state: EAttributeValueState)
    }

    val keys: Set<String> by lazy { dao.data.mapNotNull { it.attributeLocalId }.toSet() }

    fun getValueInformationOf(attributeLocalId: String): ValueWithTimestamp? {
        return this.dao.data.find { it.attributeLocalId == attributeLocalId }?.let {
            ValueWithTimestamp(
                    it.serializedValue?.let { TypeStringSerializationHelper.deserialize(it) },
                    it.timestamp
            )
        }
    }

    fun saveToItem(itemDao: OTItemDAO?, loggingSource: ItemLoggingSource?): OTItemDAO {
        val itemDaoToSave = itemDao ?: OTItemDAO()
        if (itemDao == null) {
            itemDaoToSave.deviceId = OTApp.instance.deviceId
            itemDaoToSave.loggingSource = loggingSource ?: ItemLoggingSource.Unspecified
            itemDaoToSave.trackerId = dao.tracker?.objectId
        } else {
            itemDaoToSave.userUpdatedAt = System.currentTimeMillis()
        }
        itemDaoToSave.synchronizedAt = null

        dao.data.forEach { builderFieldEntry ->
            builderFieldEntry.attributeLocalId?.let {
                itemDaoToSave.setValueOf(it, builderFieldEntry.serializedValue)
            }
        }

        return itemDaoToSave
    }

    fun makeAutoCompleteObservable(realmProvider: Provider<Realm>, onAttributeStateChangedListener: AttributeStateChangedListener? = null, applyToBuilder: Boolean = false): Observable<Pair<String, ValueWithTimestamp>> {

        return Observable.defer {
            val attributes = dao.tracker?.attributes?.filter { it.isHidden == false && it.isInTrashcan == false }
            if (attributes == null) {
                return@defer Observable.empty<Pair<String, ValueWithTimestamp>>()
            } else {
                val realm = realmProvider.get()
                Observable.merge(attributes.mapIndexed { i, attr: OTAttributeDAO ->
                    val attrLocalId = attr.localId
                    val connection = attr.getParsedConnection()
                    if (connection != null && connection.isAvailableToRequestValue()) {
                        //Connection
                        connection.getRequestedValue(this).flatMap { data ->
                            if (data.datum == null) {
                                println("ValueConnection result was null. send fallback value")
                                return@flatMap attr.getFallbackValue(realm).toFlowable()
                            } else {
                                println("Received valueConnection result - ${data.datum}")
                                return@flatMap Flowable.just(data)
                            }
                        }.onErrorResumeNext { err: Throwable -> err.printStackTrace(); attr.getFallbackValue(realm).toFlowable() }.map { nullable: Nullable<out Any> -> Pair(attrLocalId, ValueWithTimestamp(nullable)) }.subscribeOn(Schedulers.io()).doOnSubscribe {

                            onAttributeStateChangedListener?.onAttributeStateChanged(attrLocalId, EAttributeValueState.GettingExternalValue)
                        }.toObservable()
                    } else {

                        println("No connection. use fallback value: ${attrLocalId}")
                        return@mapIndexed attr.getFallbackValue(realm).map { nullable ->
                            println("No connection. received fallback value: ${attrLocalId}, ${nullable.datum}")
                            Pair(attrLocalId, ValueWithTimestamp(nullable))
                        }.doOnSubscribe {
                            onAttributeStateChangedListener?.onAttributeStateChanged(attrLocalId, EAttributeValueState.Processing)
                        }.toObservable()
                    }
                }).observeOn(AndroidSchedulers.mainThread()).subscribeOn(Schedulers.io())
                        .doOnNext { result ->

                            println("RX doOnNext: ${Thread.currentThread().name}")
                            val attrLocalId = result.first
                            val value = result.second
                            if (applyToBuilder) {
                                dao.setValue(attrLocalId, value, realm)
                            }

                            onAttributeStateChangedListener?.onAttributeStateChanged(attrLocalId, EAttributeValueState.Idle)
                        }.doOnError { err -> err.printStackTrace(); realm.close() }.doOnComplete {
                    realm.close()
                    println("RX finished autocompleting builder=======================")
                }
            }
        }
    }
}