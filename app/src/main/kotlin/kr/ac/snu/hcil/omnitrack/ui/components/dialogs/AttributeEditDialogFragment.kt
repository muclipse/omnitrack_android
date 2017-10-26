package kr.ac.snu.hcil.omnitrack.ui.components.dialogs

import android.app.Dialog
import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import io.realm.Realm
import kr.ac.snu.hcil.omnitrack.OTApp
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.database.local.OTAttributeDAO
import kr.ac.snu.hcil.omnitrack.core.database.local.OTItemDAO
import kr.ac.snu.hcil.omnitrack.ui.components.common.LockableFrameLayout
import kr.ac.snu.hcil.omnitrack.ui.components.common.RxBoundDialogFragment
import kr.ac.snu.hcil.omnitrack.ui.components.inputs.attributes.AAttributeInputView
import kr.ac.snu.hcil.omnitrack.utils.serialization.TypeStringSerializationHelper

/**
 * Created by younghokim on 2017. 4. 14..
 */
class AttributeEditDialogFragment : RxBoundDialogFragment() {

    companion object {
        const val TAG = "AttributeEditDialog"
        const val EXTRA_SERIALIZED_VALUE = "serializedValue"

        fun makeInstance(itemId: String, attributeLocalId: String, trackerId: String, listener: Listener): AttributeEditDialogFragment {
            val args = Bundle()
            args.putString(OTApp.INTENT_EXTRA_OBJECT_ID_TRACKER, trackerId)
            args.putString(OTApp.INTENT_EXTRA_LOCAL_ID_ATTRIBUTE, attributeLocalId)
            args.putString(OTApp.INTENT_EXTRA_OBJECT_ID_ITEM, itemId)

            val instance = AttributeEditDialogFragment()
            instance.arguments = args
            instance.addListener(listener)

            return instance
        }
    }

    interface Listener {
        fun onOkAttributeEditDialog(changed: Boolean, value: Any?, trackerId: String, attributeLocalId: String, itemId: String?)
    }

    private var trackerId: String? = null
    private var item: OTItemDAO? = null
    private var attribute: OTAttributeDAO? = null

    private lateinit var container: LockableFrameLayout
    private lateinit var progressBar: View

    private var titleView: TextView? = null
    private var valueView: AAttributeInputView<out Any>? = null

    private var isContentLoaded: Boolean = false

    private val listeners = HashSet<Listener>()

    private lateinit var realm: Realm

    private val subscriptions = CompositeDisposable()

    fun addListener(listener: Listener) {
        this.listeners.add(listener)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        realm = OTApp.instance.databaseManager.getRealmInstance()

        val view = setupViews(LayoutInflater.from(activity), savedInstanceState)

        val dialog = MaterialDialog.Builder(context!!)
                .customView(view, true)
                .positiveColorRes(R.color.colorPointed)
                .positiveText(R.string.msg_apply)
                .negativeColorRes(R.color.colorRed_Light)
                .negativeText(R.string.msg_cancel)
                .cancelable(false)
                .title(getString(R.string.msg_attribute_edit_dialog_title))
                .autoDismiss(false)
                .onNegative { dialog, which ->
                    dialog.dismiss()
                }
                .onPositive { dialog, which ->
                    this.valueView?.let { valueView ->
                        subscriptions.add(
                                valueView.forceApplyValueAsync().subscribe { (value) ->
                                    val changed: Boolean
                                    if (this.item == null) {
                                        changed = true
                                    } else {
                                        val itemValue = attribute?.localId?.let { this.item?.getValueOf(it) }
                                        changed = value != itemValue
                                    }

                                    val attribute = attribute
                                    val trackerId = trackerId
                                    if (trackerId != null && attribute != null) {
                                        for (listener in listeners) {
                                            listener.onOkAttributeEditDialog(changed, value, trackerId, attribute.localId, item?.objectId)
                                        }
                                    }

                                    dialog.dismiss()
                                }
                        )
                    }
                }
                .build()

        titleView = dialog.titleView

        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        realm.close()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val activity = activity
        if (activity is Listener && savedInstanceState != null) {
            addListener(activity)
        }
    }

    override fun onBind(savedInstanceState: Bundle?): Disposable {
        val bundle = savedInstanceState ?: arguments ?: Bundle()

        container.locked = true
        container.alpha = 0.25f
        progressBar.visibility = View.VISIBLE

        if (bundle.containsKey(OTApp.INTENT_EXTRA_OBJECT_ID_TRACKER)
                && bundle.containsKey(OTApp.INTENT_EXTRA_LOCAL_ID_ATTRIBUTE)) {
            trackerId = bundle.getString(OTApp.INTENT_EXTRA_OBJECT_ID_TRACKER)
            val attributeLocalId = bundle.getString(OTApp.INTENT_EXTRA_LOCAL_ID_ATTRIBUTE)
            val itemId = bundle.getString(OTApp.INTENT_EXTRA_OBJECT_ID_ITEM)

            val itemObservable = OTApp.instance.databaseManager
                    .makeItemsQuery(trackerId, null, null, realm)
                    .equalTo("objectId", itemId).findFirstAsync()
                    .asFlowable<OTItemDAO>().filter { it.isValid == true && it.isLoaded == true }
                    .firstOrError()
                    .doOnSuccess { this.item = it }

            val attributeObservable = realm.where(OTAttributeDAO::class.java).equalTo("trackerId", trackerId).equalTo("localId", attributeLocalId)
                    .findFirstAsync()
                    .asFlowable<OTAttributeDAO>().filter { it.isValid == true && it.isLoaded == true }
                    .firstOrError()
                    .doOnSuccess { this.attribute = it }

            return Single.zip(listOf(attributeObservable, itemObservable)) { array ->
                println("item edit dialog: loaded attribute and item (zip)")
                val attribute = array[0] as OTAttributeDAO
                val item = array[1] as OTItemDAO
                Pair(attribute, item)
            }.subscribe { (attr, item) ->
                println("item edit dialog: loaded attribute and item")
                this.titleView?.text = String.format(resources.getString(R.string.msg_format_attribute_edit_dialog_title), attr.name)
                this.valueView = attr.getHelper().getInputView(context!!, false, attr, this.valueView)
                this.valueView?.boundAttributeObjectId = attr.objectId

                if (valueView != null) {
                    valueView?.onCreate(savedInstanceState)
                    valueView?.onResume()
                    if (this.container.getChildAt(0) !== valueView) {
                        this.container.removeAllViewsInLayout()
                        this.container.addView(valueView)
                    }
                }

                val cachedItemValue = bundle.getString(EXTRA_SERIALIZED_VALUE)
                if (cachedItemValue != null) {
                    val value = TypeStringSerializationHelper.deserialize(cachedItemValue)
                    this.valueView?.setAnyValue(value)
                } else {
                    if (this.attribute != null) {
                        val value = item.getValueOf(attr.localId)
                        if (value != null) {
                            println("value : $value")
                            this.valueView?.setAnyValue(value)
                        }
                    }
                }

                progressBar.visibility = View.GONE
                container.alpha = 1f
                container.locked = false
                isContentLoaded = true
            }
        } else return Disposables.empty()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        valueView?.onLowMemory()
    }

    override fun onResume() {
        super.onResume()
        valueView?.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        trackerId = null
        item = null
        attribute = null
        valueView?.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        valueView?.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        valueView?.let {
            outState.putString(EXTRA_SERIALIZED_VALUE, it.value?.let { TypeStringSerializationHelper.serialize(it) })
        }

        trackerId?.let {
            outState.putString(OTApp.INTENT_EXTRA_OBJECT_ID_TRACKER, trackerId)
        }

        attribute?.let {
            outState.putString(OTApp.INTENT_EXTRA_LOCAL_ID_ATTRIBUTE, it.localId)
        }

        item?.let {
            outState.putString(OTApp.INTENT_EXTRA_OBJECT_ID_ITEM, it.objectId)
        }
    }

    private fun setupViews(inflater: LayoutInflater, savedInstanceState: Bundle?): View {

        /*
        val layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val view = FrameLayout(activity)
        view.layoutParams = layoutParams
        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.activity_horizontal_margin)
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.activity_vertical_margin)
        view.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        */
        val view = inflater.inflate(R.layout.layout_item_field_edit_dialog, null, false)
        progressBar = view.findViewById(R.id.ui_progress_bar)
        container = view.findViewById(R.id.container)
        return view
    }

    fun show(fragmentManager: FragmentManager) {
        this.show(fragmentManager, TAG)
    }

}