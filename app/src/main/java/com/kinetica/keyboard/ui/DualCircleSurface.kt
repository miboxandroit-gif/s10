package com.kinetica.keyboard.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import com.kinetica.keyboard.engine.Alphabet
import com.kinetica.keyboard.engine.KeyboardGeometry
import com.kinetica.keyboard.layout.Key
import com.kinetica.keyboard.layout.KeyType
import com.kinetica.keyboard.layout.KeyboardLayout
import kotlin.math.max

/**
 * The optional Dual Circle UI surface.
 *
 * This class owns the radial surface's geometry, key-code mapping, hit testing and drawing. The
 * upstream KeyboardView remains responsible for gesture routing and for deciding whether this
 * surface is enabled at all, which keeps the Kinetica engine untouched.
 */
class DualCircleSurface(private val density: Float) {
    private val layout = DualCircleLayout()
    private val letterKeyIndex = IntArray(Alphabet.MAX_SIZE) { -1 }
    private val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var leftGradient: Shader? = null
    private var rightGradient: Shader? = null
    private var leftBaseColor = 0xFF2456A6.toInt()
    private var rightBaseColor = 0xFFA93664.toInt()
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val specialPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val landmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val landmarkDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val previewHubPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val leftPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rightPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressedOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private data class RadialSymbol(
        val keyIndex: Int,
        val path: android.graphics.Path,
        val bounds: RectF,
        val cx: Float,
        val cy: Float,
        val inner: Float,
        val outer: Float,
        val start: Float,
        val end: Float,
    )
    private val radialSymbols = ArrayList<RadialSymbol>()
    private var radialSymbolMode = false
    private val previewByPointer = HashMap<Int, Int>()

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private var radius = 0f
    private var cornerPx = 7f * density
    private var layoutActive = false

    fun applyTheme(theme: KeyboardTheme) {
        hubPaint.color = theme.keySpecial
        outlinePaint.color = theme.keyText
        pressedPaint.color = theme.keyPressed
        specialPaint.color = theme.keySpecial
        labelPaint.color = theme.keyText
        leftBaseColor = theme.accent
        rightBaseColor = theme.keySpecial
        leftPaint.color = leftBaseColor
        rightPaint.color = rightBaseColor
        leftPressedPaint.color = theme.keyPressed
        rightPressedPaint.color = theme.accent
        landmarkPaint.color = theme.keyText
        landmarkPaint.alpha = 185
        landmarkPaint.strokeWidth = max(1.2f, density * 1.5f)
        landmarkDotPaint.color = theme.keyText
        landmarkDotPaint.alpha = 210
        previewPaint.color = theme.keyPressed
        previewPaint.alpha = 135
        previewHubPaint.color = theme.keyPressed
        previewHubPaint.alpha = 235
        pressedOutlinePaint.color = theme.keyText
        pressedOutlinePaint.alpha = 185
        pressedOutlinePaint.strokeWidth = max(1.5f, density * 2f)
        cornerPx = 7f * density
    }

    fun isActive(layout: KeyboardLayout?): Boolean =
        layout != null && layoutActive && layout.alphabet in setOf(Alphabet.LATIN, Alphabet.TURKISH)

    fun rebuild(keyboard: KeyboardLayout, width: Float, height: Float, isLandscape: Boolean, sizePct: Int = 100, spreadPct: Int = 100, layoutStyle: String = "optimized"): DualCircleLayout.Config {
        val cfg = layout.rebuild(width, height, keyboard.keys, density, isLandscape, sizePct, spreadPct, layoutStyle)
        java.util.Arrays.fill(letterKeyIndex, -1)
        for (i in keyboard.keys.indices) {
            val key = keyboard.keys[i]
            if (key.isLetter && key.letterCode in letterKeyIndex.indices) {
                // letterCode is an Alphabet-local index (0..n-1), never a Unicode code point.
                letterKeyIndex[key.letterCode] = i
            }
        }
        radius = cfg.radius
        leftGradient = SweepGradient(
            cfg.leftCx, cfg.cy,
            intArrayOf(leftBaseColor, lighten(leftBaseColor, 0.32f), leftBaseColor, leftBaseColor),
            null,
        )
        rightGradient = SweepGradient(
            cfg.rightCx, cfg.cy,
            intArrayOf(rightBaseColor, lighten(rightBaseColor, 0.32f), rightBaseColor, rightBaseColor),
            null,
        )
        radialSymbols.clear()
        radialSymbolMode = keyboard.keys.any { it.type == KeyType.MODE_ALPHA } &&
            keyboard.keys.count { it.type == KeyType.CHAR && !it.isLetter && it.output.length == 1 } >= 8
        if (radialSymbolMode) buildRadialSymbols(keyboard, cfg)
        previewByPointer.clear()
        layoutActive = layout.letterCodes().isNotEmpty() || radialSymbols.isNotEmpty()
        return cfg
    }

    fun buildGeometry(keyboard: KeyboardLayout, cfg: DualCircleLayout.Config): KeyboardGeometry? {
        val centers = HashMap<Int, Pair<Float, Float>>()
        if (!radialSymbolMode) for (code in layout.letterCodes()) {
            val x = layout.centerX(code) ?: continue
            val y = layout.centerY(code) ?: continue
            centers[code] = x to y
        }
        if (radialSymbolMode || centers.isEmpty()) return null
        val keyWidth = max(1f, cfg.radius * 0.42f)
        return KeyboardGeometry.fromCustomPx(
            keyWidthPx = keyWidth,
            midlinePx = (cfg.leftCx + cfg.rightCx) * 0.5f,
            centersPx = centers,
            alphabet = keyboard.alphabet,
            keyAtPx = { x, y ->
                when (val hit = layout.hit(x, y)) {
                    is DualCircleLayout.Hit.Letter -> hit.code
                    else -> -1
                }
            },
            insideInflatedPx = { x, y, code, inflate ->
                layout.containsForCode(x, y, code, inflate)
            },
        )
    }

    fun keyIndexAt(keyboard: KeyboardLayout, x: Float, y: Float): Int {
        radialSymbolHit(x, y)?.let { return it.keyIndex }
        return when (val hit = layout.hit(x, y)) {
            is DualCircleLayout.Hit.Letter -> letterKeyIndex.getOrNull(hit.code) ?: -1
            is DualCircleLayout.Hit.Hub -> {
                val type = if (hit.side == DualCircleLayout.Side.LEFT) KeyType.SPACE else KeyType.BACKSPACE
                keyboard.keys.indexOfFirst { it.type == type }
            }
            is DualCircleLayout.Hit.Special -> hit.keyIndex
            DualCircleLayout.Hit.None -> -1
        }
    }

    fun rectForKey(key: Key, keyIndex: Int): RectF {
        radialSymbols.firstOrNull { it.keyIndex == keyIndex }?.let { return RectF(it.bounds) }
        return layout.rectForKey(key, keyIndex)
    }

    fun hubRect(side: DualCircleLayout.Side): RectF = layout.hubRect(side)
    fun specialRect(keyIndex: Int): RectF? = layout.specialRect(keyIndex)

    /** Install/remove a delayed live letter preview for a specific pointer. */
    fun setPreviewKey(pointerId: Int, keyIndex: Int): Boolean {
        if (previewByPointer[pointerId] == keyIndex) return false
        if (keyIndex < 0) previewByPointer.remove(pointerId) else previewByPointer[pointerId] = keyIndex
        return true
    }

    fun clearPreview(pointerId: Int): Boolean = setPreviewKey(pointerId, -1)

    fun clearAllPreviews(): Boolean {
        if (previewByPointer.isEmpty()) return false
        previewByPointer.clear()
        return true
    }

    private fun drawLandmarks(canvas: Canvas, hub: RectF, radius: Float) {
        val cx = hub.centerX()
        val cy = hub.centerY()
        val r = radius * 0.985f
        val inner = radius * 0.84f
        val markerRadius = max(6f * density, radius * 0.022f)
        val points = floatArrayOf(-90f, 0f, 90f, 180f)
        for (deg in points) {
            val a = Math.toRadians(deg.toDouble())
            val cosA = kotlin.math.cos(a).toFloat()
            val sinA = kotlin.math.sin(a).toFloat()
            canvas.drawLine(
                cx + inner * cosA,
                cy + inner * sinA,
                cx + r * cosA,
                cy + r * sinA,
                landmarkPaint,
            )
            canvas.drawCircle(
                cx + (r + markerRadius * 0.18f) * cosA,
                cy + (r + markerRadius * 0.18f) * sinA,
                markerRadius * 0.34f,
                landmarkDotPaint,
            )
        }
    }

    private fun labelScale(label: String, alphabet: Alphabet): Float {
        val c = label.firstOrNull()?.lowercaseChar() ?: return 1f
        val common = if (alphabet == Alphabet.TURKISH) {
            "aeinrkldmtuyosşç"
        } else {
            "etaoinsrhlcdum"
        }
        return when {
            c in common.take(5) -> 1.09f
            c in common.take(11) -> 1.04f
            c in common.take(16) -> 1.0f
            else -> 0.94f
        }
    }

    fun pathForKey(key: Key): android.graphics.Path? = layout.pathForKey(key)
    fun centerX(code: Int): Float? = layout.centerX(code)
    fun centerY(code: Int): Float? = layout.centerY(code)
    fun segment(code: Int): DualCircleLayout.Segment? = layout.segment(code)
    fun letterCodes(): IntArray = layout.letterCodes()
    fun radius(): Float = radius
    fun layoutGeometry(): DualCircleLayout = layout

    fun drawStatic(
        canvas: Canvas,
        keyboard: KeyboardLayout,
        uppercase: Boolean,
        uppercaseLabel: (String) -> String,
        enterLabel: String?,
        spaceLabel: String,
        deleteLabel: String,
    ) {
        for (code in layout.letterCodes()) {
            val seg = layout.segment(code) ?: continue
            val keyIdx = letterKeyIndex.getOrNull(code) ?: -1
            if (keyIdx !in keyboard.keys.indices) continue
            val key = keyboard.keys[keyIdx]
            val fill = if (seg.side == DualCircleLayout.Side.LEFT) leftPaint else rightPaint
            fill.shader = if (seg.side == DualCircleLayout.Side.LEFT) leftGradient else rightGradient
            fill.alpha = 238
            canvas.drawPath(seg.path, fill)
            canvas.drawPath(seg.path, outlinePaint)
            val centerX = layout.centerX(code) ?: seg.bounds.centerX()
            val centerY = layout.centerY(code) ?: seg.bounds.centerY()
            val main = if (uppercase) uppercaseLabel(key.label) else key.label
            labelPaint.textSize = max(12f * density, seg.bounds.height() * 0.26f * labelScale(main, keyboard.alphabet))
            canvas.drawText(
                main,
                centerX,
                centerY - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                labelPaint,
            )
        }

        if (radialSymbolMode) {
            for (s in radialSymbols) {
                val fill = if (s.cx < canvas.width * 0.5f) leftPaint else rightPaint
                fill.shader = if (s.cx < canvas.width * 0.5f) leftGradient else rightGradient
                fill.alpha = 238
                canvas.drawPath(s.path, fill)
                canvas.drawPath(s.path, outlinePaint)
                val k = keyboard.keys.getOrNull(s.keyIndex) ?: continue
                labelPaint.textSize = max(13f * density, s.bounds.height() * 0.27f)
                canvas.drawText(
                    k.label,
                    s.bounds.centerX(),
                    s.bounds.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                    labelPaint,
                )
            }
        }
        drawLandmarks(canvas, layout.hubRect(DualCircleLayout.Side.LEFT), layout.radius())
        drawLandmarks(canvas, layout.hubRect(DualCircleLayout.Side.RIGHT), layout.radius())

        val leftHub = layout.hubRect(DualCircleLayout.Side.LEFT)
        val rightHub = layout.hubRect(DualCircleLayout.Side.RIGHT)
        canvas.drawCircle(leftHub.centerX(), leftHub.centerY(), leftHub.width() * 0.5f, hubPaint)
        canvas.drawCircle(rightHub.centerX(), rightHub.centerY(), rightHub.width() * 0.5f, hubPaint)
        labelPaint.textSize = leftHub.height() * 0.22f
        canvas.drawText(
            spaceLabel,
            leftHub.centerX(),
            leftHub.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f,
            labelPaint,
        )
        canvas.drawText(
            deleteLabel,
            rightHub.centerX(),
            rightHub.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f,
            labelPaint,
        )
        labelPaint.textSize = max(9f * density, rightHub.height() * 0.13f)
        canvas.drawText("↔", leftHub.centerX(), leftHub.bottom - 7f * density, labelPaint)
        canvas.drawText("↔", rightHub.centerX(), rightHub.bottom - 7f * density, labelPaint)

        for ((keyIdx, r) in layout.allSpecialRects()) {
            if (keyIdx !in keyboard.keys.indices) continue
            val key = keyboard.keys[keyIdx]
            canvas.drawRoundRect(r, cornerPx, cornerPx, specialPaint)
            val label = when (key.type) {
                KeyType.SHIFT -> "⇧"
                KeyType.MODE_SYMBOLS, KeyType.MODE_SYMBOLS2 -> "123"
                KeyType.ENTER -> enterLabel ?: "↵"
                else -> key.label
            }
            labelPaint.textSize = r.height() * 0.40f
            canvas.drawText(
                label,
                r.centerX(),
                r.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                labelPaint,
            )
        }
    }

    fun drawPressed(
        canvas: Canvas,
        keyboard: KeyboardLayout,
        key: Key,
        keyIndex: Int,
        uppercase: Boolean,
        uppercaseLabel: (String) -> String,
        spaceLabel: String,
        deleteLabel: String,
    ) {
        if (radialSymbolMode && !key.isLetter && key.type == KeyType.CHAR) {
            radialSymbols.firstOrNull { it.keyIndex == keyIndex }?.let { s ->
                canvas.drawPath(s.path, if (s.cx < canvas.width * 0.5f) leftPressedPaint else rightPressedPaint)
                canvas.drawPath(s.path, pressedOutlinePaint)
                labelPaint.textSize = max(15f * density, s.bounds.height() * 0.28f)
                canvas.drawText(
                    if (uppercase) uppercaseLabel(key.label) else key.label,
                    s.bounds.centerX(),
                    s.bounds.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                    labelPaint,
                )
            }
            return
        }
        if (key.isLetter) {
            layout.pathForKey(key)?.let { path ->
                val pressPaint = if (layout.segment(key.letterCode)?.side == DualCircleLayout.Side.LEFT) {
                    leftPressedPaint
                } else {
                    rightPressedPaint
                }
                canvas.drawPath(path, pressPaint)
                canvas.drawPath(path, pressedOutlinePaint)
                val centerX = layout.centerX(key.letterCode) ?: 0f
                val centerY = layout.centerY(key.letterCode) ?: 0f
                val label = if (uppercase) uppercaseLabel(key.label) else key.label
                labelPaint.textSize = max(15f * density, layout.radius() * 0.10f * labelScale(label, keyboard.alphabet))
                canvas.drawText(
                    label,
                    centerX,
                    centerY - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                    labelPaint,
                )
            }
            return
        }
        when (key.type) {
            KeyType.SPACE -> {
                val r = layout.hubRect(DualCircleLayout.Side.LEFT)
                canvas.drawCircle(r.centerX(), r.centerY(), r.width() * 0.5f, pressedPaint)
                labelPaint.textSize = r.height() * 0.22f
                canvas.drawText(
                    spaceLabel,
                    r.centerX(),
                    r.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                    labelPaint,
                )
            }
            KeyType.BACKSPACE -> {
                val r = layout.hubRect(DualCircleLayout.Side.RIGHT)
                canvas.drawCircle(r.centerX(), r.centerY(), r.width() * 0.5f, pressedPaint)
                labelPaint.textSize = r.height() * 0.22f
                canvas.drawText(
                    deleteLabel,
                    r.centerX(),
                    r.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                    labelPaint,
                )
            }
            else -> layout.specialRect(keyIndex)?.let { r ->
                canvas.drawRoundRect(r, cornerPx, cornerPx, pressedPaint)
            }
        }
    }

    /**
     * Dynamic letter affordance. KeyboardView only installs this after the same letter remains
     * under the finger for a short debounce interval, so fast swipe paths do not flicker.
     */
    fun drawOverlay(
        canvas: Canvas,
        keyboard: KeyboardLayout,
        uppercase: Boolean,
        uppercaseLabel: (String) -> String,
    ) {
        for ((_, keyIndex) in previewByPointer) {
            val key = keyboard.keys.getOrNull(keyIndex) ?: continue
            if (!key.isLetter) continue
            val seg = layout.segment(key.letterCode) ?: continue
            canvas.drawPath(seg.path, previewPaint)
            canvas.drawPath(seg.path, pressedOutlinePaint)

            val hub = layout.hubRect(seg.side)
            canvas.drawCircle(
                hub.centerX(),
                hub.centerY(),
                hub.width() * 0.5f,
                previewHubPaint,
            )
            val text = if (uppercase) uppercaseLabel(key.label) else key.label
            labelPaint.textSize = max(25f * density, hub.height() * 0.55f)
            canvas.drawText(
                text,
                hub.centerX(),
                hub.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                labelPaint,
            )
        }
    }

    private fun lighten(color: Int, amount: Float): Int {
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)
        fun l(v: Int) = (v + (255 - v) * amount.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return android.graphics.Color.rgb(l(r), l(g), l(b))
    }

    private fun buildRadialSymbols(keyboard: KeyboardLayout, cfg: DualCircleLayout.Config) {
        radialSymbols.clear()
        val chars = keyboard.keys.mapIndexedNotNull { index, key ->
            if (key.type == KeyType.CHAR && !key.isLetter && key.output.length == 1) {
                index to key
            } else null
        }
        if (chars.isEmpty()) return

        val digits = chars.filter { it.second.output.first().isDigit() }
        val symbols = chars.filterNot { it.second.output.first().isDigit() }

        if (digits.isNotEmpty()) {
            addRadialGroup(digits, DualCircleLayout.Side.LEFT, cfg)
            addRadialGroup(symbols, DualCircleLayout.Side.RIGHT, cfg)
        } else {
            val midpoint = (chars.size + 1) / 2
            addRadialGroup(chars.take(midpoint), DualCircleLayout.Side.LEFT, cfg)
            addRadialGroup(chars.drop(midpoint), DualCircleLayout.Side.RIGHT, cfg)
        }
    }

    private fun addRadialGroup(
        keys: List<Pair<Int, Key>>,
        side: DualCircleLayout.Side,
        cfg: DualCircleLayout.Config,
    ) {
        if (keys.isEmpty()) return
        val innerCount = (keys.size + 1) / 2
        val groups = listOf(keys.take(innerCount), keys.drop(innerCount))
        for (ring in 0..1) {
            val ringKeys = groups[ring]
            if (ringKeys.isEmpty()) continue
            val count = ringKeys.size
            val step = 2f * Math.PI.toFloat() / count
            val inner = if (ring == 0) cfg.innerInner else cfg.outerInner
            val outer = if (ring == 0) cfg.innerOuter else cfg.outerOuter
            val cx = if (side == DualCircleLayout.Side.LEFT) cfg.leftCx else cfg.rightCx
            for (i in ringKeys.indices) {
                val start = -Math.PI.toFloat() / 2f + Math.toRadians(DualCircleMath.PHASE_DEGREES.toDouble()).toFloat() +
                    i * step + cfg.gapRad / 2f
                val end = -Math.PI.toFloat() / 2f + Math.toRadians(DualCircleMath.PHASE_DEGREES.toDouble()).toFloat() +
                    (i + 1) * step - cfg.gapRad / 2f
                val path = radialPath(cx, cfg.cy, inner, outer, start, end)
                radialSymbols += RadialSymbol(
                    ringKeys[i].first,
                    path,
                    RectF().also { path.computeBounds(it, true) },
                    cx, cfg.cy, inner, outer, start, end,
                )
            }
        }
    }

    private fun radialPath(cx: Float, cy: Float, inner: Float, outer: Float, start: Float, end: Float): android.graphics.Path {
        val p = android.graphics.Path()
        fun point(r: Float, a: Float): Pair<Float, Float> =
            cx + r * kotlin.math.cos(a) to cy + r * kotlin.math.sin(a)
        val sweep = Math.toDegrees((end - start).toDouble()).toFloat()
        val (sx, sy) = point(outer, start)
        p.moveTo(sx, sy)
        p.arcTo(RectF(cx - outer, cy - outer, cx + outer, cy + outer), Math.toDegrees(start.toDouble()).toFloat(), sweep, false)
        val (ex, ey) = point(inner, end)
        p.lineTo(ex, ey)
        p.arcTo(RectF(cx - inner, cy - inner, cx + inner, cy + inner), Math.toDegrees(end.toDouble()).toFloat(), -sweep, false)
        p.close()
        return p
    }

    private fun radialSymbolHit(x: Float, y: Float): RadialSymbol? {
        if (!radialSymbolMode) return null
        return radialSymbols.minByOrNull { s ->
            val dx = x - s.bounds.centerX()
            val dy = y - s.bounds.centerY()
            dx * dx + dy * dy
        }?.takeIf { s ->
            val cx = s.cx
            val d = DualCircleMath.distance(x, y, cx, s.cy)
            if (d < s.inner - radius * 0.045f || d > s.outer + radius * 0.045f) return@takeIf false
            val angle = DualCircleMath.angleAt(x, y, cx, s.cy)
            DualCircleMath.containsAngle(angle, s.start, s.end, radius * 0.045f / max(d, 1f))
        }
    }

}
