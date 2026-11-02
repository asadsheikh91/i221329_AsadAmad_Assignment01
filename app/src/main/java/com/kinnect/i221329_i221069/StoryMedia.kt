package com.kinnect.i221329_i221069

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.VideoView
import java.io.File
import java.util.concurrent.Executors

// Shows the picture or the video of a story inside the story card (viewer and "your story").
//  - photo: the small picture appears at once and is replaced by the big one when it has loaded
//  - video: the small picture shows while the clip is loaded from the database, written to a temporary file
//    in the cache folder and played by a VideoView laid over the picture
class StoryMediaView(private val activity: Activity, card: ViewGroup, private val art: ImageView) {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val video = VideoView(activity)
    private var currentId = ""

    init {
        video.visibility = android.view.View.GONE
        val params = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        params.addRule(RelativeLayout.CENTER_IN_PARENT)
        // Just above the picture, below the progress bar and the buttons
        card.addView(video, 1, params)
    }

    /** Shows [story]. For a video, [onVideoReady] is called with its length when it starts to play. */
    fun show(story: Story, onVideoReady: (Long) -> Unit) {
        currentId = story.id
        video.stopPlayback()
        video.visibility = android.view.View.GONE
        art.visibility = android.view.View.VISIBLE
        PostImages.show(art, "story_thumb_${story.id}", story.thumb)

        Backend.loadStoryMedia(story.id) { media ->
            if (media == null || currentId != story.id) {
                return@loadStoryMedia
            }
            if (story.isVideo) {
                playVideo(story, media, onVideoReady)
            } else {
                PostImages.show(art, "story_full_${story.id}", media)
            }
        }
    }

    private fun playVideo(story: Story, media: String, onVideoReady: (Long) -> Unit) {
        worker.execute {
            val file = File(activity.cacheDir, "story_${story.id}.mp4")
            try {
                file.writeBytes(Base64.decode(media, Base64.DEFAULT))
            } catch (e: Exception) {
                return@execute
            }
            main.post {
                if (currentId != story.id) {
                    return@post
                }
                video.setOnPreparedListener { player ->
                    player.isLooping = false
                    if (currentId == story.id) {
                        video.visibility = android.view.View.VISIBLE
                        video.start()
                        onVideoReady(player.duration.toLong())
                    }
                }
                video.setOnErrorListener { _, _, _ ->
                    // The clip cannot be played: the picture stays
                    onVideoReady(5000)
                    true
                }
                video.setVideoPath(file.path)
            }
        }
    }

    /** Pauses or resumes the clip together with the story's progress bar. */
    fun setHeld(held: Boolean) {
        if (video.visibility != android.view.View.VISIBLE) {
            return
        }
        if (held) video.pause() else video.start()
    }

    fun release() {
        currentId = ""
        video.stopPlayback()
    }
}
