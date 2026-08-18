package com.meta.wearable.dat.externalsampleapps.cameraaccess.settings

/**
 * When a camera frame is sent to the AI backend.
 *
 * The original app only had STREAM: one JPEG every second for as long as the
 * session was open, whether or not anything had been asked. A minute of silence
 * cost sixty images — which is also why the setup handshake had to enable
 * context window compression to keep the conversation from overflowing.
 */
enum class VideoFrameMode(val displayName: String, val description: String) {

    /**
     * One frame when the user starts speaking. Roughly one image per question
     * instead of sixty per minute, and the image is the one the user was looking
     * at as they asked.
     */
    ON_QUESTION(
        displayName = "On question",
        description = "Send one frame when you start speaking. Fewest images, longest session.",
    ),

    /** The original behaviour, kept for comparison and for moving subjects. */
    STREAM(
        displayName = "Continuous",
        description = "Send a frame every second. Follows movement, drains quota fast.",
    ),

    /** Voice only. The backend never sees the camera. */
    OFF(
        displayName = "Off",
        description = "Never send frames. Voice only.",
    );

    companion object {
        val DEFAULT = ON_QUESTION

        fun fromName(name: String?): VideoFrameMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
