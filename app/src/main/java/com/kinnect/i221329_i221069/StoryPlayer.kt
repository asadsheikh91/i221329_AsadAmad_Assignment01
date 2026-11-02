package com.kinnect.i221329_i221069

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout

// Plays a list of stories one after the other, for both "view a friend's story" and "your story".
//  - [bar] is the progress row at the top: one segment per story, the current one fills up
//    (5 seconds for a photo, as long as the clip for a video)
//  - tap the right part of [touchArea] for the next story, the left third for the previous one
//  - holding a finger on [touchArea] (or typing a reply) pauses the story
//  - after the last story [onFinished] is called
class StoryPlayer(
    private val activity: Activity,
    private val bar: LinearLayout,
    touchArea: View,
    private val stories: List<Story>,
    private val onShow: (Story) -> Unit,
    private val onFinished: () -> Unit
) {

    /** Called when the story is paused (true) or plays on (false), so a video can pause with it. */
    var onHoldChanged: ((Boolean) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val fills = ArrayList<View>()
    private val holds = HashSet<String>()     // reasons the story is paused right now
    private var animator: ValueAnimator? = null
    private var index = 0
    private var timerStarted = false
    private var touchDownTime = 0L

    init {
        // The design's sample bar is replaced by one segment per story
        bar.removeAllViews()
        bar.background = null
        for (i in stories.indices) {
            val segment = FrameLayout(activity)
            segment.setBackgroundResource(R.drawable.bg_pill)
            segment.backgroundTintList = ColorStateList.valueOf(0x66FFFFFF)

            val fill = View(activity)
            fill.setBackgroundResource(R.drawable.bg_pill)
            fill.pivotX = 0f
            fill.scaleX = 0f
            segment.addView(fill, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            if (i > 0) {
                params.marginStart = dp(activity, 4)
            }
            bar.addView(segment, params)
            fills.add(fill)
        }

        touchArea.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownTime = event.eventTime
                    hold("touch", true)
                }
                MotionEvent.ACTION_UP -> {
                    hold("touch", false)
                    // A quick tap moves between stories, a long press only pauses
                    if (event.eventTime - touchDownTime < 300) {
                        if (event.x < view.width / 3f) previous() else next()
                    }
                }
                MotionEvent.ACTION_CANCEL -> hold("touch", false)
            }
            true
        }
    }

    /** Starts playing at story number [at]. */
    fun start(at: Int) {
        if (stories.isNotEmpty()) {
            show(at.coerceIn(0, stories.size - 1))
        }
    }

    /** Pauses ([held] true) or resumes the story. The story plays only while nothing holds it. */
    fun hold(reason: String, held: Boolean) {
        val before = holds.isNotEmpty()
        if (held) holds.add(reason) else holds.remove(reason)
        val running = animator
        if (running != null) {
            if (holds.isEmpty()) {
                if (running.isPaused) running.resume()
            } else {
                if (running.isRunning) running.pause()
            }
        }
        val after = holds.isNotEmpty()
        if (before != after) {
            onHoldChanged?.invoke(after)
        }
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        animator?.cancel()
        animator = null
    }

    /** A video story is ready to play and lasts [durationMs]: now its progress bar starts. */
    fun videoReady(storyId: String, durationMs: Long) {
        if (!timerStarted && stories.getOrNull(index)?.id == storyId) {
            startTimer(index, durationMs.coerceIn(2000, 15000))
        }
    }

    private fun show(position: Int) {
        main.removeCallbacksAndMessages(null)
        animator?.cancel()
        index = position
        timerStarted = false
        for (i in fills.indices) {
            fills[i].scaleX = if (i < position) 1f else 0f
        }
        val story = stories[position]
        onShow(story)

        if (story.isVideo) {
            // The bar waits for the video (videoReady). If it never comes, move on after a while.
            main.postDelayed({ if (!timerStarted && index == position) startTimer(position, 5000) }, 10_000)
        } else {
            startTimer(position, 5000)
        }
    }

    private fun startTimer(position: Int, durationMs: Long) {
        timerStarted = true
        val running = ValueAnimator.ofFloat(0f, 1f)
        running.duration = durationMs
        running.interpolator = LinearInterpolator()
        running.addUpdateListener { fills[position].scaleX = it.animatedValue as Float }
        running.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) {
                    next()
                }
            }
        })
        animator = running
        running.start()
        if (holds.isNotEmpty()) {
            running.pause()
        }
    }

    private fun next() {
        if (index + 1 < stories.size) {
            show(index + 1)
        } else {
            stop()
            onFinished()
        }
    }

    private fun previous() {
        show(maxOf(0, index - 1))
    }
}
