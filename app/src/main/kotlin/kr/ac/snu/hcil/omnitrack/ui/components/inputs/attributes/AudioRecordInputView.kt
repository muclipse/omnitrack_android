package kr.ac.snu.hcil.omnitrack.ui.components.inputs.attributes

import android.content.Context
import android.util.AttributeSet
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.database.SynchronizedUri
import kr.ac.snu.hcil.omnitrack.ui.components.common.sound.AudioRecorderView

/**
 * Created by Young-Ho Kim on 2016-07-22.
 */
class AudioRecordInputView(context: Context, attrs: AttributeSet? = null) : AAttributeInputView<SynchronizedUri>(R.layout.input_audio_record, context, attrs) {
    override val typeId: Int = VIEW_TYPE_AUDIO_RECORD


    override var value: SynchronizedUri
        get() = SynchronizedUri(valueView.audioFileUri)
        set(value) {
            valueView.audioFileUri = value.localUri
        }

    val valueView: AudioRecorderView

    init {
        valueView = findViewById(R.id.ui_audio_recorder) as AudioRecorderView

        valueView.fileRemoved += {
            sender, time ->
            this.onValueChanged(SynchronizedUri(valueView.audioFileUri))
        }

        valueView.recordingComplete += {
            sender, length ->
            this.onValueChanged(SynchronizedUri(valueView.audioFileUri))
        }
    }

    override fun focus() {

    }

    override fun onAttributeBound(attributeId: String) {
        valueView.mediaSessionId = attributeId
    }

    override fun onPause() {
        super.onPause()
        println("dispose audio recorder view")
        valueView.dispose()
    }
}