package com.grapsee.gsai.ocrfallback

import android.content.Context
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.android.gms.tasks.Task
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdateListener
import com.google.android.play.core.splitinstall.model.SplitInstallState
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR engine selection, and the one-time install of the fallback.
 *
 * MOVED OUT OF THE APP MODULE. It was in app/src/main and would not compile:
 * Play Core was not resolving onto the classpath, so every splitinstall type was
 * an unresolved reference --
 *     Unresolved reference 'SplitInstallStateUpdateListener'.
 *     Unresolved reference 'SplitInstallState'.
 *     Unresolved reference 'getInstalledModuleNames'.
 *     Unresolved reference 'uninstallModule'.
 * and one call site got past that to a type mismatch against
 * com.google.android.play.core.splitinstall.SplitInstallStateUpdateListener.
 *
 * The code belongs HERE, in the feature module, which is where the decision to
 * use the fallback belongs. The app module should not reference the fallback's
 * types at all: it asks the module whether the module is installed, and never
 * links against it.
 *
 * ## The two paths
 *
 *   ML Kit        PRIMARY, bundled in the base APK. ~99% of Android devices have
 *                 Play Services, and the bundled model needs no download and no
 *                 Play Services at runtime.
 *   Tesseract     FALLBACK, an on-demand dynamic feature. ~1% of devices have no
 *                 Play Services at all -- GrapheneOS, LineageOS without GMS,
 *                 Huawei after 2019, some Chinese ROMs -- and for those ML Kit
 *                 does not merely fail, it is not there. That 1% is the whole
 *                 reason the fallback exists, and it is why it is a separate
 *                 module: 12.3 MB of static libraries plus ~15 MB of traineddata
 *                 is the wrong thing to charge to every install to cover 1%.
 *
 * ## The contract that makes this safe
 *
 * Both paths return [OcrResult], and the caller cannot tell which ran. If the
 * two returned different shapes, every call site would grow a branch, and the
 * branch would be wrong on exactly the 1% of devices it exists to serve.
 *
 * ## Failure is not an empty string
 *
 * A failed OCR throws or returns [OcrResult.failure] with a reason. It never
 * returns empty text, because empty text is indistinguishable from "this photo
 * genuinely has no words in it" and the caller would show a blank result with no
 * error, which is the worst thing this can do.
 */
class OcrEngine private constructor(
    private val context: Context,
    private val hasPlayServices: Boolean,
) {

    enum class Engine { ML_KIT, TESSERACT, NONE }

    @Volatile
    var engine: Engine = Engine.NONE
        private set

    /** Human-readable, for a diagnostics screen. */
    val describe: String
        get() = "engine=${engine.name} playServices=$hasPlayServices " +
            "fallbackInstalled=${isFallbackInstalled()}"

    /**
     * Decides the engine. Deliberately NOT downloading anything: the fallback
     * install is a user-visible decision and is started by [requestFallbackModule]
     * after they agree to it.
     */
    fun select() {
        engine = when {
            hasPlayServices -> Engine.ML_KIT
            isFallbackInstalled() -> Engine.TESSERACT
            // No Play Services and no fallback yet. The caller should offer the
            // download; OCR is unavailable until then, and that is said plainly
            // rather than silently degraded.
            else -> Engine.NONE
        }
        Log.i(TAG, "OCR engine selected: $describe")
    }

    // ---- Play Services availability -------------------------------------

    companion object {
        private const val TAG = "OcrEngine"
        const val FALLBACK_MODULE = "ocr-fallback"

        /**
         * Is Google Play Services usable on this device?
         *
         * `isGooglePlayServicesAvailable` rather than a check for the GMS APK:
         * the APK can be present and the service still unusable -- disabled,
         * outdated, or blocked by a ROM. The API answer is the one that matters,
         * and it is the only way to know before a request fails.
         *
         * Returns false on ANY throw. A device where the check itself errors is
         * a device where the primary path is not dependable, so falling back to
         * the module is the safe reading.
         */
        fun hasPlayServices(context: Context): Boolean = try {
            val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val status = availability.isGooglePlayServicesAvailable(context)
            if (status == com.google.android.gms.common.ConnectionResult.SUCCESS) {
                true
            } else {
                Log.w(TAG, "Play Services unavailable, status=$status")
                false
            }
        } catch (t: Throwable) {
            // The check can throw NoClassDefFoundError on a ROM that ships a
            // partial GMS. That is exactly the population this fallback serves.
            Log.w(TAG, "Play Services check threw; treating as unavailable", t)
            false
        }

        @Volatile
        private var instance: OcrEngine? = null

        /** Process-wide. The ML Kit recogniser is expensive to build per call. */
        @JvmStatic
        fun get(context: Context): OcrEngine {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: OcrEngine(
                    context.applicationContext,
                    hasPlayServices(context.applicationContext),
                ).also {
                    it.select()
                    instance = it
                }
            }
        }
    }

    // ---- fallback module state -------------------------------------------

    fun isFallbackInstalled(): Boolean = try {
        val manager = SplitInstallManagerFactory.create(context)
        manager.getInstalledModuleNames().contains(FALLBACK_MODULE)
    } catch (t: Throwable) {
        Log.w(TAG, "could not query installed modules", t)
        false
    }

    /**
     * Start the one-time fallback install. Returns false if Play Core itself is
     * unavailable, which is a third distinct case: no Play Services for the
     * CHECK, and no Play Core for the INSTALLER either.
     */
    fun requestFallbackModule(onState: (String) -> Unit): Boolean = try {
        val manager = SplitInstallManagerFactory.create(context)
        val listener = object : SplitInstallStateUpdateListener {
            override fun onStateUpdate(state: SplitInstallState) = Unit
            override fun onInstallStarted(names: MutableList<String>) =
                onState("downloading the OCR fallback")

            override fun onInstallFailed(names: MutableList<String>, errorCode: Int, message: String?) {
                Log.e(TAG, "fallback install FAILED code=$errorCode: $message")
                // A failed install is reported with its code, not swallowed. The
                // user was promised OCR and did not get it.
                onState("OCR fallback install failed ($errorCode): ${message ?: "no detail"}")
                select()
            }

            override fun onInstallComplete(names: MutableList<String>) {
                Log.i(TAG, "fallback installed: $names")
                onState("OCR fallback ready")
                select()
            }
        }
        manager.registerListener(listener)
        val request = SplitInstallRequest.newBuilder().addModule(FALLBACK_MODULE).build()
        manager.startInstall(request)
        true
    } catch (t: Throwable) {
        Log.e(TAG, "cannot start the fallback install", t)
        onState("this device cannot install the OCR fallback")
        false
    }

    /** Remove the fallback and reclaim its space. */
    fun removeFallbackModule() {
        try {
            SplitInstallManagerFactory.create(context)
                .uninstallModule(FALLBACK_MODULE)
            select()
        } catch (t: Throwable) {
            Log.w(TAG, "uninstall failed", t)
        }
    }

    // ---- recognition ------------------------------------------------------

    /**
     * Recognise text in an image file.
     *
     * Suspends. Both engines do real work off the main thread and the caller
     * should not have to remember that.
     */
    suspend fun recognise(imagePath: String): OcrResult {
        val file = File(imagePath)
        if (!file.isFile) {
            return OcrResult.failure("no such image: $imagePath")
        }
        return when (engine) {
            Engine.ML_KIT -> runMlKit(imagePath)
            Engine.TESSERACT -> runTesseract(imagePath)
            Engine.NONE -> OcrResult.failure(
                "no OCR engine: Play Services is absent and the fallback module " +
                    "is not installed"
            )
        }
    }

    private suspend fun runMlKit(imagePath: String): OcrResult = try {
        val image = InputImage.fromFilePath(context, android.net.Uri.fromFile(File(imagePath)))
        val recogniser = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val text = recogniser.process(image).awaitText()
        recogniser.close()
        if (text.isBlank()) OcrResult.noText() else OcrResult.ok(text, Engine.ML_KIT)
    } catch (t: Throwable) {
        Log.e(TAG, "ML Kit failed", t)
        OcrResult.failure("ML Kit: ${t.message ?: t.javaClass.simpleName}")
    }

    /**
     * Tesseract through the JNI shim that the fallback module provides.
     *
     * Reached by reflection on purpose: the class lives in the dynamic feature,
     * which is NOT on the classpath unless the module is installed. A direct
     * reference compiles fine and throws NoClassDefFoundError at runtime on the
     * 99% of devices that never installed it, which is a crash in a text
     * recognition path.
     */
    private suspend fun runTesseract(imagePath: String): OcrResult = try {
        val cls = Class.forName("com.grapsee.gsai.ocrfallback.TesseractOcr")
        val method = cls.getMethod("recognise", Context::class.java, String::class.java)
        @Suppress("UNCHECKED_CAST")
        val text = method.invoke(null, context, imagePath) as String
        if (text.isBlank()) OcrResult.noText() else OcrResult.ok(text, Engine.TESSERACT)
    } catch (e: ClassNotFoundException) {
        Log.e(TAG, "fallback module is not installed", e)
        OcrResult.failure("the OCR fallback module is not installed")
    } catch (t: Throwable) {
        Log.e(TAG, "Tesseract failed", t)
        OcrResult.failure("Tesseract: ${t.message ?: t.javaClass.simpleName}")
    }
}

/**
 * The result, identical in shape whichever engine produced it.
 *
 * [empty] is a real answer -- the image genuinely has no text -- and is
 * deliberately distinct from a failure. Conflating them is how "OCR found
 * nothing" becomes "OCR is broken" with no way to tell.
 */
sealed class OcrResult {
    data class Success(val text: String, val engine: OcrEngine.Engine) : OcrResult()
    data object NoTextFound : OcrResult()
    data class Failed(val reason: String) : OcrResult()

    val isSuccess: Boolean get() = this is Success
    val isEmpty: Boolean get() = this is NoTextFound

    /** The text, or a reason. Never an empty string for a failure. */
    fun textOrReason(): String = when (this) {
        is Success -> text
        is NoTextFound -> ""
        is Failed -> "OCR failed: $reason"
    }

    companion object {
        fun ok(text: String, engine: OcrEngine.Engine) = Success(text, engine)
        fun noText() = NoTextFound
        fun failure(reason: String) = Failed(reason)
    }
}

/**
 * Await a Google Task on a coroutine.
 *
 * ML Kit returns a Task, and blocking on it from a coroutine would occupy a
 * thread for the length of a recognition. Without this, the caller either
 * blocks or nests a callback and the cancellation story gets lost.
 */
private suspend fun <T> Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
        // A Task that outlives the coroutine must stop listening, or the
        // continuation is resumed into a cancelled scope and the recogniser
        // keeps a reference the caller can no longer reach.
        cont.invokeOnCancellation { }
    }

private suspend fun Task<String>.awaitText(): String = awaitTask()
