package kr.ac.snu.hcil.omnitrack.ui.pages.attribute

import android.arch.lifecycle.ViewModel
import kr.ac.snu.hcil.omnitrack.OTApplication
import kr.ac.snu.hcil.omnitrack.core.attributes.OTAttributeManager
import kr.ac.snu.hcil.omnitrack.core.attributes.helpers.OTAttributeHelper
import kr.ac.snu.hcil.omnitrack.core.connection.OTConnection
import kr.ac.snu.hcil.omnitrack.core.database.local.OTAttributeDAO
import kr.ac.snu.hcil.omnitrack.utils.Nullable
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import java.util.*

/**
 * Created by Young-Ho on 10/8/2017.
 */
class AttributeDetailViewModel : ViewModel() {
    companion object {
        const val CONNECTION_NULL = "null"
    }

    var attributeDAO: OTAttributeDAO? = null
        private set

    private val realm = OTApplication.app.databaseManager.getRealmInstance()

    val isValid: Boolean get() = attributeDAO?.isValid ?: false

    val attributeHelper: OTAttributeHelper? get() = attributeDAO?.let { OTAttributeManager.getAttributeHelper(it.type) }

    val isInDatabase: Boolean get() = attributeDAO?.isManaged ?: false

    val nameObservable = BehaviorSubject.create<String>("")
    val connectionObservable = BehaviorSubject.create<Nullable<OTConnection>>()

    val typeObservable = BehaviorSubject.create<Int>()

    val defaultValuePolicyObservable = BehaviorSubject.create<Int>(-1)
    val defaultValuePresetObservable = BehaviorSubject.create<Nullable<String>>(Nullable<String>(null))

    val isRequiredObservable = BehaviorSubject.create<Boolean>(false)

    val onPropertyValueChanged = PublishSubject.create<Pair<String, Any?>>()

    private val propertyTable = Hashtable<String, Any?>()

    val currentPropertyTable: Map<String, Any?> get() = propertyTable

    var name: String
        get() {
            return nameObservable.value
        }
        set(value) {
            if (nameObservable.value != value) {
                nameObservable.onNext(value)
            }
        }

    var isRequired: Boolean
        get() {
            return isRequiredObservable.value
        }
        set(value) {
            if (isRequiredObservable.value != value) {
                isRequiredObservable.onNext(value)
            }
        }

    var defaultValuePolicy: Int
        get() = defaultValuePolicyObservable.value
        set(value) {
            if (value != defaultValuePolicyObservable.value) {
                defaultValuePolicyObservable.onNext(value)
            }
        }

    var defaultValuePreset: String?
        get() = defaultValuePresetObservable.value.datum
        set(value) {
            if (value != defaultValuePresetObservable.value.datum) {
                defaultValuePresetObservable.onNext(Nullable(value))
            }
        }

    val isConnectionDirty: Boolean
        get() = connectionObservable.value?.datum != attributeDAO?.getParsedConnection()

    val isDefaultValuePolicyDirty: Boolean
        get() = defaultValuePolicy != attributeDAO?.fallbackValuePolicy

    val isDefaultValuePresetDirty: Boolean
        get() = defaultValuePreset != attributeDAO?.fallbackPresetSerializedValue

    val isNameDirty: Boolean
        get() = name != attributeDAO?.name

    val isRequiredDirty: Boolean
        get() = isRequired != attributeDAO?.isRequired

    var connection: OTConnection?
        get() {
            return connectionObservable.value?.datum
        }
        set(value) {
            if (connectionObservable.value?.datum != value) {
                connectionObservable.onNext(Nullable(value))
            }
        }

    fun init(attributeDao: OTAttributeDAO) {
        if (this.attributeDAO != attributeDao) {
            this.attributeDAO = attributeDao
            name = attributeDao.name
            connection = attributeDao.serializedConnection?.let { OTConnection.fromJson(it) }

            isRequired = attributeDao.isRequired

            println("initial policy: ${attributeDao.fallbackValuePolicy}")

            defaultValuePolicy = attributeDao.fallbackValuePolicy
            defaultValuePreset = attributeDao.fallbackPresetSerializedValue

            propertyTable.clear()
            val helper = attributeHelper
            if (helper != null) {
                val table = helper.getDeserializedPropertyTable(attributeDao)
                for (entry in table) {
                    propertyTable.set(entry.key, entry.value)
                    onPropertyValueChanged.onNext(Pair(entry.key, entry.value))
                }
            }

            if (typeObservable.value != attributeDao.type)
                typeObservable.onNext(attributeDao.type)
        }
    }

    fun setPropertyValue(propertyKey: String, value: Any?) {
        if (propertyTable[propertyKey] != value) {
            propertyTable[propertyKey] = value
            onPropertyValueChanged.onNext(Pair(propertyKey, value))
        }
    }

    fun isPropertyChanged(propertyKey: String): Boolean {
        return attributeDAO?.let { propertyTable[propertyKey] != attributeHelper?.getDeserializedPropertyValue<Any>(propertyKey, it) } ?: false
    }

    fun hasAnyPropertyChanged(): Boolean {
        return attributeHelper?.propertyKeys?.find { isPropertyChanged(it) } != null
    }

    fun isChanged(): Boolean {
        return isNameDirty || isRequiredDirty || isDefaultValuePolicyDirty || isDefaultValuePresetDirty || hasAnyPropertyChanged() || isConnectionDirty
    }

    fun applyChanges() {
        attributeDAO?.name = name
        attributeDAO?.fallbackValuePolicy = defaultValuePolicy
        attributeDAO?.fallbackPresetSerializedValue = defaultValuePreset
        attributeDAO?.isRequired = isRequired

        for (entry in propertyTable) {
            val value = entry.value
            val propertyHelper = attributeHelper?.getPropertyHelper<Any>(entry.key)
            if (propertyHelper != null && value != null) {
                attributeDAO?.setPropertySerializedValue(entry.key, propertyHelper.getSerializedValue(value))
            }
        }

        attributeDAO?.serializedConnection = connection?.getSerializedString()
    }

    fun makeFrontalChangesToDao(): OTAttributeDAO? {
        return attributeDAO?.let {
            val dao = OTAttributeDAO()
            dao.objectId = it.objectId
            dao.type = it.type
            dao.localId = it.localId
            dao.position = it.position
            dao.trackerId = it.trackerId
            dao.updatedAt = it.updatedAt
            dao.isRequired = it.isRequired
            dao.fallbackValuePolicy = defaultValuePolicy
            dao.fallbackPresetSerializedValue = defaultValuePreset
            dao.name = name
            for (entry in propertyTable) {
                val value = entry.value
                val propertyHelper = attributeHelper?.getPropertyHelper<Any>(entry.key)
                if (propertyHelper != null && value != null) {
                    dao.setPropertySerializedValue(entry.key, propertyHelper.getSerializedValue(value))
                }
            }

            dao.serializedConnection = connectionObservable.value?.datum?.getSerializedString()
            dao
        }
    }

    override fun onCleared() {
        super.onCleared()
        realm.close()
        attributeDAO = null
    }
}