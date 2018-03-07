package kr.ac.snu.hcil.omnitrack.core.net

import com.google.gson.JsonObject
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kr.ac.snu.hcil.omnitrack.core.OTUserRolePOJO
import kr.ac.snu.hcil.omnitrack.core.database.OTDeviceInfo
import kr.ac.snu.hcil.omnitrack.core.database.configured.models.research.ExperimentInfo
import kr.ac.snu.hcil.omnitrack.core.synchronization.ESyncDataType
import kr.ac.snu.hcil.omnitrack.core.synchronization.SyncResultEntry
import kr.ac.snu.hcil.omnitrack.utils.ValueWithTimestamp
import retrofit2.Retrofit

/**
 * Created by younghokim on 2017. 9. 28..
 */
class OTOfficialServerApiController(retrofit: Retrofit) : ISynchronizationServerSideAPI, IUserReportServerAPI, IUsageLogUploadAPI, IResearchServerAPI {

    private val service: OTOfficialServerService by lazy {
        retrofit.create(OTOfficialServerService::class.java)
    }

    override fun getUserRoles(): Single<List<OTUserRolePOJO>> {
        return service.getUserRoles().subscribeOn(Schedulers.io())
    }

    override fun postUserRoleConsentResult(result: OTUserRolePOJO): Single<Boolean> {
        return service.postUserRoleConsentResult(result).subscribeOn(Schedulers.io())
    }

    override fun putDeviceInfo(info: OTDeviceInfo): Single<ISynchronizationServerSideAPI.DeviceInfoResult> {
        return service.putDeviceInfo(info).subscribeOn(Schedulers.io())
    }

    override fun sendUserReport(inquiryData: JsonObject): Single<Boolean> {
        return service.postUserReport(inquiryData).subscribeOn(Schedulers.io())
    }

    override fun removeDeviceInfo(userId: String, deviceId: String): Single<Boolean> {
        TODO()
    }

    override fun getRowsSynchronizedAfter(vararg batch: Pair<ESyncDataType, Long>): Single<Map<ESyncDataType, Array<JsonObject>>> {
        return service.getServerDataChanges(batch.map { it.first }.toTypedArray(), batch.map { it.second }.toTypedArray())
                .subscribeOn(Schedulers.io())
    }

    override fun postDirtyRows(vararg batch: ISynchronizationServerSideAPI.DirtyRowBatchParameter): Single<Map<ESyncDataType, Array<SyncResultEntry>>> {
        val maxChunkSize = 200
        if (batch.sumBy { it.rows.size } > maxChunkSize) {
            //divide chunk
            val chunkList = ArrayList<ISynchronizationServerSideAPI.DirtyRowBatchParameter>()
            batch.forEach { entry ->
                chunkList.addAll(entry.rows.toList().chunked(maxChunkSize, { chunk -> ISynchronizationServerSideAPI.DirtyRowBatchParameter(entry.type, chunk.toTypedArray()) }))
            }
            println("chunkList: " + chunkList.size)
            return Single.concat(chunkList.map { service.postLocalDataChanges(arrayOf(it)).subscribeOn(Schedulers.io()) }).reduce(HashMap<ESyncDataType, Array<SyncResultEntry>>() as Map<ESyncDataType, Array<SyncResultEntry>>) { finalMap: Map<ESyncDataType, Array<SyncResultEntry>>, newMap: Map<ESyncDataType, Array<SyncResultEntry>> ->
                newMap.entries.forEach {
                    if (finalMap.containsKey(it.key)) {
                        (finalMap as HashMap).set(it.key, finalMap[it.key]!! + it.value)
                    } else {
                        (finalMap as HashMap).set(it.key, it.value)
                    }
                }

                return@reduce finalMap
            }
        } else return service.postLocalDataChanges(batch).subscribeOn(Schedulers.io())
    }

    override fun uploadLocalUsageLogs(serializedLogs: List<String>): Single<List<Long>> {
        return service.uploadUsageLogs(serializedLogs)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    override fun putUserName(name: String, timestamp: Long): Single<ISynchronizationServerSideAPI.InformationUpdateResult> {
        return service.putUserName(ValueWithTimestamp(name, timestamp))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    override fun approveExperimentInvitation(invitationCode: String): Single<ExperimentCommandResult> {
        return service.approveExperimentInvitation(invitationCode)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    override fun rejectExperimentInvitation(invitationCode: String): Completable {
        return service.rejectExperimentInvitation(invitationCode)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    override fun dropOutFromExperiment(experimentId: String, reason: CharSequence?): Single<ExperimentCommandResult> {
        return service.dropOutFromExperiment(experimentId, DropoutBody(reason?.toString()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }


    override fun retrieveJoinedExperiments(after: Long): Single<List<ExperimentInfo>> {
        return service.getJoinedExperiments(after)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }


    override fun retrievePublicInvitations(): Single<List<ExperimentInvitation>> {
        return service.getPublicInvitations()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }
}