package com.kinnect.i221329_i221069

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.ColorStateList
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

// Plays a list of stories one after the other, for both "view a friend's story" and "your story".
//  - [bar] is the progress row at the top: one segment per story, the current one fills up in 5 seconds
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

    private val fills = ArrayList<View>()
    private val holds = HashSet<String>()     // reasons the story is paused right now
    private var animator: ValueAnimator? = null
    private var index = 0
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
        if (held) holds.add(reason) else holds.remove(reason)
        val running = animator ?: return
        if (holds.isEmpty()) {
            if (running.isPaused) running.resume()
        } else {
            if (running.isRunning) running.pause()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    private fun show(position: Int) {
        animator?.cancel()
        index = position
        for (i in fills.indices) {
            fills[i].scaleX = if (i < position) 1f else 0f
        }
        onShow(stories[position])

        val running = ValueAnimator.ofFloat(0f, 1f)
        running.duration = 5000
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

/**
 * Shows a story's picture: the small one at once, then the full size one when it has been loaded.
 * [stillCurrent] says whether the story is still the one on screen when the big picture arrives.
 */
fun showStoryPicture(view: ImageView, story: Story, stillCurrent: () -> Boolean) {
    PostImages.show(view, "story_thumb_${story.id}", story.thumb)
    Backend.loadStoryImage(story.id) { full ->
        if (full != null && stillCurrent()) {
            PostImages.show(view, "story_full_${story.id}", full)
        }
    }
}
