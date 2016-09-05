package kr.ac.snu.hcil.omnitrack.services

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kr.ac.snu.hcil.omnitrack.OTApplication
import kr.ac.snu.hcil.omnitrack.core.OTItemBuilder
import kr.ac.snu.hcil.omnitrack.core.OTTracker


/*TODO
*
* this will need to be refactored to be a normal service to handle the long retrieval operation of external measure factory values.
 */
class OTBackgroundLoggingService : IntentService("OTBackgroundLoggingService") {

    companion object {
        private val ACTION_LOG = "kr.ac.snu.hcil.omnitrack.services.action.LOG"

        fun startLogging(context: Context, tracker: OTTracker) {

            context.startService(makeIntent(context, tracker))
        }

        fun makeIntent(context: Context, tracker: OTTracker): Intent
        {
            val intent = Intent(context, OTBackgroundLoggingService::class.java)
            intent.action = ACTION_LOG
            intent.putExtra(OTApplication.INTENT_EXTRA_OBJECT_ID_TRACKER, tracker.objectId)
            return intent
        }
    }

    override fun onHandleIntent(intent: Intent?) {
        //tracker_id
        if (intent != null) {
            val action = intent.action
            if (ACTION_LOG == action) {
                val trackerId = intent.getStringExtra(OTApplication.INTENT_EXTRA_OBJECT_ID_TRACKER)
                handleLogging(trackerId)
            }
        }
    }

    private fun handleLogging(trackerId: String) {
        val tracker = OTApplication.app.currentUser[trackerId]
        if (tracker != null) {
            val builder = OTItemBuilder(tracker, OTItemBuilder.MODE_BACKGROUND)

            builder.autoCompleteAsync {
                OTApplication.app.dbHelper.save(builder.makeItem(), tracker)

                Toast.makeText(OTApplication.app, "${tracker.name} item was logged", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
