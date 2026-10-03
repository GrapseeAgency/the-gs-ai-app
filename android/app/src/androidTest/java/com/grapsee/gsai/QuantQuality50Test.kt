package com.grapsee.gsai

import android.content.Context
import android.os.Environment
import com.grapsee.gsai.native.GsNative
import com.grapsee.gsai.native.GsNativeLoader
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * THE SHIPPED-QUANTISATION DECISION, measured rather than argued.
 *
 * **Q4_0 measured 788-792 ms TTFT against Q4_K_M's 3830-4269 ms -- 4.9x to 5.4x --
 * on runs 37005521803 and 37022078121.** The commit that recorded those numbers
 * also recorded, correctly, that they do not license shipping Q4_0, because
 * speed was the only axis measured and a quantisation trade is never only speed.
 *
 * So this measures the other axis: 50 fixed prompts, both quantisations, one
 * deterministic rule-based judge, a mean and a 95% interval each, and a delta.
 *
 * ## WHY THE JUDGE IS RULE-BASED AND NOT AN LLM
 *
 * An LLM judge introduces a second model whose quantisation, prompt and mood are
 * all uncontrolled, and its own sampling is a variance source of exactly the
 * size of the effect being measured. A deterministic rubric cannot drift between
 * runs, which is the property this decision needs. It is also *biased*: it can
 * only reward answers whose expected content is written down in advance. That
 * bias is stated rather than hidden, and it is the reason 23 of the 50 prompts
 * carry an explicit expected-answer list.
 *
 * ## THE RUBRIC, IN FULL, SO IT CAN BE DISPUTED
 *
 *   score 1  the reply is blank, OR it is degenerate (see below)
 *   score 2  not degenerate, but carries none of the expected answer and is
 *            too short to be an answer
 *   score 3  expected answer partly present (>=1 of N), or a short open reply
 *   score 4  expected answer >=60% present, or a full-length open reply
 *   score 5  EVERY expected token present
 *
 * Degenerate means a 24-character window repeats 4+ times, or the reply is one
 * token repeated to the budget. That is the failure mode quantisation damage
 * actually produces, and scoring it 1 is the whole point of having a bottom.
 *
 * This is a proxy for quality, and it is only a proxy. It cannot see
 * fluency, factuality beyond the written-down answers, helpfulness, or
 * tone. A score of 5 means "the answer that was expected is present in a
 * non-degenerate reply" and nothing more. Read it that way.
 */
@RunWith(AndroidJUnit4::class)
class QuantQuality50Test {

    /** One prompt and, where the answer is checkable, the tokens that must appear. */
    private data class Q(
        val prompt: String,
        val expect: List<String> = emptyList(),
    )

    // 50 prompts, written once and hardcoded. 23 have checkable answers; 27 are
    // open and are judged on form alone. Order is fixed so both quantisations
    // see the identical sequence.
    private val prompts = listOf(
        // --- 22 with checkable answers ---
        Q("What is the capital of France? Answer with one word.", listOf("paris")),
        Q("What is 12 + 7? Answer with the number only.", listOf("19")),
        Q("Name the largest planet in the solar system. One word.", listOf("jupiter")),
        Q("Translate 'good morning' into French. Reply with the French only.", listOf("bonjour", "matin")),
        Q("List the first five prime numbers, comma separated, nothing else.", listOf("2", "3", "5", "7", "11")),
        Q("What is the chemical symbol for water? One or two letters.", listOf("h2o")),
        Q("How many days are in a week? Number only.", listOf("7")),
        Q("What is the capital of Japan? One word.", listOf("tokyo")),
        Q("Name the capital of Italy. One word.", listOf("rome")),
        Q("What is 10 minus 4? Number only.", listOf("6")),
        Q("How many continents are there? Number only.", listOf("7")),
        Q("Which planet is known as the Red Planet? One word.", listOf("mars")),
        Q("What is the capital of Canada? One word.", listOf("ottawa")),
        Q("Translate 'thank you' into Spanish. Spanish only.", listOf("gracias")),
        Q("What is 5 times 6? Number only.", listOf("30")),
        Q("Name the largest ocean on Earth. One word.", listOf("pacific")),
        Q("How many legs does a spider have? Number only.", listOf("8")),
        Q("What gas do plants absorb from the air? One word.", listOf("carbon dioxide")),
        Q("What is the capital of Germany? One word.", listOf("berlin")),
        Q("How many minutes are in an hour? Number only.", listOf("60")),
        Q("What is the square root of 81? Number only.", listOf("9")),
        Q("Name the currency of Japan. One word.", listOf("yen")),
        // --- 28 open, judged on form ---
        Q("Explain in one sentence why the sky appears blue."),
        Q("Give a short tip for improving sleep quality."),
        Q("Write one sentence describing a rainy city street."),
        Q("Name three primary colours."),
        Q("In one sentence, what is photosynthesis for?"),
        Q("Give a one-sentence summary of what an operating system does."),
        Q("Suggest a name for a coffee shop."),
        Q("In one sentence, explain what gravity does."),
        Q("List two benefits of regular exercise."),
        Q("Write one sentence about why bridges have expansion joints."),
        Q("Describe the water cycle in one sentence."),
        Q("Give a short definition of 'algorithm'."),
        Q("Name three programming languages."),
        Q("In one sentence, explain what a compiler does."),
        Q("Write one sentence of advice for someone learning to cook."),
        Q("Give one reason why sleep matters."),
        Q("Name two uses of aluminium."),
        Q("Explain in one sentence what a database index is for."),
        Q("Write a one-sentence summary of the water cycle's importance."),
        Q("Give a short reason to wear a helmet when cycling."),
        Q("Name three colours that are not primary."),
        Q("In one sentence, explain what recycling does for the environment."),
        Q("Write one sentence about why the ocean is salty."),
        Q("Give a short definition of 'cache'."),
        Q("Name two types of renewable energy."),
        Q("In one sentence, explain what version control is for."),
        Q("Write one sentence of encouragement for a new programmer."),
        Q("Give a one-sentence reason to back up files."),
    )

    private fun findQuant(ctx: Context, id: String): File? {
        val name = "$id.gguf"
        for (dir in listOf(
            ctx.filesDir,
            ctx.getExternalFilesDir(null),
            ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            File("/sdcard"),
            File("/storage/emulated/0"),
        )) {
            if (dir == null) continue
            val f = File(dir, name)
            if (f.isFile && f.length() > 100_000_000L) return f
        }
        return null
    }

    /** Deterministic 1-5. See the class docstring for the whole rubric. */
    private fun score(reply: String, expect: List<String>): Int {
        val t = reply.trim()
        if (t.isEmpty()) return 1
        // Degenerate: a 24-char window repeated 4+ times.
        val low = t.lowercase()
        if (low.length >= 24) {
            var worst = 0
            val seen = HashMap<String, Int>()
            for (i in 0..low.length - 24) {
                val w = low.substring(i, i + 24)
                val c = (seen[w] ?: 0) + 1
                seen[w] = c
                if (c > worst) worst = c
            }
            if (worst >= 4) return 1
        }
        if (expect.isNotEmpty()) {
            val l = low
            val hits = expect.count { it.lowercase() in l }
            return when {
                hits == expect.size -> 5
                expect.size > 0 && hits * 5 >= expect.size * 3 -> 4
                hits >= 1 -> 3
                else -> 2
            }
        }
        return when {
            t.length >= 25 && t.split(Regex("\\s+")).size >= 5 -> 4
            t.length >= 10 -> 3
            else -> 2
        }
    }

    private data class Result(val scores: List<Int>) {
        val n: Int get() = scores.size
        val mean: Double get() = scores.sum().toDouble() / n
        /** Sample sd; 95% interval via the normal approximation, n=50. */
        val ci95: Double
            get() {
                if (n < 2) return 0.0
                val m = mean
                val v = scores.sumOf { (it - m) * (it - m) } / (n - 1)
                return 1.96 * sqrt(v / n)
            }
    }

    private fun runAll(ctx: Context, quantId: String, budget: Int): Result {
        val f = findQuant(ctx, quantId)
        if (f == null) {
            // NOT a skip. A partial comparison is worse than none: it would let a
            // default be switched on one quantisation's numbers alone.
            throw AssertionError(
                "$quantId is not on the device. The quality decision compares two " +
                    "quantisations and a one-sided comparison is not a decision."
            )
        }
        GsNativeLoader.release()
        val ok = GsNativeLoader.isLibraryLoaded() &&
            GsNative.initTuned(f.absolutePath, 2048, 4, -1, 0, 0, -1)
        if (!ok) throw AssertionError("initTuned failed for $quantId at ${f.absolutePath}")

        val out = ArrayList<Int>(prompts.size)
        val perPrompt = ArrayList<String>(prompts.size)
        for ((i, q) in prompts.withIndex()) {
            val reply = try {
                GsNative.chatWithBudget(q.prompt, budget)
            } catch (t: Throwable) {
                "<<threw ${t::class.java.simpleName}: ${t.message}>>"
            }
            val s = score(reply, q.expect)
            out.add(s)
            perPrompt.add(
                "  [$i] score=$s ${if (q.expect.isEmpty()) "(open)" else q.expect.toString()}" +
                    " reply=${reply.replace(Regex("\\s+"), " ").take(110)}"
            )
        }
        println("=== QUANT50 $quantId (${f.length()} bytes) ===")
        perPrompt.forEach { println(it) }
        val r = Result(out)
        println(
            "QUANT50 $quantId n=${r.n} mean=${"%.4f".format(r.mean)} " +
                "ci95=+/-${"%.4f".format(r.ci95)} dist=${out.groupingBy { it }.eachCount()}"
        )
        return r
    }

    @Test
    fun the_fifty_prompt_quality_comparison_between_q4_0_and_q4_k_m() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val budget = 32

        val sweepIds = ModelCatalog.QUANT_SWEEP.map { it.id }
        val q4_0 = sweepIds.firstOrNull { it.endsWith("q4_0") }
        val q4km = sweepIds.firstOrNull { it.endsWith("q4_k_m") }
        assertTrue("QUANT_SWEEP has no q4_0 entry: $sweepIds", q4_0 != null)
        assertTrue("QUANT_SWEEP has no q4_k_m entry: $sweepIds", q4km != null)

        // Interleaved order is deliberately NOT used: the engine keeps one
        // process-wide context, so each quantisation is loaded once and run to
        // completion. Ordering is therefore fixed and stated, not randomised.
        val a = runAll(ctx, q4_0!!, budget)
        val b = runAll(ctx, q4km!!, budget)

        val delta = a.mean - b.mean
        println("=== QUANT50 DECISION ===")
        println("  prompts              : ${prompts.size}")
        println("  q4_0   mean +/- ci95 : ${"%.4f".format(a.mean)} +/- ${"%.4f".format(a.ci95)}")
        println("  q4_k_m mean +/- ci95 : ${"%.4f".format(b.mean)} +/- ${"%.4f".format(b.ci95)}")
        println("  delta (q4_0 - q4_k_m): ${"%.4f".format(delta)}")
        val within = abs(delta) <= 0.3
        println("  |delta| <= 0.3       : $within")
        println(
            if (within)
                "  VERDICT: Q4_0 is within 0.3 points of the shipped Q4_K_M, so the" +
                    " throughput argument may be taken as decisive and the default may" +
                    " switch to Q4_0."
            else
                "  VERDICT: Q4_0 is NOT within 0.3 points of Q4_K_M" +
                    (if (delta < 0) " and is WORSE on this rubric" else " and is better") +
                    ", so the measured throughput gain is not free and Q4_K_M stays."
        )

        // The measurement itself is load-bearing; the verdict is NOT asserted here
        // on purpose. This test's job is to produce two numbers that a decision can
        // be made from, and hard-coding the verdict would make a future
        // regression look like a passing test. The decision is recorded in
        // RESULTS-mobile-concurrency.md by a human reading this output.
        assertEquals(prompts.size, a.n)
        assertEquals(prompts.size, b.n)
    }
}