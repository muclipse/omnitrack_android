package kr.ac.snu.hcil.omnitrack.core.database.models.helpermodels

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

/**
 * Created by Young-Ho on 10/8/2017.
 */

open class OTTrackerLayoutElementDAO : RealmObject() {

    companion object {
        const val TYPE_FIELD = "field"
        const val TYPE_DESCRIPTION = "desc"
    }

    @PrimaryKey
    var id: String = ""
    var type: String = ""
    var reference: String = ""
}