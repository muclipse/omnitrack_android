package kr.ac.snu.hcil.omnitrack.ui.pages.field.wizard.pages

import android.content.Context
import android.view.View
import kr.ac.snu.hcil.android.common.view.wizard.AWizardPage
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.connection.OTTimeRangeQuery
import kr.ac.snu.hcil.omnitrack.ui.pages.field.wizard.ConnectionWizardView

/**
 * Created by Young-Ho Kim on 2016-08-30.
 */
class TimeQueryPage(override val parent: ConnectionWizardView) : AWizardPage(parent) {

    override val getTitleResourceId: Int = R.string.msg_connection_wizard_title_time_query

    override val canGoBack: Boolean = false
    override val canGoNext: Boolean
        get() {
            return true
        }

    private var view: TimeQuerySettingPanel? = null

    val timeQuery: OTTimeRangeQuery?
        get() = view?.timeQuery


    init {
        isCompleteButtonAvailable = true
    }

    override fun onLeave() {
        view?.refreshQueryFromViewValues()
    }

    override fun onEnter() {
    }

    override fun makeViewInstance(context: Context): View {
        view = TimeQuerySettingPanel(context)
        view?.init()
        return view!!
    }
}