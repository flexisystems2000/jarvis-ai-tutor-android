package com.flexi.jarvis.live

object ScreenShareFrameBus {
    @Volatile var onFrame: ((ByteArray) -> Unit)? = null
    @Volatile var onStopped: (() -> Unit)? = null
}
