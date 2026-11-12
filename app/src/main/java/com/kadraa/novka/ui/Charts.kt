package com.kadraa.novka.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max

data class Bucket(val label: String, val earned: Long, val spent: Long, val balance: Long)

data class Slice(val label: String, val value: Long, val colour: Color)

@Composable
fun Legend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (label, colour) ->
            Box(Modifier.size(10.dp).background(colour, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
        }
    }
}

/** Sparse x-axis labels: first, middle and last, so they never collide. */
@Composable
private fun AxisLabels(labels: List<String>) {
    if (labels.isEmpty()) return
    val shown = listOf(labels.first(), labels[labels.size / 2], labels.last()).take(if (labels.size >= 3) 3 else 1)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        shown.forEachIndexed { index, label ->
            Text(
                label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = when {
                    shown.size == 1 || index == 0 -> androidx.compose.ui.text.style.TextAlign.Start
                    index == shown.lastIndex -> androidx.compose.ui.text.style.TextAlign.End
                    else -> androidx.compose.ui.text.style.TextAlign.Center
                },
            )
        }
    }
}

/** Earned and spent side by side for each bucket. Tap a bucket to read its values. */
@Composable
fun EarnedSpentChart(buckets: List<Bucket>, earnedLabel: String, spentLabel: String, format: (Long) -> String) {
    val colors = chartColors()
    var selected by remember(buckets) { mutableStateOf<Int?>(null) }
    val top = max(1L, buckets.maxOfOrNull { max(it.earned, it.spent) } ?: 1L)
    val picked = selected?.let(buckets::getOrNull)
    Column {
        Legend(listOf(earnedLabel to colors.earned, spentLabel to colors.spent))
        Text(
            picked?.let { "${it.label}: $earnedLabel ${format(it.earned)} · $spentLabel ${format(it.spent)}" } ?: format(top),
            style = MaterialTheme.typography.labelMedium, fontWeight = if (picked != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (picked != null) MaterialTheme.colorScheme.onSurface else colors.muted,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )
        Canvas(
            Modifier.fillMaxWidth().height(170.dp)
                .semantics { contentDescription = buckets.joinToString { "${it.label}: $earnedLabel ${format(it.earned)}, $spentLabel ${format(it.spent)}" } }
                .pointerInput(buckets) {
                    detectTapGestures { offset ->
                        if (buckets.isNotEmpty()) {
                            val index = (offset.x / (size.width.toFloat() / buckets.size)).toInt().coerceIn(0, buckets.lastIndex)
                            selected = if (selected == index) null else index
                        }
                    }
                },
        ) {
            val base = size.height - 1.dp.toPx()
            for (i in 0..2) {
                val y = base * i / 2f
                drawLine(colors.grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            if (buckets.isEmpty()) return@Canvas
            val slot = size.width / buckets.size
            val gap = 2.dp.toPx()
            val bar = ((slot - gap * 3) / 2f).coerceIn(1.dp.toPx(), 14.dp.toPx())
            val radius = CornerRadius(minOf(4.dp.toPx(), bar / 2))
            buckets.forEachIndexed { index, bucket ->
                val centre = slot * index + slot / 2
                val dim = selected != null && selected != index
                fun draw(value: Long, left: Float, colour: Color) {
                    if (value <= 0) return
                    val height = max(2.dp.toPx(), base * value / top.toFloat())
                    val path = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(left, base - height, left + bar, base, topLeftCornerRadius = radius, topRightCornerRadius = radius)
                        )
                    }
                    drawPath(path, if (dim) colour.copy(alpha = 0.35f) else colour)
                }
                draw(bucket.earned, centre - bar - gap / 2, colors.earned)
                draw(bucket.spent, centre + gap / 2, colors.spent)
            }
        }
        AxisLabels(buckets.map { it.label })
    }
}

/** One line: total balance at the end of each bucket. Tap to read a point. */
@Composable
fun BalanceChart(buckets: List<Bucket>, format: (Long) -> String) {
    val colors = chartColors()
    val surface = MaterialTheme.colorScheme.surface
    var selected by remember(buckets) { mutableStateOf<Int?>(null) }
    val low = buckets.minOfOrNull { it.balance } ?: 0L
    val high = buckets.maxOfOrNull { it.balance } ?: 1L
    val span = max(1L, high - low).toFloat()
    val picked = selected?.let(buckets::getOrNull) ?: buckets.lastOrNull()
    Column {
        Text(
            picked?.let { "${it.label}: ${format(it.balance)}" }.orEmpty(),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp),
        )
        Canvas(
            Modifier.fillMaxWidth().height(150.dp)
                .semantics { contentDescription = buckets.joinToString { "${it.label}: ${format(it.balance)}" } }
                .pointerInput(buckets) {
                    detectTapGestures { offset ->
                        if (buckets.isNotEmpty()) {
                            val step = size.width.toFloat() / max(1, buckets.size - 1)
                            selected = ((offset.x + step / 2) / step).toInt().coerceIn(0, buckets.lastIndex)
                        }
                    }
                },
        ) {
            val pad = 6.dp.toPx()
            val height = size.height - pad * 2
            for (i in 0..2) {
                val y = pad + height * i / 2f
                drawLine(colors.grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            if (buckets.isEmpty()) return@Canvas
            val step = size.width / max(1, buckets.size - 1)
            fun point(index: Int) = Offset(if (buckets.size == 1) size.width / 2 else step * index, pad + height * (1 - (buckets[index].balance - low) / span))
            val path = Path()
            buckets.indices.forEach { i -> point(i).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
            drawPath(path, colors.line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val mark = selected ?: buckets.lastIndex
            drawCircle(surface, 6.dp.toPx(), point(mark))
            drawCircle(colors.line, 4.dp.toPx(), point(mark))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(format(low), style = MaterialTheme.typography.labelSmall, color = colors.muted, modifier = Modifier.weight(1f))
            Text(format(high), style = MaterialTheme.typography.labelSmall, color = colors.muted)
        }
        AxisLabels(buckets.map { it.label })
    }
}

/** Share of spending per category. The ranked list beside it carries names and amounts. */
@Composable
fun Donut(slices: List<Slice>, centre: String, caption: String, modifier: Modifier = Modifier) {
    val total = max(1L, slices.sumOf { it.value }).toFloat()
    Box(modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(150.dp)) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2
            val gapDegrees = if (slices.size > 1) 2f else 0f
            var start = -90f
            slices.forEach { slice ->
                val sweep = 360f * slice.value / total
                if (sweep > gapDegrees) {
                    drawArc(
                        slice.colour, start + gapDegrees / 2, sweep - gapDegrees, useCenter = false,
                        topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke),
                    )
                }
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
