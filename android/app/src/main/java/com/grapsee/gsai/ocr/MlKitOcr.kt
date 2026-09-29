package com.grapsee.gsai.ocr

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * On-device OCR via ML Kit, which is the PRIMARY path.
 *
 * Why this exists rather than the native one: `GsNative.runOcr` cannot work in
 * this build, and never has. The native side says so itself:
 *
 *     set_err("no OCR engine is compiled into this mobile build; run OCR through
 *             the provider path or use a gs-ocr-enabled build");
 *
 * The Tesseract archives that WOULD make it work are cross-compiled and
 * verified (item 2, 12,282,294 bytes stripped) but are not linked into gs-ffi,
 * and the on-demand module that would carry them is BLOCKED. So the engine that
 * actually ships is ML Kit, bundled in the app module
 * (`libs.mlkit.text.recognition`, 16.0.1), and that is what this wraps.
 *
 * Synchronous on purpose. The callers are Kotlin and the tests are JUnit, and
 * every caller that wanted async had been wrapping this in a coroutine anyway.
 * The work happens on ML Kit's own executor; `Tasks.await` blocks the calling
 * thread, so callers on the main thread must not use it.
 */
object MlKitOcr {

    /** A timeout for [recognize] on a 0.5B-scale device with a cold ML Kit. */
    private const val TIMEOUT_SECONDS = 30L

    /**
     * The text in the image at [imagePath], or throws with the reason.
     *
     * An image with no text returns "" -- which is a real answer and is
     * deliberately different from a failure. A missing file, an unreadable
     * image, or a model failure all throw, so "no text here" and "OCR did not
     * run" never look the same.
     */
    fun recognize(context: Context, imagePath: String): String {
        val f = File(imagePath)
        require(f.isFile) { "image does not exist: $imagePath" }
        require(f.length() > 0) { "image is empty: $imagePath" }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(f))
            val result = Tasks.await(
                recognizer.process(image),
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
            return result.text
        } finally {
            recognizer.close()
        }
    }

    /** [recognize], or null with the reason. For callers that treat OCR as optional. */
    fun recognizeOrNull(context: Context, imagePath: String): Result<String> =
        runCatching { recognize(context, imagePath) }
}
