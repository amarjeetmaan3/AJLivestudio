package com.amarjeetmaan.ajlivestudio.ui.live

/**
 * Custom RTMP configuration — manual URL + Stream Key, no YouTube OAuth.
 * (Direct YouTube API mode is a later phase per the roadmap.)
 */
data class RtmpConfig(
    val serverUrl: String = "",
    val streamKey: String = "",
) {
    /**
     * Builds the full ingest URL StreamPack expects, e.g.
     * "rtmp://a.rtmp.youtube.com/live2" + "abcd-efgh-1234" ->
     * "rtmp://a.rtmp.youtube.com/live2/abcd-efgh-1234"
     */
    fun fullUrl(): String {
        val trimmedServer = serverUrl.trim().trimEnd('/')
        val trimmedKey = streamKey.trim()
        return if (trimmedKey.isEmpty()) trimmedServer else "$trimmedServer/$trimmedKey"
    }

    fun isValid(): Boolean =
        serverUrl.trim().let { it.startsWith("rtmp://") || it.startsWith("rtmps://") } &&
            streamKey.trim().isNotEmpty()
}
