package kr.ac.snu.hcil.omnitrack.core.attributes.helpers

import android.Manifest
import android.content.Context
import android.view.View
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.database.SynchronizedUri
import kr.ac.snu.hcil.omnitrack.core.database.local.OTAttributeDAO
import kr.ac.snu.hcil.omnitrack.statistics.NumericCharacteristics
import kr.ac.snu.hcil.omnitrack.ui.components.common.sound.AudioItemListView
import kr.ac.snu.hcil.omnitrack.ui.components.inputs.attributes.AAttributeInputView
import kr.ac.snu.hcil.omnitrack.ui.components.inputs.attributes.AudioRecordInputView
import kr.ac.snu.hcil.omnitrack.utils.serialization.TypeStringSerializationHelper
import rx.Single

/**
 * Created by Young-Ho on 10/7/2017.
 */
class OTAudioRecordAttributeHelper : OTAttributeHelper() {

    private val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    override fun getValueNumericCharacteristics(attribute: OTAttributeDAO): NumericCharacteristics = NumericCharacteristics(false, false)

    override fun getTypeNameResourceId(attribute: OTAttributeDAO): Int {
        return R.string.type_audio_record_name
    }

    override fun getTypeSmallIconResourceId(attribute: OTAttributeDAO): Int {
        return R.drawable.icon_small_audio
    }

    override fun isExternalFile(attribute: OTAttributeDAO): Boolean {
        return true
    }

    override fun getRequiredPermissions(attribute: OTAttributeDAO): Array<String>? = permissions

    override val typeNameForSerialization: String = TypeStringSerializationHelper.TYPENAME_SYNCHRONIZED_URI

    override fun getInputViewType(previewMode: Boolean, attribute: OTAttributeDAO): Int = AAttributeInputView.VIEW_TYPE_AUDIO_RECORD

    override fun refreshInputViewUI(inputView: AAttributeInputView<out Any>, attribute: OTAttributeDAO) {
        if (inputView is AudioRecordInputView) {   //TODO cacheDir
            //inputView.valueView.recordingOutputDirectoryPathOverride = tracker?.getItemCacheDir(inputView.context, true)
        }
    }

    override fun formatAttributeValue(attribute: OTAttributeDAO, value: Any): CharSequence {
        return "Audio $value"
    }

    override fun getViewForItemList(attribute: OTAttributeDAO, context: Context, recycledView: View?): View {
        val view = recycledView as? AudioItemListView ?: AudioItemListView(context)

        return view
    }

    override fun applyValueToViewForItemList(attribute: OTAttributeDAO, value: Any?, view: View): Single<Boolean> {
        if (view is AudioItemListView && value is SynchronizedUri) {
            view.mountedUri = value
        }
        return super.applyValueToViewForItemList(attribute, value, view)
    }
}