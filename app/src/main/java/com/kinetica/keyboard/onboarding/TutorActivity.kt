package com.kinetica.keyboard.onboarding

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.preference.PreferenceManager
import com.kinetica.keyboard.R
import com.kinetica.keyboard.engine.Alphabet
import com.kinetica.keyboard.ime.AlphaLayouts
import com.kinetica.keyboard.layout.Key
import com.kinetica.keyboard.layout.LayoutLoader
import com.kinetica.keyboard.settings.KeyboardConfig
import com.kinetica.keyboard.ui.DualCircleLayout
import com.kinetica.keyboard.ui.GhostPath
import com.kinetica.keyboard.ui.KeyboardTheme
import com.kinetica.keyboard.ui.TutorPathView
import java.io.IOException
import org.json.JSONException

/**
 * The gesture tutor: one word at a time, its strokes drawn over the board, typed with Kinetica
 * into the field below; the next word comes once this one is written. Nothing is recorded.
 */
class TutorActivity : AppCompatActivity() {

    private val main = Handler(Looper.getMainLooper())
    private var index = 0
    private var letters: List<Key> = emptyList()
    private var lessons: List<TutorScript.Lesson> = TutorScript.lessons("en")
    private lateinit var step: TextView
    private lateinit var hint: TextView
    private lateinit var board: TutorPathView
    private lateinit var field: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val config = KeyboardConfig.from(PreferenceManager.getDefaultSharedPreferences(this))
        lessons = TutorScript.lessons(config.language)
        letters = try {
            val bundled = (assets.list("layouts") ?: emptyArray()).map { it.removeSuffix(".json") }.toSet()
            // The lesson language and the board alphabet must agree. Turkish lessons include
            // ı/ö/ğ; for a Turkish layout the same radial geometry as the live Dual Circle is
            // used when that mode is enabled.
            val boardName = if (Alphabet.forLanguage(config.language) == Alphabet.LATIN) AlphaLayouts.name(config, bundled) else "qwerty"
            val keyboardKeys = AlphaLayouts.build(LayoutLoader.load(assets, "layouts/$boardName.json"), config).keys
            val letterKeys = keyboardKeys.filter { it.isLetter }
            if (config.language == "tr" && config.dualCircleMode) {
                val boardWidth = 1000f
                val boardHeight = 420f
                val radial = DualCircleLayout()
                radial.rebuild(
                    boardWidth, boardHeight, keyboardKeys, 1f, isLandscape = true,
                    sizePct = config.dualCircleSizePct,
                    spreadPct = config.dualCircleSpreadPct,
                    layoutStyle = config.dualCircleLayout,
                )
                letterKeys.map { key ->
                    val keyIndex = keyboardKeys.indexOf(key)
                    val rect = radial.rectForKey(key, keyIndex)
                    key.copy(
                        x = rect.left / boardWidth,
                        y = rect.top / boardHeight,
                        w = rect.width() / boardWidth,
                        h = rect.height() / boardHeight,
                    )
                }
            } else {
                letterKeys
            }
        } catch (e: IOException) {
            Log.w(TAG, "tutor layout", e)
            emptyList()
        } catch (e: JSONException) {
            Log.w(TAG, "tutor layout", e)
            emptyList()
        }
        step = TextView(this).apply { textSize = 20f }
        hint = TextView(this).apply { setPadding(0, pad / 2, 0, pad / 2) }
        board = TutorPathView(this).apply {
            theme = KeyboardTheme.resolve(this@TutorActivity, config.themeMode, config.themeColor, config.themeBrightness)
            keys = letters
        }
        field = EditText(this).apply { setHint(R.string.tutor_field_hint) }
        val skip = Button(this).apply {
            setText(R.string.tutor_skip)
            setOnClickListener { advance() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad)
            addView(step)
            addView(hint)
            val boardParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.widthPixels * 0.36f).toInt(),
            )
            addView(board, boardParams)
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                // Use the app window's measured width, not the physical display width. This
                // keeps the practice board usable in split-screen and freeform windows.
                val availableHeight = (v.height * 0.34f).toInt().coerceAtLeast((180 * resources.displayMetrics.density).toInt())
                val target = (v.width * 0.72f).toInt().coerceAtMost(availableHeight)
                val lp = board.layoutParams
                if (v.width > 0 && lp.height != target) {
                    lp.height = target
                    board.layoutParams = lp
                }
            }
            addView(field)
            addView(skip)
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val lesson = lessons.getOrNull(index) ?: return
                if (s != null && TutorScript.done(s, lesson.word, config.language)) {
                    hint.setText(R.string.tutor_well_done)
                    main.postDelayed({ advance() }, ADVANCE_MS)
                }
            }
        })
        setContentView(root)
        show()
    }

    override fun onResume() {
        super.onResume()
        showGhost()
    }

    override fun onPause() {
        // The ghost belongs to the tutor alone, and a pending advance must not paint it back
        // after the tutor has left.
        main.removeCallbacksAndMessages(null)
        GhostPath.clear()
        super.onPause()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        GhostPath.clear()
        super.onDestroy()
    }

    /** The lesson's strokes on the keyboard itself, as on the board above the field. */
    private fun showGhost() {
        val lesson = lessons.getOrNull(index) ?: return GhostPath.clear()
        GhostPath.show(strokesOf(lesson).map { GhostPath.Stroke(it.left, it.letters) })
    }

    private fun strokesOf(lesson: TutorScript.Lesson): List<TutorScript.Stroke> {
        val byLetter = letters.associateBy { it.output[0] }
        val left = letters.minOfOrNull { it.x } ?: 0f
        val right = letters.maxOfOrNull { it.x + it.w } ?: 1f
        val midline = (left + right) / 2f
        return TutorScript.strokes(lesson.word, lesson.twoThumbs) { c ->
            val k = byLetter[c]
            k != null && k.x + k.w / 2f < midline
        }
    }

    private fun advance() {
        main.removeCallbacksAndMessages(null)
        index++
        if (index >= lessons.size) {
            finish()
            return
        }
        show()
    }

    private fun show() {
        val lesson = lessons[index]
        step.text = getString(R.string.tutor_step, index + 1, lessons.size, lesson.word)
        hint.setText(
            when (lesson.hint) {
                TutorScript.Hint.ONE_THUMB -> R.string.tutor_hint_one_thumb
                TutorScript.Hint.SWIPE_AND_TAP -> R.string.tutor_hint_swipe_and_tap
                TutorScript.Hint.TWO_SWIPES -> R.string.tutor_hint_two_swipes
                TutorScript.Hint.MIXED -> R.string.tutor_hint_mixed
                TutorScript.Hint.ALTERNATE -> R.string.tutor_hint_alternate
            },
        )
        val byLetter = letters.associateBy { it.output[0] }
        board.strokes = strokesOf(lesson).map { s ->
            TutorPathView.Stroke(
                s.left,
                s.letters.mapNotNull { c -> byLetter[c]?.let { (it.x + it.w / 2f) to (it.y + it.h / 2f) } },
            )
        }
        field.setText("")
        field.requestFocus()
        showGhost()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private companion object {
        const val TAG = "KineticaTutor"

        /** Long enough to read the praise, short enough not to wait for it. */
        const val ADVANCE_MS = 700L
    }
}
