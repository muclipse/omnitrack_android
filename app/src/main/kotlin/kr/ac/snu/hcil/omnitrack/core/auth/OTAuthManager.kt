package kr.ac.snu.hcil.omnitrack.core.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.GoogleApiClient
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.Lazy
import dagger.internal.Factory
import io.reactivex.Completable
import io.reactivex.Completable.defer
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposables
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import kr.ac.snu.hcil.omnitrack.BuildConfig
import kr.ac.snu.hcil.omnitrack.OTApp
import kr.ac.snu.hcil.omnitrack.core.analytics.IEventLogger
import kr.ac.snu.hcil.omnitrack.core.database.OTDeviceInfo
import kr.ac.snu.hcil.omnitrack.core.database.models.OTUserDAO
import kr.ac.snu.hcil.omnitrack.core.di.global.Backend
import kr.ac.snu.hcil.omnitrack.core.di.global.ForGeneralAuth
import kr.ac.snu.hcil.omnitrack.core.di.global.ForGeneric
import kr.ac.snu.hcil.omnitrack.core.net.ISynchronizationServerSideAPI
import kr.ac.snu.hcil.omnitrack.core.system.OTShortcutPanelManager
import kr.ac.snu.hcil.omnitrack.core.toCompletable
import kr.ac.snu.hcil.omnitrack.core.toSingle
import kr.ac.snu.hcil.omnitrack.core.triggers.OTTriggerSystemManager
import kr.ac.snu.hcil.omnitrack.ui.pages.experiment.ExperimentSignUpActivity
import org.jetbrains.anko.runOnUiThread
import rx_activity_result2.Result
import rx_activity_result2.RxActivityResult
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by Young-Ho Kim on 2017-02-03.
 */
@Singleton
class OTAuthManager @Inject constructor(
        private val context: Context,
        private val firebaseAuth: FirebaseAuth,
        private val triggerSystemManager: Lazy<OTTriggerSystemManager>,
        private val shortcutPanelManager: OTShortcutPanelManager,
        private val eventLogger: Lazy<IEventLogger>,
        @ForGeneric private val gson: Gson,
        @Backend private val realmFactory: Factory<Realm>,
        @ForGeneralAuth private val googleSignInOptions: Lazy<GoogleSignInOptions>,
        private val synchronizationServerController: Lazy<ISynchronizationServerSideAPI>) {

    companion object {
        const val LOG_TAG = "OMNITRACK Auth Manager"
        const val GOOGLE_SIGN_IN_REQUEST_CODE = 9428
    }
    enum class SignedInLevel {
        NONE, CACHED, AUTHORIZED
    }

    enum class AuthError(@StringRes messageResId: Int) {
        NETWORK_NOT_CONNECTED(0), OMNITRACK_SERVER_NOT_RESPOND(0)
    }

    private val mGoogleApiClient: GoogleApiClient

    init {
        Log.d(LOG_TAG, "Initializing Google SDK...")

        // Build GoogleApiClient with access to basic profile
        mGoogleApiClient = GoogleApiClient.Builder(context)
                .addApi(Auth.GOOGLE_SIGN_IN_API, googleSignInOptions.get())
                .build()
        mGoogleApiClient.connect()
    }

    fun getDeviceLocalKey(): String? {
        val uid = userId
        if (uid != null) {
            return realmFactory.get().use { realm ->
                realm.where(OTUserDAO::class.java).equalTo("uid", uid)
                        .findFirst()?.uid
            }
        } else return null
    }

    val userId: String?
        get() {
            val id = firebaseAuth.currentUser?.uid
            println("user id: $id")
            return id
        }

    fun isUserSignedIn(): Boolean {
        return firebaseAuth.currentUser != null && isUserInDb(firebaseAuth.currentUser!!.uid)
    }

    private fun isUserInDb(uid: String): Boolean {
        realmFactory.get().use { realm ->
            return realm.where(OTUserDAO::class.java).equalTo("uid", uid)
                    .findFirst() != null
        }
    }

    val currentSignedInLevel: SignedInLevel get() {
        val result = try {
            if (isUserSignedIn()) {
                return SignedInLevel.AUTHORIZED
            } else return SignedInLevel.NONE
        } catch(ex: Exception) {
            SignedInLevel.NONE
        }

        println("Current signed in level: $result")
        return result
    }

    fun loadGoogleSignInAccount(): Maybe<GoogleSignInAccount> {
        return Maybe.defer {
            val connectionResult = mGoogleApiClient.blockingConnect()
            if (connectionResult.isSuccess) {
                return@defer Maybe.just(Auth.GoogleSignInApi.silentSignIn(mGoogleApiClient).await().signInAccount!!)
            } else return@defer Maybe.never<GoogleSignInAccount>()
        }.observeOn(Schedulers.io())
    }

    fun getAuthToken(): Maybe<String> {
        return Maybe.create { disposable ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                val task = user.getIdToken(true).addOnCompleteListener { result ->
                    if (result.isSuccessful) {
                        val token = result.result?.token!!
                        if (!disposable.isDisposed) {
                            disposable.onSuccess(token)
                        }
                    } else {
                        if (!disposable.isDisposed) {
                            disposable.onError(result.exception ?: Exception("Token error"))
                        }
                    }
                }
            } else {
                if (!disposable.isDisposed) {
                    disposable.onComplete()
                }
            }
        }
    }

    private fun signInSilently(): Completable {
        return loadGoogleSignInAccount().flatMapSingle { acc ->
            firebaseAuthWithGoogle(acc)
        }.ignoreElement()
    }

    fun refreshCredentialSilently(skipValidationIfCacheHit: Boolean): Completable {
        return defer {
            if (isUserSignedIn()) {
                if (skipValidationIfCacheHit) {
                    Log.d(LOG_TAG, "Skip sign in. use cached Firebase User.")
                    return@defer Completable.complete()
                } else {
                    Log.d(LOG_TAG, "Reload Firebase User to check connection.")

                    return@defer firebaseAuth.currentUser!!.reload().toCompletable().onErrorResumeNext {
                        signInSilently()
                    }
                }
            } else {
                Log.d(LOG_TAG, "Firebase user does not exist. Sign in silently")
                return@defer signInSilently()
            }
        }

    }

    fun getAuthStateRefreshObservable(): Observable<SignedInLevel> {
        return Observable.create { subscriber ->
            val listener = FirebaseAuth.AuthStateListener { auth ->
                val signedInLevel = currentSignedInLevel
                if (!subscriber.isDisposed) {
                    subscriber.onNext(signedInLevel)
                }
            }
            firebaseAuth.addAuthStateListener(listener)

            subscriber.setDisposable(Disposables.fromAction { firebaseAuth.removeAuthStateListener(listener) })
        }
    }

    fun makeSignIntent(): Intent {
        return Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient)
    }

    fun handleSignInProcessResult(activityResult: Result<AppCompatActivity>): Single<Boolean> {
        return Single.defer {
            val result = Auth.GoogleSignInApi.getSignInResultFromIntent(activityResult.data())
            if (result.isSuccess) {
                //Signed in successfully.
                println("signed in google account: ${result.signInAccount}")
                firebaseAuthWithGoogle(result.signInAccount!!)

                        .flatMap { authResult ->
                            println("Signed in through Google account. try to push device info to server...")
                            OTDeviceInfo.makeDeviceInfo(context)
                        }
                        .flatMap { deviceInfo ->
                            if (!BuildConfig.DEFAULT_EXPERIMENT_ID.isNullOrBlank()) {
                                synchronizationServerController.get()
                                        .checkExperimentParticipationStatus(BuildConfig.DEFAULT_EXPERIMENT_ID)
                                        .flatMap { isInExperiment ->
                                            if (!isInExperiment) {
                                                synchronizationServerController.get().getExperimentConsentInfo(BuildConfig.DEFAULT_EXPERIMENT_ID)
                                                        .flatMap {
                                                            if (BuildConfig.DEFAULT_INVITATION_CODE != null && (!it.receiveConsentInApp || (it.consent == null && it.demographicFormSchema == null))) {
                                                                Single.just(Pair<String, JsonObject?>(BuildConfig.DEFAULT_INVITATION_CODE, null))
                                                            } else RxActivityResult.on(activityResult.targetUI())
                                                                    .startIntent(ExperimentSignUpActivity.makeIntent(activityResult.targetUI(), BuildConfig.DEFAULT_INVITATION_CODE == null, if (it.receiveConsentInApp) it.consent else null, if (it.receiveConsentInApp) it.demographicFormSchema else null))
                                                                    .firstOrError()
                                                                    .map { result ->
                                                                        if (result.resultCode() != Activity.RESULT_OK) {
                                                                            throw CancellationException()
                                                                        } else {
                                                                            Pair<String, JsonObject?>(
                                                                                    BuildConfig.DEFAULT_INVITATION_CODE
                                                                                            ?: result.data().getStringExtra(ExperimentSignUpActivity.INVITATION_CODE),
                                                                                    result.data().getStringExtra(ExperimentSignUpActivity.DEMOGRAPHIC_SCHEMA)?.let { serializedSchema ->
                                                                                        gson.fromJson(serializedSchema, JsonObject::class.java)
                                                                                    })
                                                                        }
                                                                    }
                                                        }.flatMap {
                                                            synchronizationServerController.get().authenticate(deviceInfo, it.first, it.second)
                                                        }
                                            } else synchronizationServerController.get().authenticate(deviceInfo, null, null)
                                        }
                            } else {
                                //Pure experiment-free authentication
                                synchronizationServerController.get().authenticate(deviceInfo, null, null)
                            }
                        }
                        .flatMap { authenticationResult ->
                            handleAuthenticationResult(authenticationResult)
                        }
                        .doOnSuccess { success ->
                            if (success) {
                                activityResult.targetUI().applicationContext.runOnUiThread { notifySignedIn() }
                                triggerSystemManager.get().checkInAllToSystem(userId!!)
                            }
                        }.doOnError { ex ->
                            ex.printStackTrace()
                            firebaseAuth.signOut()
                            Auth.GoogleSignInApi.signOut(mGoogleApiClient)
                        }
            } else if (result.status.isCanceled || result.status.statusCode == 12501) {
                throw CancellationException(result.status.statusMessage)
            } else {
                println("Google sign in failed")
                eventLogger.get().logExceptionEvent("GoogleSignInError",
                        Exception(result.status.statusMessage),
                        Thread.currentThread()) { json ->
                    json.addProperty("statusCode", result.status.statusCode)
                    json.addProperty("isInterrupted", result.status.isInterrupted)
                    json.addProperty("isCanceled", result.status.isCanceled)
                    json.addProperty("hasResolution", result.status.hasResolution())
                }
                return@defer Single.error<Boolean>(Exception("Google login process was failed. status code: ${result.status.statusCode}, message: ${result.status.statusMessage}"))
            }
        }
    }

    fun handleAuthenticationResult(result: ISynchronizationServerSideAPI.AuthenticationResult): Single<Boolean> {
        return Single.defer {
            val userInfo = result.userInfo
            if (userInfo != null) {
                realmFactory.get().use { realm ->
                    realm.executeTransaction {
                        val user = realm.where(OTUserDAO::class.java).equalTo("uid", userInfo._id).findFirst()
                                ?: realm.createObject(OTUserDAO::class.java, userInfo._id)
                        user.thisDeviceLocalKey = result.deviceLocalKey
                        user.email = userInfo.email
                        user.name = userInfo.name ?: ""
                        user.photoServerPath = userInfo.picture ?: ""
                        user.nameUpdatedAt = userInfo.nameUpdatedAt ?: System.currentTimeMillis()
                        user.nameSynchronizedAt = user.nameUpdatedAt
                    }
                }
                return@defer Single.just(true)
            } else return@defer Single.just(false)
        }
    }

    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount): Single<AuthResult> {
        return Single.defer {
            Log.d(LOG_TAG, "firebaseAuthWithGooogle:" + acct.id)
            val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
            return@defer firebaseAuth.signInWithCredential(credential).toSingle()
        }
    }

    /*
    fun deleteUser() {
        firebaseAuth.currentUser?.delete()?.addOnCompleteListener {
            task ->
            if (task.isSuccessful) {
                resultsHandler.onSuccess()
                signOut()
            } else {
                resultsHandler.onError(task.exception!!)
            }
        } ?: resultsHandler.onError(Exception("Not signed in."))
    }*/

    fun signOut() {
        val lastUserId = userId
        if (lastUserId != null) {
            clearUserInfo()
            firebaseAuth.signOut()
            Auth.GoogleSignInApi.signOut(mGoogleApiClient)

            triggerSystemManager.get().checkOutAllFromSystem(lastUserId)
            shortcutPanelManager.disposeShortcutPanel()

            notifySignedOut()
        }
    }

    private fun notifySignedIn() {
        val intent = Intent(OTApp.BROADCAST_ACTION_USER_SIGNED_IN)
                .putExtra(OTApp.INTENT_EXTRA_OBJECT_ID_USER, userId)
        context.sendBroadcast(intent)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    private fun notifySignedOut() {
        val intent = Intent(OTApp.BROADCAST_ACTION_USER_SIGNED_OUT)
                .putExtra(OTApp.INTENT_EXTRA_OBJECT_ID_USER, userId)
        context.sendBroadcast(intent)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    fun clearUserInfo() {
        if (userId != null) {
            realmFactory.get().use { realm ->
                realm.executeTransaction { realm ->
                    realm.where(OTUserDAO::class.java).equalTo("uid", userId).findAll().deleteAllFromRealm()
                    /*
                    realm.where(OTTrackerDAO::class.java).equalTo("userId", userId).findAll().deleteAllFromRealm()
                    realm.where(OTTriggerDAO::class.java).equalTo("userId", userId).findAll().deleteAllFromRealm()
                    realm.where(OTItemDAO::class.java).equalTo("userId", userId).findAll().deleteAllFromRealm()*/

                }
            }
        }
    }
}