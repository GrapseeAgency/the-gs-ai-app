package com.grapsee.gsai.ui.chat.content

// ---------------------------------------------------------------------------
// Interactive native widgets (0.78.0 "interactive chat"): gauge, quiz, form,
// carousel, action cards, follow-up prompts. These are real controls, not
// pictures of controls — taps change state inside the transcript.
// ---------------------------------------------------------------------------

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.grapsee.gsai.ui.theme.GsTheme
import androidx.compose.foundation.Image

// --- gauge -------------------------------------------------------------------

@Composable
internal fun GaugeWidgetView(w: GaugeWidget) {
    WidgetCardShell(w.title.ifEmpty { "Gauges" }, "gauge") {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            w.items.take(6).forEachIndexed { i, item ->
                GaugeArc(item, seriesColor(i))
            }
        }
    }
}

@Composable
private fun GaugeArc(item: GaugeItem, color: Color) {
    var animate by remember(item.label) { mutableStateOf(false) }
    LaunchedEffect(item.label) { animate = true }
    val safeMax = if (item.max > 0f) item.max else 100f
    val frac = (item.value / safeMax).coerceIn(0f, 1f)
    val progress by animateFloatAsState(if (animate) frac else 0f, tween(900), label = "gauge")
    val trackColor = GsTheme.colors.raisedSurface
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(84.dp)) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2f
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke)
            )
            drawArc(
                color = color,
                startAngle = 135f,
                sweepAngle = 270f * progress,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke)
            )
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(
                if (progress >= 0.99f) "${formatChartValue(item.value)}${item.unit}" else "",
                style = MaterialTheme.typography.titleLarge,
                color = GsTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(item.label, style = MaterialTheme.typography.labelMedium, color = GsTheme.colors.textSecondary)
        }
    }
}

// --- quiz --------------------------------------------------------------------

@Composable
internal fun QuizWidgetView(w: QuizWidget) {
    val questions = w.questions.take(10).filter { it.q.isNotBlank() && it.options.isNotEmpty() }
    if (questions.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(emptySet<Int>()) }
    val answers = remember { mutableStateMapOf<Int, Int>() }
    var finished by remember { mutableStateOf(false) }
    val c = GsTheme.colors

    WidgetCardShell(w.title.ifEmpty { "Quiz" }, "quiz") {
        Column(Modifier.padding(top = 8.dp)) {
            if (finished) {
                val correct = questions.indices.count { qi ->
                    revealed.contains(qi) && answers[qi] == questions[qi].answer
                }
                Text(
                    "Score: $correct / ${questions.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                questions.forEachIndexed { qi, q ->
                    Text(
                        "${if (revealed.contains(qi) && answers[qi] == q.answer) "✓" else "•"} ${q.q}",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                TextButton(onClick = {
                    answers.clear(); revealed = emptySet(); index = 0; finished = false
                }) { Text("Restart") }
                return@Column
            }
            val q = questions[index]
            Text(
                "Q${index + 1}/${questions.size}",
                style = MaterialTheme.typography.labelSmall,
                color = c.textTertiary
            )
            Text(
                q.q,
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            val isRevealed = revealed.contains(index)
            q.options.take(6).forEachIndexed { oi, opt ->
                val chosen = answers[index] == oi
                val isAnswer = oi == q.answer
                val bg = when {
                    isRevealed && isAnswer -> c.success.copy(alpha = 0.18f)
                    isRevealed && chosen -> c.error.copy(alpha = 0.18f)
                    else -> c.surface
                }
                val border = when {
                    isRevealed && isAnswer -> c.success
                    isRevealed && chosen -> c.error
                    else -> c.divider
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .clickable(enabled = !isRevealed) {
                            answers[index] = oi
                            revealed = revealed + index
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${'A' + oi}",
                        style = MaterialTheme.typography.labelMedium,
                        color = border,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        opt,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textPrimary,
                        modifier = Modifier.padding(start = 10.dp)
                    )
                    if (isRevealed && isAnswer) {
                        Text(" ✓", color = c.success, modifier = Modifier.padding(start = 6.dp))
                    }
                    if (isRevealed && chosen && !isAnswer) {
                        Text(" ✗", color = c.error, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            if (isRevealed && q.explain.isNotBlank()) {
                Text(
                    q.explain,
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (isRevealed) {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        if (index < questions.lastIndex) index += 1 else finished = true
                    }) {
                        Text(if (index < questions.lastIndex) "Next" else "Finish")
                    }
                }
            }
        }
    }
}

// --- form --------------------------------------------------------------------

@Composable
internal fun FormWidgetView(w: FormWidget, onSendPrompt: ((String) -> Unit)?) {
    val fields = w.fields.take(12).filter { it.label.isNotBlank() }
    val c = GsTheme.colors
    val values = remember { mutableStateMapOf<Int, String>() }
    val checks = remember { mutableStateMapOf<Int, Set<String>>() }
    val sliders = remember { mutableStateMapOf<Int, Float>() }
    var error by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf<String?>(null) }

    WidgetCardShell(w.title.ifEmpty { "Form" }, "form") {
        Column(Modifier.padding(top = 8.dp)) {
            fields.forEachIndexed { i, f ->
                when (f.type) {
                    "textarea" -> OutlinedTextField(
                        value = values[i] ?: "",
                        onValueChange = { values[i] = it },
                        label = { Text(if (f.required) "${f.label} *" else f.label) },
                        placeholder = { Text(f.placeholder) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    )
                    "select" -> {
                        var expanded by remember { mutableStateOf(false) }
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(
                                if (f.required) "${f.label} *" else f.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = c.textSecondary
                            )
                            Box {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(c.surface)
                                        .clickable { expanded = true }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        values[i] ?: f.placeholder.ifEmpty { "Select…" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (values[i] == null) c.textTertiary else c.textPrimary
                                    )
                                    Text("▾", color = c.textTertiary)
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    f.options.take(12).forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt) },
                                            onClick = { values[i] = opt; expanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    "radio" -> Column(Modifier.padding(vertical = 6.dp)) {
                        Text(
                            if (f.required) "${f.label} *" else f.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textSecondary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        f.options.take(8).forEach { opt ->
                            Row(
                                Modifier.fillMaxWidth().clickable { values[i] = opt }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = values[i] == opt, onClick = { values[i] = opt })
                                Text(opt, style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
                            }
                        }
                    }
                    "checkbox" -> Column(Modifier.padding(vertical = 6.dp)) {
                        Text(
                            f.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textSecondary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        val current = checks[i] ?: emptySet()
                        f.options.take(8).forEach { opt ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    checks[i] = if (opt in current) current - opt else current + opt
                                }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = opt in current, onCheckedChange = null)
                                Text(opt, style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
                            }
                        }
                    }
                    "segmented" -> Column(Modifier.padding(vertical = 6.dp)) {
                        Text(
                            if (f.required) "${f.label} *" else f.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(c.surface).padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            val current = values[i] ?: f.options.firstOrNull()
                            f.options.take(5).forEach { opt ->
                                val selected = current == opt
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) c.accent else Color.Transparent)
                                        .clickable { values[i] = opt }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        opt,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) c.onAccent else c.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    "slider" -> {
                        val v = sliders[i] ?: f.value
                        val lo = minOf(f.min, f.max)
                        val hi = maxOf(f.min, f.max).let { if (it == lo) lo + 1f else it }
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(f.label, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                                Text(
                                    "${formatChartValue(v)}${f.unit}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = c.textPrimary
                                )
                            }
                            Slider(
                                value = v.coerceIn(lo, hi),
                                onValueChange = { sliders[i] = it },
                                valueRange = lo..hi
                            )
                        }
                    }
                    "toggle" -> Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(f.label, style = MaterialTheme.typography.labelMedium, color = c.textPrimary)
                        Switch(
                            checked = values[i] == "on",
                            onCheckedChange = { values[i] = if (it) "on" else "off" }
                        )
                    }
                    "date" -> OutlinedTextField(
                        value = values[i] ?: "",
                        onValueChange = { values[i] = it },
                        label = { Text(if (f.required) "${f.label} *" else f.label) },
                        placeholder = { Text("YYYY-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    )
                    "time", "email", "url" -> OutlinedTextField(
                        value = values[i] ?: "",
                        onValueChange = { values[i] = it },
                        label = { Text(if (f.required) "${f.label} *" else f.label) },
                        placeholder = { Text(when (f.type) { "time" -> "HH:MM"; "email" -> "name@example.com"; else -> "https://…" }) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = when (f.type) {
                                // Compose has no Time keyboard; the date/time
                                // pickers above already constrain the shape, so
                                // a numeric pad is the closest honest hint.
                                "time" -> KeyboardType.Number
                                "email" -> KeyboardType.Email
                                else -> KeyboardType.Uri
                            }
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    )
                    "number" -> Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (f.required) "${f.label} *" else f.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            val cur = values[i]?.toFloatOrNull() ?: f.value
                            values[i] = ((cur - 1f).coerceIn(f.min, f.max)).toInt().toString()
                        }) { Text("−") }
                        Text(
                            "${values[i]?.toFloatOrNull() ?: f.value}${f.unit}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary
                        )
                        TextButton(onClick = {
                            val cur = values[i]?.toFloatOrNull() ?: f.value
                            values[i] = ((cur + 1f).coerceIn(f.min, f.max)).toInt().toString()
                        }) { Text("+") }
                    }
                    "rating" -> Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (f.required) "${f.label} *" else f.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        val maxStars = f.max.toInt().coerceIn(1, 10)
                        val current = (values[i]?.toFloatOrNull() ?: 0f).toInt()
                        Text(
                            "★".repeat(current) + "☆".repeat(maxStars - current),
                            style = MaterialTheme.typography.titleMedium,
                            color = seriesColor(0),
                            modifier = Modifier
                                .clickable {
                                    val next = if (current >= maxStars) 0 else current + 1
                                    values[i] = next.toString()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Text("tap to rate", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
                    }
                    "otp" -> Column(Modifier.padding(vertical = 6.dp)) {
                        Text(
                            if (f.required) "${f.label} *" else f.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textSecondary
                        )
                        val code = values[i] ?: ""
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            repeat(6) { d ->
                                Box(
                                    Modifier
                                        .size(width = 34.dp, height = 44.dp)
                                        .border(
                                            1.5.dp,
                                            if (d < code.length) seriesColor(0) else c.divider,
                                            RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        code.getOrNull(d)?.toString() ?: "",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = c.textPrimary
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = code,
                            onValueChange = { v -> values[i] = v.filter { it.isDigit() }.take(6) },
                            label = { Text("6-digit code") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    }
                    "color" -> Column(Modifier.padding(vertical = 6.dp)) {
                        Text(
                            if (f.required) "${f.label} *" else f.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            listOf("#4C6FFF", "#22C55E", "#F59E0B", "#EF4444", "#A855F7", "#06B6D4", "#EC4899", "#84CC16").forEach { hex ->
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .background(parseHexColor(hex) ?: seriesColor(0), RoundedCornerShape(13.dp))
                                        .border(
                                            width = if (values[i] == hex) 2.dp else 1.dp,
                                            color = if (values[i] == hex) c.textPrimary else c.divider,
                                            shape = RoundedCornerShape(13.dp)
                                        )
                                        .clickable { values[i] = hex }
                                )
                            }
                        }
                        Text(
                            values[i] ?: "pick a colour",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textTertiary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    "file" -> Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .border(1.dp, c.divider, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📎", modifier = Modifier.padding(end = 8.dp))
                        Text(
                            "Attach — ${f.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "file pickers open on the web client",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textTertiary
                        )
                    }
                    else -> OutlinedTextField(
                        value = values[i] ?: "",
                        onValueChange = { values[i] = it },
                        label = { Text(if (f.required) "${f.label} *" else f.label) },
                        placeholder = { Text(f.placeholder) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    )
                }
            }

            if (error.isNotEmpty()) {
                Text(error, style = MaterialTheme.typography.labelMedium, color = c.error, modifier = Modifier.padding(top = 4.dp))
            }

            Button(
                onClick = {
                    val missing = fields.filter { f ->
                        f.required && (values[fields.indexOf(f)].isNullOrBlank())
                    }
                    if (missing.isNotEmpty()) {
                        error = "Required: " + missing.joinToString(", ") { it.label }
                        submitted = null
                    } else {
                        error = ""
                        val lines = fields.mapIndexed { i, f ->
                            when (f.type) {
                                "checkbox" -> "$f.label: ${(checks[i] ?: emptySet()).joinToString(", ").ifEmpty { "—" }}"
                                "slider" -> "$f.label: ${formatChartValue(sliders[i] ?: f.value)}${f.unit}"
                                "toggle" -> "$f.label: ${if (values[i] == "on") "Yes" else "No"}"
                                else -> "$f.label: ${values[i]?.ifBlank { "—" } ?: "—"}"
                            }
                        }
                        submitted = (if (w.title.isNotBlank()) "${w.title}\n" else "") + lines.joinToString("\n")
                    }
                },
                modifier = Modifier.padding(top = 10.dp)
            ) { Text(w.submitLabel.ifBlank { "Submit" }) }

            val summary = submitted
            if (summary != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surface)
                        .padding(12.dp)
                ) {
                    Text("Ready to send", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
                    summary.lines().forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
                    }
                    if (onSendPrompt != null) {
                        Button(
                            onClick = { onSendPrompt(summary) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) { Text("Send as message") }
                    }
                }
            }
        }
    }
}

// --- carousel ----------------------------------------------------------------

@Composable
internal fun CarouselWidgetView(w: CarouselWidget) {
    val items = w.items.take(20).filter { it.image.startsWith("https://") }
    var index by remember { mutableIntStateOf(0) }
    val c = GsTheme.colors
    WidgetCardShell(w.title.ifEmpty { "Gallery" }, "carousel") {
        if (items.isEmpty()) {
            Text("No images", style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.padding(top = 8.dp))
            return@WidgetCardShell
        }
        val safeIndex = index.coerceIn(0, items.lastIndex)
        val state by produceState<ImageState>(
            initialValue = imageCache.get(items[safeIndex].image)?.let { ImageState.Ready(it) } ?: ImageState.Loading,
            key1 = items[safeIndex].image
        ) {
            val bitmap = fetchBitmap(items[safeIndex].image)
            value = if (bitmap != null) ImageState.Ready(bitmap) else ImageState.Failed
        }
        Column(Modifier.padding(top = 8.dp)) {
            Box(contentAlignment = Alignment.Center) {
                when (val current = state) {
                    is ImageState.Loading -> Box(
                        Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(c.raisedSurface),
                        contentAlignment = Alignment.Center
                    ) { Text("…", color = c.textTertiary) }
                    is ImageState.Failed -> Box(
                        Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(c.raisedSurface),
                        contentAlignment = Alignment.Center
                    ) { Text("Image unavailable", style = MaterialTheme.typography.labelMedium, color = c.textTertiary) }
                    is ImageState.Ready -> Image(
                        bitmap = current.bitmap,
                        contentDescription = items[safeIndex].caption,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    )
                }
                if (items.size > 1) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(c.elevatedSurface.copy(alpha = 0.9f))
                                .clickable { index = (safeIndex - 1 + items.size) % items.size },
                            contentAlignment = Alignment.Center
                        ) { Text("‹", style = MaterialTheme.typography.titleMedium, color = c.textPrimary) }
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(c.elevatedSurface.copy(alpha = 0.9f))
                                .clickable { index = (safeIndex + 1) % items.size },
                            contentAlignment = Alignment.Center
                        ) { Text("›", style = MaterialTheme.typography.titleMedium, color = c.textPrimary) }
                    }
                }
            }
            if (items[safeIndex].caption.isNotBlank()) {
                Text(
                    items[safeIndex].caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (items.size > 1) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.indices.take(12).forEach { i ->
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (i == safeIndex) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (i == safeIndex) c.accent else c.divider)
                        )
                    }
                    Text(
                        "${safeIndex + 1}/${items.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textTertiary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

// --- action cards ------------------------------------------------------------

@Composable
internal fun CardsWidgetView(w: CardsWidget, onSendPrompt: ((String) -> Unit)?) {
    val c = GsTheme.colors
    WidgetCardShell(w.title.ifEmpty { "Choices" }, "cards") {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            w.cards.take(8).forEach { card ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface)
                        .then(
                            if (onSendPrompt != null && card.prompt.isNotBlank()) {
                                Modifier.clickable { onSendPrompt(card.prompt) }
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            card.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = c.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (card.subtitle.isNotBlank()) {
                            Text(
                                card.subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = c.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (onSendPrompt != null && card.prompt.isNotBlank()) {
                        Text("→", style = MaterialTheme.typography.titleMedium, color = c.accent)
                    }
                }
            }
        }
    }
}

// --- follow-up prompts -------------------------------------------------------

@Composable
internal fun FollowupsWidgetView(w: FollowupsWidget, onSendPrompt: ((String) -> Unit)?) {
    val c = GsTheme.colors
    WidgetCardShell(w.title.ifEmpty { "Ask next" }, "followups") {
        if (onSendPrompt == null) {
            Text(
                "Tap disabled here",
                style = MaterialTheme.typography.labelMedium,
                color = c.textTertiary,
                modifier = Modifier.padding(top = 8.dp)
            )
            return@WidgetCardShell
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            w.prompts.take(12).forEach { prompt ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(c.surface)
                        .clickable { onSendPrompt(prompt) }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(prompt, style = MaterialTheme.typography.labelMedium, color = c.accent, maxLines = 2)
                }
            }
        }
    }
}
