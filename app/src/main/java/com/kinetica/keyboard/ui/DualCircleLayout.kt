package com.kinetica.keyboard.ui

import android.graphics.Path
import android.graphics.RectF
import com.kinetica.keyboard.layout.Key
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Responsive two-thumb radial board used by the Dual Circle fork.
 *
 * The primary Latin alphabet is split once between the two thumbs. Turkish-specific
 * characters are shown as visual hints on their base keys and remain available through
 * Kinetica's existing alternate/long-press mechanism.
 */
class DualCircleLayout {
    enum class Side { LEFT, RIGHT }

    sealed interface Hit {
        data class Letter(val code: Int) : Hit
        data class Hub(val side: Side) : Hit
        data class Special(val keyIndex: Int) : Hit
        data object None : Hit
    }

    data class Segment(
        val side: Side,
        val ring: Int,
        val index: Int,
        val count: Int,
        val code: Int,
        val path: Path,
        val bounds: RectF,
    )

    data class Config(
        val leftCx: Float,
        val rightCx: Float,
        val cy: Float,
        val radius: Float,
        val hubRadius: Float,
        val innerInner: Float,
        val innerOuter: Float,
        val outerInner: Float,
        val outerOuter: Float,
        val gapRad: Float,
        val specialRects: Map<Int, RectF>,
        val touchInflatePx: Float,
    )

    /** A four-ring partition expressed as characters; converted to alphabet-local codes at rebuild. */
    private data class Partition(
        val leftInner: String,
        val leftOuter: String,
        val rightInner: String,
        val rightOuter: String,
    ) {
        fun codes(alphabet: com.kinetica.keyboard.engine.Alphabet): Array<IntArray> = arrayOf(
            leftInner.map(alphabet::codeOf).toIntArray(),
            leftOuter.map(alphabet::codeOf).toIntArray(),
            rightInner.map(alphabet::codeOf).toIntArray(),
            rightOuter.map(alphabet::codeOf).toIntArray(),
        )
    }

    /** Original Kinetica Latin split retained when Dual Circle is used outside Turkish. */
    private val latinPartition = Partition(
        leftInner = "aeiou",
        leftOuter = "sdrygchv",
        rightInner = "nltkm",
        rightOuter = "bfpzxjqw",
    )

    /** Turkish 29-letter split optimized for balanced two-thumb travel. */
    private val turkishPartition = Partition(
        leftInner = "aeıiou",
        leftOuter = "öüçcfgjp",
        rightInner = "nrlkdm",
        rightOuter = "ytsbzşhvğ",
    )

    /** Turkish QWERTY order, restricted to Kinetica's 29-letter Turkish alphabet. */
    private val turkishQwertyPartition = Partition(
        leftInner = "ertyuı",
        leftOuter = "opğüasdf",
        rightInner = "ghjklş",
        rightOuter = "izcvbnmöç",
    )

    private var activeCodes = IntArray(0)
    private val hintByCode = emptyMap<Int, String>()

    private var config: Config? = null
    private val segmentsByCode = HashMap<Int, Segment>(29)
    private val segments = ArrayList<Segment>(29)
    private val specialRects = HashMap<Int, RectF>()

    fun rebuild(width: Float, height: Float, keys: List<Key>, density: Float, isLandscape: Boolean = height < width, sizePct: Int = 100, spreadPct: Int = 100, layoutStyle: String = "optimized"): Config {
        segments.clear()
        segmentsByCode.clear()
        specialRects.clear()

        val portrait = !isLandscape

        // The circles are the primary thumb targets. In portrait we give them more of the
        // available width; in landscape we push them toward the side bezels so each thumb can
        // work mostly on its own side instead of crossing toward the centre.
        val spread = (spreadPct / 100f).coerceIn(0.85f, 1.15f)
        val size = (sizePct / 100f).coerceIn(0.90f, 1.15f)
        val margin = 8f * density
        val baseCxGap = (if (portrait) width * 0.28f else width * 0.18f) / spread
        // At the largest size, balance the edge clearance against the distance between circles.
        // Without this, centerSpacingLimit stays fixed and the "large" option cannot grow the
        // target despite increasing preferredRadius. Smaller settings keep the original centers.
        val sizeGrowth = ((size - 1f) / 0.15f).coerceIn(0f, 1f)
        val maxSizeCxGap = width * 0.25f + margin * 0.5f
        val cxGap = baseCxGap + (maxSizeCxGap - baseCxGap) * sizeGrowth
        val rightCx = width - cxGap
        val cy = height * if (portrait) 0.54f else 0.52f

        // The action row occupies the lower strip. Reserve that space first, then size the
        // circles from both the width and the remaining vertical room so a larger radius never
        // collides with punctuation / Enter controls.
        val smallH = min(height * 0.10f, 48f * density)
        val actionReserve = smallH + margin + 4f * density
        val widthRadiusLimit = cxGap / 0.96f
        val centerSpacingLimit = (rightCx - cxGap) / 1.92f
        val heightRadiusLimit = min(
            cy / 0.96f,
            (height - actionReserve - cy) / 0.96f,
        )
        val preferredRadius = if (portrait) {
            min(width * 0.24f, height * 0.34f) * size
        } else {
            min(width * 0.17f, height * 0.38f) * size
        }
        val radius = min(
            max(72f * density, preferredRadius),
            min(widthRadiusLimit, min(centerSpacingLimit, heightRadiusLimit)),
        )
        // Give the frequent-letter inner ring more physical area without making the
        // Space/Delete hubs too small for thumb landing. Keep a 28dp radius floor.
        val hubRadius = max(radius * 0.27f, 28f * density)
        val innerInner = hubRadius * 1.16f
        val innerOuter = radius * 0.60f
        val outerInner = radius * 0.63f
        val outerOuter = radius * 0.96f
        val gapRad = Math.toRadians((if (portrait) 3.2f else 3.8f).toDouble()).toFloat()

        val cfg = Config(
            leftCx = cxGap,
            rightCx = rightCx,
            cy = cy,
            radius = radius,
            hubRadius = hubRadius,
            innerInner = innerInner,
            innerOuter = innerOuter,
            outerInner = outerInner,
            outerOuter = outerOuter,
            gapRad = gapRad,
            specialRects = specialRects,
            touchInflatePx = max(8f * density, radius * 0.045f),
        )
        config = cfg

        val alphabet = keys.firstOrNull()?.alphabet ?: com.kinetica.keyboard.engine.Alphabet.LATIN
        val partition = when {
            alphabet == com.kinetica.keyboard.engine.Alphabet.TURKISH && layoutStyle == "qwerty" -> turkishQwertyPartition
            alphabet == com.kinetica.keyboard.engine.Alphabet.TURKISH -> turkishPartition
            else -> latinPartition
        }
        val rings = partition.codes(alphabet)
        activeCodes = rings.flatMap { it.asIterable() }.toIntArray()
        addRing(cfg, Side.LEFT, 0, rings[0], DualCircleMath.PHASE_DEGREES)
        addRing(cfg, Side.LEFT, 1, rings[1], DualCircleMath.PHASE_DEGREES)
        addRing(cfg, Side.RIGHT, 0, rings[2], DualCircleMath.PHASE_DEGREES)
        addRing(cfg, Side.RIGHT, 1, rings[3], DualCircleMath.PHASE_DEGREES)

        // System/action keys live outside the circles. Use semantic ids rather than list
        // positions so future upstream layout changes do not break the mapping.
        val smallW = min(width * 0.090f, 72f * density)
        val gap = 6f * density
        fun index(id: String): Int = keys.indexOfFirst { it.id == id }
        fun put(id: String, rect: RectF) {
            val idx = index(id)
            if (idx >= 0) specialRects[idx] = rect
        }
        put("shift", RectF(margin, margin, margin + smallW, margin + smallH))
        put("mode", RectF(margin, margin + smallH + gap, margin + smallW, margin + 2f * smallH + gap))
        val semanticModeIndex = keys.indexOfFirst {
            it.type == com.kinetica.keyboard.layout.KeyType.MODE_ALPHA ||
                it.type == com.kinetica.keyboard.layout.KeyType.MODE_SYMBOLS ||
                it.type == com.kinetica.keyboard.layout.KeyType.MODE_SYMBOLS2 ||
                it.type == com.kinetica.keyboard.layout.KeyType.MODE_NUMPAD
        }
        if (semanticModeIndex >= 0) {
            specialRects[semanticModeIndex] = RectF(
                margin,
                margin + smallH + gap,
                margin + smallW,
                margin + 2f * smallH + gap,
            )
        }
        put("emoji", RectF(margin, height - smallH - margin, margin + smallW, height - margin))
        put("enter", RectF(width - margin - smallW, height - smallH - margin, width - margin, height - margin))
        val hasApostrophe = keys.any { it.id == "apostrophe" }
        if (hasApostrophe) {
            put("comma", RectF(width * 0.31f, height - smallH - margin, width * 0.31f + smallW, height - margin))
            put("apostrophe", RectF(width * 0.50f - smallW * 0.5f, height - smallH - margin, width * 0.50f + smallW * 0.5f, height - margin))
            put("period", RectF(width * 0.69f - smallW, height - smallH - margin, width * 0.69f, height - margin))
        } else {
            put("comma", RectF(width * 0.42f, height - smallH - margin, width * 0.42f + smallW, height - margin))
            put("period", RectF(width * 0.58f - smallW, height - smallH - margin, width * 0.58f, height - margin))
        }
        return cfg
    }

    private fun addRing(cfg: Config, side: Side, ring: Int, codes: IntArray, phaseDegrees: Float) {
        val inner = if (ring == 0) cfg.innerInner else cfg.outerInner
        val outer = if (ring == 0) cfg.innerOuter else cfg.outerOuter
        val n = codes.size
        val step = (2.0 * PI / n).toFloat()
        val phase = Math.toRadians(phaseDegrees.toDouble()).toFloat()
        for (i in codes.indices) {
            val start = -PI.toFloat() / 2f + phase + i * step + cfg.gapRad / 2f
            val end = -PI.toFloat() / 2f + phase + (i + 1) * step - cfg.gapRad / 2f
            val path = annularPath(
                if (side == Side.LEFT) cfg.leftCx else cfg.rightCx,
                cfg.cy,
                inner,
                outer,
                start,
                end,
            )
            val segment = Segment(side, ring, i, n, codes[i], path, RectF().also { path.computeBounds(it, true) })
            segments += segment
            segmentsByCode[codes[i]] = segment
        }
    }

    private fun annularPath(cx: Float, cy: Float, inner: Float, outer: Float, start: Float, end: Float): Path {
        val p = Path()
        val sweepDeg = Math.toDegrees((end - start).toDouble()).toFloat()
        fun point(r: Float, angle: Float): Pair<Float, Float> =
            Pair(cx + r * cos(angle), cy + r * sin(angle))
        val (sx, sy) = point(outer, start)
        p.moveTo(sx, sy)
        val outerRect = RectF(cx - outer, cy - outer, cx + outer, cy + outer)
        p.arcTo(outerRect, Math.toDegrees(start.toDouble()).toFloat(), sweepDeg, false)
        val (ex, ey) = point(inner, end)
        p.lineTo(ex, ey)
        val innerRect = RectF(cx - inner, cy - inner, cx + inner, cy + inner)
        p.arcTo(innerRect, Math.toDegrees(end.toDouble()).toFloat(), -sweepDeg, false)
        p.close()
        return p
    }

    fun hit(x: Float, y: Float): Hit {
        val c = config ?: return Hit.None
        // Special controls are outside the circular board and win there.
        for ((keyIndex, rect) in specialRects) {
            if (rect.contains(x, y)) return Hit.Special(keyIndex)
        }
        val leftD = DualCircleMath.distance(x, y, c.leftCx, c.cy)
        val rightD = DualCircleMath.distance(x, y, c.rightCx, c.cy)
        if (leftD <= c.hubRadius) return Hit.Hub(Side.LEFT)
        if (rightD <= c.hubRadius) return Hit.Hub(Side.RIGHT)
        // Visual sectors keep their small separation, but touch targets overlap slightly so a
        // thumb landing on a boundary still resolves to a letter instead of a dead zone.
        val candidates = segments.filter { containsForCode(x, y, it.code, c.touchInflatePx) }
        if (candidates.isNotEmpty()) {
            val best = candidates.minBy { segmentCenterDistanceSquared(x, y, it) }
            return Hit.Letter(best.code)
        }
        return Hit.None
    }

    private fun segmentCenterDistanceSquared(x: Float, y: Float, s: Segment): Float {
        val c = config ?: return Float.MAX_VALUE
        val cx = if (s.side == Side.LEFT) c.leftCx else c.rightCx
        val step = (2.0 * PI / s.count).toFloat()
        val angle = -PI.toFloat() / 2f + Math.toRadians(DualCircleMath.PHASE_DEGREES.toDouble()).toFloat() + (s.index + 0.5f) * step
        val ring = if (s.ring == 0) (c.innerInner + c.innerOuter) * 0.5f else (c.outerInner + c.outerOuter) * 0.5f
        val px = cx + ring * cos(angle)
        val py = c.cy + ring * sin(angle)
        val dx = x - px
        val dy = y - py
        return dx * dx + dy * dy
    }

    fun containsForCode(x: Float, y: Float, code: Int, inflatePx: Float): Boolean {
        val s = segmentsByCode[code] ?: return false
        val c = config ?: return false
        val cx = if (s.side == Side.LEFT) c.leftCx else c.rightCx
        val d = DualCircleMath.distance(x, y, cx, c.cy)
        val inner = if (s.ring == 0) c.innerInner else c.outerInner
        val outer = if (s.ring == 0) c.innerOuter else c.outerOuter
        if (d < inner - inflatePx || d > outer + inflatePx) return false
        val angle = DualCircleMath.angleAt(x, y, cx, c.cy)
        val step = (2.0 * PI / s.count).toFloat()
        val phase = Math.toRadians(DualCircleMath.PHASE_DEGREES.toDouble()).toFloat()
        val start = DualCircleMath.normalizeAngle(
            -PI.toFloat() / 2f + phase + s.index * step + c.gapRad / 2f,
        )
        val end = DualCircleMath.normalizeAngle(
            -PI.toFloat() / 2f + phase + (s.index + 1) * step - c.gapRad / 2f,
        )
        return DualCircleMath.containsAngle(
            angle, start, end, c.gapRad / 2f + inflatePx / max(d, 1f),
        )
    }

    /** Pure angle slot helper used by geometry tests and comments. */
    internal fun slotCenterAngleForTest(index: Int, count: Int): Float =
        DualCircleMath.slotAngle(index, count)

    fun segment(code: Int): Segment? = segmentsByCode[code]

    fun centerX(code: Int): Float? {
        val s = segmentsByCode[code] ?: return null
        val b = s.bounds
        return b.centerX()
    }

    fun centerY(code: Int): Float? {
        val s = segmentsByCode[code] ?: return null
        val b = s.bounds
        return b.centerY()
    }

    fun hubRect(side: Side): RectF {
        val c = requireNotNull(config)
        val cx = if (side == Side.LEFT) c.leftCx else c.rightCx
        return RectF(cx - c.hubRadius, c.cy - c.hubRadius, cx + c.hubRadius, c.cy + c.hubRadius)
    }

    fun specialRect(keyIndex: Int): RectF? = specialRects[keyIndex]

    fun allSpecialRects(): Map<Int, RectF> = specialRects.toMap()

    fun hintFor(code: Int): String? = hintByCode[code]

    fun leftCenterX(): Float = requireNotNull(config).leftCx
    fun rightCenterX(): Float = requireNotNull(config).rightCx
    fun centerY(): Float = requireNotNull(config).cy
    fun radius(): Float = requireNotNull(config).radius

    fun letterCodes(): IntArray = activeCodes.copyOf()

    /** Pure partition data exposed only to module tests; drawing/hit code still consumes codes. */
    internal fun partitionLettersForTest(alphabet: com.kinetica.keyboard.engine.Alphabet, layoutStyle: String = "optimized"): List<String> =
        if (alphabet == com.kinetica.keyboard.engine.Alphabet.TURKISH) {
            val p = if (layoutStyle == "qwerty") turkishQwertyPartition else turkishPartition
            listOf(p.leftInner, p.leftOuter, p.rightInner, p.rightOuter)
        } else {
            listOf(
                latinPartition.leftInner, latinPartition.leftOuter,
                latinPartition.rightInner, latinPartition.rightOuter,
            )
        }

    internal fun ringCodesForTest(alphabet: com.kinetica.keyboard.engine.Alphabet): List<IntArray> =
        (if (alphabet == com.kinetica.keyboard.engine.Alphabet.TURKISH) turkishPartition else latinPartition)
            .codes(alphabet).toList()

    fun rectForKey(key: Key, keyIndex: Int): RectF {
        when {
            key.isLetter -> segmentsByCode[key.letterCode]?.bounds?.let { return RectF(it) }
            key.type == com.kinetica.keyboard.layout.KeyType.SPACE -> return hubRect(Side.LEFT)
            key.type == com.kinetica.keyboard.layout.KeyType.BACKSPACE -> return hubRect(Side.RIGHT)
            else -> specialRects[keyIndex]?.let { return RectF(it) }
        }
        return RectF()
    }

    fun pathForKey(key: Key): Path? = if (key.isLetter) segmentsByCode[key.letterCode]?.path else null

}
