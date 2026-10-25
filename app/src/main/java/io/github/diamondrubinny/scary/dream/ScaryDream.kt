// SPDX-License-Identifier: Apache-2.0
// Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
// Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.

package io.github.diamondrubinny.scary.dream

import io.github.diamondrubinny.scary.core.security.ParentalGate
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.service.dreams.DreamService
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.scale
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.github.diamondrubinny.scary.R
import io.github.diamondrubinny.scary.ScaryApplication
import io.github.diamondrubinny.scary.core.brand.Credit
import io.github.diamondrubinny.scary.core.brand.Donation
import io.github.diamondrubinny.scary.core.brand.QrCode
import io.github.diamondrubinny.scary.core.catalog.FamilyNight
import io.github.diamondrubinny.scary.core.model.Language
import io.github.diamondrubinny.scary.core.model.Movie
import io.github.diamondrubinny.scary.data.LanguageStore
import io.github.diamondrubinny.scary.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * SCARY's screensaver: backdrops from the library fade in one after another, each with its title.
 *
 * No flashing: a 1.5 second crossfade every 12 seconds and a slow 8% zoom, both skipped when the TV's
 * "Remove animations" is on. With the parental lock on, only movies below the lock level appear; on family night,
 * only the gentlest ones.
 * Only the curated horror library appears. Offline it falls back to those movies' own tints, so it never shows a
 * blank screen.
 */
class ScaryDream : DreamService() {
    private var scope: CoroutineScope? = null
    private lateinit var layers: Array<ImageView>
    private lateinit var title: TextView
    private lateinit var meta: TextView
    private lateinit var overlay: View
    private var front = 0
    private var reduceMotion = false
    private var language = Language.HE

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        language = LanguageStore.read(this)
        reduceMotion = !SettingsRepository.systemAnimationsEnabled(this)
        setContentView(buildLayout())
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        val job = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = job
        job.launch { cycle() }
    }

    override fun onDreamingStopped() {
        scope?.cancel()
        scope = null
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    /** Movies to show, shuffled; replaced when artwork arrives from the cache or the network. */
    private var pool: List<Movie> = emptyList()

    private suspend fun cycle() {
        scope?.launch { watchCatalog() }
        var index = 0
        while (true) {
            val current = pool
            if (current.isEmpty()) {
                delay(WAIT_MS)
                continue
            }
            show(current[index % current.size])
            index++
            delay(SLIDE_MS)
        }
    }

    /**
     * The same layered catalog the home screen uses: bundled first, then cached and fresh artwork. It honors the
     * online switch, and with the parental lock on it keeps only movies below the lock level.
     */
    private suspend fun watchCatalog() {
        val container = (application as ScaryApplication).container
        val prefs = container.settingsRepository.prefs.first()
        container.tmdbRepository.load()
        val hasKey = container.tmdbRepository.keyMask.value != null
        val random = Random(System.currentTimeMillis())
        try {
            container.catalogRepository
                .catalog(flowOf(prefs.onlineEnabled), flowOf(hasKey), flowOf(language))
                .collect { catalog ->
                    // Only the hand-picked library: the online "popular" rows can include plain action movies.
                    val allowed = catalog.movies.filter {
                        it.curated &&
                            // The screensaver never unlocks: it shows only what needs no PIN.
                            !ParentalGate.isLocked(prefs.parentalEnabled, unlocked = false, prefs.lockFromFear, it) &&
                            (!prefs.familyNight || FamilyNight.allows(it))
                    }
                    val withArt = allowed.filter { it.backgroundUrl != null }
                    val next = withArt.ifEmpty { allowed }
                    // Only swap when the set grows into artwork, so the slideshow does not restart for nothing.
                    if (pool.isEmpty() || (withArt.isNotEmpty() && pool.none { it.backgroundUrl != null })) {
                        pool = next.shuffled(random)
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A broken bundled catalog or storage error: nothing to show, the TV falls back to its own dark screen.
            Log.w(TAG, "Screensaver catalog failed: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun show(movie: Movie) {
        val back = layers[1 - front]
        val image = movie.backgroundUrl?.let { url ->
            val request = ImageRequest.Builder(this).data(url).build()
            (imageLoader.execute(request) as? SuccessResult)?.image?.asDrawable(resources)
        }
        back.setImageDrawable(image ?: tintFor(movie))
        back.scaleX = 1f
        back.scaleY = 1f
        title.text = movie.displayTitle(language)
        meta.text = listOfNotNull(
            movie.year.toString(),
            movie.director.takeIf(String::isNotBlank),
        ).joinToString("  ·  ")

        val old = layers[front]
        if (reduceMotion) {
            back.alpha = 1f
            old.alpha = 0f
        } else {
            back.animate().alpha(1f).setDuration(FADE_MS).start()
            old.animate().alpha(0f).setDuration(FADE_MS).start()
            // A separate animator: ViewPropertyAnimator shares one duration across properties on the same view.
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(back, View.SCALE_X, 1f, ZOOM),
                    ObjectAnimator.ofFloat(back, View.SCALE_Y, 1f, ZOOM),
                )
                duration = SLIDE_MS + FADE_MS
                interpolator = LinearInterpolator()
                start()
            }
        }
        back.bringToFront()
        front = 1 - front
        // Text and the darkening scrim stay above both images.
        overlay.bringToFront()
    }

    private fun tintFor(movie: Movie): Drawable {
        val hue = movie.hue.toFloat()
        val sat = movie.saturation / 100f
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.HSVToColor(floatArrayOf(hue, sat, 0.32f)), Color.HSVToColor(floatArrayOf(hue, sat, 0.08f)), Color.BLACK),
        )
    }

    private fun buildLayout(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutDirection = if (language.rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        }
        layers = Array(2) { i ->
            ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                alpha = if (i == 0) 1f else 0f
                // Images are decorative; the title below names the movie.
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                root.addView(this, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
        }

        val overlay = FrameLayout(this)
        this.overlay = overlay
        overlay.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(90, 0, 0, 0), Color.TRANSPARENT, Color.argb(235, 0, 0, 0)),
        )
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(64), 0, dp(64), dp(56))
        }
        title = TextView(this).apply {
            typeface = font(
                when (language) {
                    Language.RU -> R.font.oswald_semibold
                    Language.AR -> R.font.rakkas_regular
                    else -> R.font.karantina_bold
                },
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f)
            setTextColor(BONE)
            maxLines = 2
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        meta = TextView(this).apply {
            typeface = font(
                when (language) {
                    Language.RU -> R.font.rubik_regular
                    Language.AR -> R.font.cairo_regular
                    else -> R.font.assistant_regular
                },
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(ASH)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        text.addView(title)
        text.addView(meta)
        overlay.addView(
            text,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START),
        )
        val logo = TextView(this).apply {
            this.text = getString(R.string.app_name)
            typeface = font(R.font.rubik_wet_paint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(BLOOD)
            setPadding(dp(48), dp(36), dp(48), 0)
        }
        val credit = TextView(this).apply {
            this.text = Credit.SHORT
            typeface = font(R.font.assistant_regular)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ASH)
            textDirection = View.TEXT_DIRECTION_LTR
            setPadding(dp(48), 0, dp(48), 0)
        }
        // The small donation code under the logo, as on the home screen. Dark on white, so phone cameras read it.
        val donate = ImageView(this).apply {
            setImageBitmap(qrBitmap(Donation.qr()))
            contentDescription = getString(R.string.donate_qr_description)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            // Wrap content: by default a vertical LinearLayout stretches text to match the widest fixed-size child,
            // and the QR code would then cut "SCARY" short.
            val wrap = { LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
            addView(logo, wrap())
            addView(credit, wrap())
            addView(
                donate,
                LinearLayout.LayoutParams(dp(DONATE_QR_DP), dp(DONATE_QR_DP)).apply { setMargins(dp(48), dp(10), dp(48), 0) },
            )
        }
        overlay.addView(
            brand,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END),
        )
        root.addView(overlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return root
    }

    /** One pixel per module, quiet zone included; the view scales it up without smoothing. */
    private fun qrBitmap(code: QrCode): Bitmap {
        val side = code.size + 2 * QrCode.QUIET_ZONE
        val pixels = IntArray(side * side) { Color.WHITE }
        for (y in 0 until code.size) {
            for (x in 0 until code.size) {
                if (code.isDark(x, y)) pixels[(y + QrCode.QUIET_ZONE) * side + x + QrCode.QUIET_ZONE] = Color.BLACK
            }
        }
        val small = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
        // Scaled here with filtering off, so the modules stay sharp squares.
        return small.scale(side * QR_SCALE, side * QR_SCALE, filter = false)
    }

    private fun font(id: Int): Typeface? = runCatching { ResourcesCompat.getFont(this, id) }.getOrNull()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "ScaryDream"
        const val SLIDE_MS = 12_000L
        const val FADE_MS = 1_500L
        const val WAIT_MS = 300L
        const val ZOOM = 1.08f
        const val DONATE_QR_DP = 72
        const val QR_SCALE = 8
        val BONE = Color.rgb(0xE9, 0xE1, 0xCF)
        val ASH = Color.rgb(0xA3, 0x9E, 0x92)
        val BLOOD = Color.rgb(0xD9, 0x40, 0x40)
    }
}
