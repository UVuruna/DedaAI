package com.meta.wearable.dat.externalsampleapps.cameraaccess.settings

/**
 * How the user puts Deda into standby. Two deliberately separate modes (owner
 * decision 2026-08-18): either the glasses' touchpad does it, or Deda never
 * touches the glasses' media buttons at all and the switch lives in the phone
 * notification. In both modes standby itself behaves the same (the wake and
 * farewell phrases, the timers).
 */
enum class DedaActivationMode(val displayName: String, val description: String) {

    /**
     * Double tap toggles Deda while no music plays; Deda holds the media
     * buttons only in that window and hands single taps back to the music app.
     * While music actually plays, every tap stays native (PLAN.md: per-gesture
     * routing is impossible).
     */
    GLASSES_TAP(
        displayName = "Glasses tap",
        description = "Double tap toggles Deda while no music plays. While music plays, all taps stay with the music.",
    ),

    /** Deda never claims the media buttons; music is untouched at all times. */
    NOTIFICATION(
        displayName = "Notification button",
        description = "Deda never touches the glasses buttons. Turn it on and off from the phone notification.",
    ),
    ;

    companion object {
        val DEFAULT = GLASSES_TAP

        fun fromName(name: String?): DedaActivationMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
