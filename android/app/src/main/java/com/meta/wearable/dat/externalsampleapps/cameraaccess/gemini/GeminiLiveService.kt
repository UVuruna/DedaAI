package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.ai.AiConnectionState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.ai.AiProvider
import java.io.ByteArrayOutputStream
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kept so the rest of the app can keep naming the type it already names. The
 * states themselves belong to [AiConnectionState], which is backend-neutral.
 */
typealias GeminiConnectionState = AiConnectionState

/** Gemini Live over a WebSocket. The only [AiProvider] implementation today. */
class GeminiLiveService : AiProvider {
    companion object {
        private const val TAG = "GeminiLiveService"

        /**
         * Reason prefixes for a server-initiated close (goAway or a close
         * frame). Google ending the session itself — e.g. its own free-tier
         * session limit — is a NORMAL end of a conversation, not a failure;
         * GeminiSession matches on these to skip the error banner.
         */
        const val REASON_SERVER_CLOSING = "Server closing"
        const val REASON_CONNECTION_CLOSED = "Connection closed"
    }

    private val _connectionState = MutableStateFlow<AiConnectionState>(AiConnectionState.Disconnected)
    override val connectionState: StateFlow<AiConnectionState> = _connectionState.asStateFlow()

    private val _isModelSpeaking = MutableStateFlow(false)
    override val isModelSpeaking: StateFlow<Boolean> = _isModelSpeaking.asStateFlow()

    override var onAudioReceived: ((ByteArray) -> Unit)? = null
    override var onTurnComplete: (() -> Unit)? = null
    override var onInterrupted: (() -> Unit)? = null
    override var onDisconnected: ((String?) -> Unit)? = null
    override var onInputTranscription: ((String) -> Unit)? = null
    override var onOutputTranscription: ((String) -> Unit)? = null
    override var onToolCall: ((String) -> Unit)? = null
    override var onFunctionCall: ((String, JSONObject) -> JSONObject?)? = null

    override val inputSampleRate: Int = GeminiConfig.INPUT_AUDIO_SAMPLE_RATE
    override val outputSampleRate: Int = GeminiConfig.OUTPUT_AUDIO_SAMPLE_RATE

    // Latency tracking
    private var lastUserSpeechEnd: Long = 0
    private var responseLatencyLogged = false

    private var webSocket: WebSocket? = null
    private val sendExecutor = Executors.newSingleThreadExecutor()

    /**
     * Tool calls run HERE, never on the reader thread. onMessage is also the
     * audio pump — playback is a blocking write on it — so a tool that reads
     * the address book or asks Telecom to place a call would stall the
     * answer's audio and can starve the socket's pings. Its own thread, kept
     * apart from sendExecutor, which carries microphone audio.
     */
    private val toolExecutor = Executors.newSingleThreadExecutor()
    private var connectCallback: ((Boolean) -> Unit)? = null
    private var timeoutTimer: Timer? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    override fun connect(onReady: (Boolean) -> Unit) {
        val url = GeminiConfig.websocketURL()
        if (url == null) {
            _connectionState.value = AiConnectionState.Error("No API key configured")
            onReady(false)
            return
        }

        _connectionState.value = AiConnectionState.Connecting
        connectCallback = onReady

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                _connectionState.value = AiConnectionState.SettingUp
                sendSetupMessage()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleMessage(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val msg = t.message ?: "Unknown error"
                Log.e(TAG, "WebSocket failure: $msg")
                _connectionState.value = AiConnectionState.Error(msg)
                _isModelSpeaking.value = false
                resolveConnect(false)
                onDisconnected?.invoke(msg)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                _connectionState.value = AiConnectionState.Disconnected
                _isModelSpeaking.value = false
                resolveConnect(false)
                onDisconnected?.invoke("$REASON_CONNECTION_CLOSED (code $code: $reason)")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                _connectionState.value = AiConnectionState.Disconnected
                _isModelSpeaking.value = false
            }
        })

        // Timeout after 15 seconds (use Timer so we don't block sendExecutor)
        timeoutTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    if (_connectionState.value == AiConnectionState.Connecting
                        || _connectionState.value == AiConnectionState.SettingUp) {
                        Log.e(TAG, "Connection timed out")
                        _connectionState.value = AiConnectionState.Error("Connection timed out")
                        resolveConnect(false)
                    }
                }
            }, 15000)
        }
    }

    override fun disconnect() {
        timeoutTimer?.cancel()
        timeoutTimer = null
        webSocket?.close(1000, null)
        webSocket = null
        _connectionState.value = AiConnectionState.Disconnected
        _isModelSpeaking.value = false
        resolveConnect(false)
    }

    override fun sendAudio(data: ByteArray) {
        if (_connectionState.value != AiConnectionState.Ready) return
        sendExecutor.execute {
            val base64 = Base64.encodeToString(data, Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("audio", JSONObject().apply {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", base64)
                    })
                })
            }
            webSocket?.send(json.toString())
        }
    }

    override fun sendImage(bitmap: Bitmap) {
        if (_connectionState.value != AiConnectionState.Ready) return
        sendExecutor.execute {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, GeminiConfig.VIDEO_JPEG_QUALITY, baos)
            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("video", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64)
                    })
                })
            }
            webSocket?.send(json.toString())
        }
    }

    override fun sendText(text: String) {
        if (_connectionState.value != AiConnectionState.Ready) return
        sendExecutor.execute {
            val json = JSONObject().apply {
                put("clientContent", JSONObject().apply {
                    put("turns", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", text)
                        }))
                    }))
                })
            }
            webSocket?.send(json.toString())
        }
    }

    // Private

    private fun resolveConnect(success: Boolean) {
        val cb = connectCallback
        connectCallback = null  // null out BEFORE invoking to prevent re-entrancy
        timeoutTimer?.cancel()
        timeoutTimer = null
        cb?.invoke(success)
    }

    private fun sendSetupMessage() {
        val setup = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", GeminiConfig.MODEL)
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("thinkingConfig", JSONObject().apply {
                        put("thinkingBudget", 0)
                    })
                    // Only sent when the code is known to be accepted. An
                    // unsupported code fails the whole handshake, so unverified
                    // languages are steered by the system prompt instead.
                    GeminiConfig.speechLanguageCode?.let { code ->
                        put("speechConfig", JSONObject().apply {
                            put("languageCode", code)
                        })
                    }
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", GeminiConfig.systemInstruction)
                    }))
                })
                // end_conversation always; the voice-command tools when the
                // user enabled them (declarations live in CommandRegistry —
                // GeminiConfig hands them over so this transport stays
                // backend-plumbing only).
                put("tools", JSONArray().put(JSONObject().apply {
                    put("functionDeclarations", GeminiConfig.toolDeclarations())
                }))
                put("realtimeInputConfig", JSONObject().apply {
                    put("automaticActivityDetection", JSONObject().apply {
                        put("disabled", false)
                        put("startOfSpeechSensitivity", "START_SENSITIVITY_HIGH")
                        put("endOfSpeechSensitivity", "END_SENSITIVITY_LOW")
                        put("silenceDurationMs", 500)
                        put("prefixPaddingMs", 40)
                    })
                    put("activityHandling", "START_OF_ACTIVITY_INTERRUPTS")
                    put("turnCoverage", "TURN_INCLUDES_ALL_INPUT")
                })
                put("contextWindowCompression", JSONObject().apply {
                    put("slidingWindow", JSONObject().apply {
                        put("targetTokens", 80000)
                    })
                })
                put("inputAudioTranscription", JSONObject())
                put("outputAudioTranscription", JSONObject())
            })
        }
        // Send directly (not via sendExecutor) to ensure it's the first message
        webSocket?.send(setup.toString())
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)

            // Setup complete
            if (json.has("setupComplete")) {
                _connectionState.value = AiConnectionState.Ready
                resolveConnect(true)
                return
            }

            // GoAway
            if (json.has("goAway")) {
                val goAway = json.getJSONObject("goAway")
                val seconds = goAway.optJSONObject("timeLeft")?.optInt("seconds", 0) ?: 0
                _connectionState.value = AiConnectionState.Disconnected
                _isModelSpeaking.value = false
                onDisconnected?.invoke("$REASON_SERVER_CLOSING (time left: ${seconds}s)")
                return
            }

            // Tool call. A handler that returns a payload (the voice
            // commands) supplies the model's function response — the model
            // SPEAKS from it (a numbered contact list, an error). Without
            // one (end_conversation), acknowledge "ok" FIRST — the model
            // waits for the response — then tell the app via onToolCall.
            if (json.has("toolCall")) {
                val calls = json.getJSONObject("toolCall").optJSONArray("functionCalls")
                if (calls != null) {
                    // The socket this call arrived on: a slow tool must never
                    // answer into a LATER session's socket.
                    val socket = webSocket
                    for (i in 0 until calls.length()) {
                        val call = calls.getJSONObject(i)
                        val id = call.optString("id", "")
                        val name = call.optString("name", "")
                        val args = call.optJSONObject("args") ?: JSONObject()
                        toolExecutor.execute {
                            val handled = try {
                                onFunctionCall?.invoke(name, args)
                            } catch (e: Exception) {
                                // A crash here must not take the session with
                                // it; the model still needs an answer.
                                Log.w(TAG, "tool $name threw: $e")
                                JSONObject().put("status", "error")
                                    .put("detail", e.message ?: "failed")
                            }
                            val response = JSONObject().apply {
                                put("toolResponse", JSONObject().apply {
                                    put("functionResponses", JSONArray().put(JSONObject().apply {
                                        put("id", id)
                                        put("name", name)
                                        put("response", handled ?: JSONObject().put("output", "ok"))
                                    }))
                                })
                            }
                            socket?.send(response.toString())  // send is thread-safe
                            Log.d(TAG, "Tool call handled: $name -> ${handled?.optString("status") ?: "ok"}")
                            if (handled == null) onToolCall?.invoke(name)
                        }
                    }
                }
                return
            }

            // Server content
            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")

                if (serverContent.optBoolean("interrupted", false)) {
                    _isModelSpeaking.value = false
                    onInterrupted?.invoke()
                    return
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    if (modelTurn.has("parts")) {
                        val parts = modelTurn.getJSONArray("parts")
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val mimeType = inlineData.optString("mimeType", "")
                                if (mimeType.startsWith("audio/pcm")) {
                                    val base64Data = inlineData.optString("data", "")
                                    if (base64Data.isNotEmpty()) {
                                        val audioData = Base64.decode(base64Data, Base64.DEFAULT)
                                        if (!_isModelSpeaking.value) {
                                            _isModelSpeaking.value = true
                                            if (lastUserSpeechEnd > 0 && !responseLatencyLogged) {
                                                val latency = System.currentTimeMillis() - lastUserSpeechEnd
                                                Log.d(TAG, "[Latency] ${latency}ms (user speech end -> first audio)")
                                                responseLatencyLogged = true
                                            }
                                        }
                                        onAudioReceived?.invoke(audioData)
                                    }
                                }
                            } else if (part.has("text")) {
                                Log.d(TAG, part.getString("text"))
                            }
                        }
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    _isModelSpeaking.value = false
                    responseLatencyLogged = false
                    onTurnComplete?.invoke()
                }

                if (serverContent.has("inputTranscription")) {
                    val transcription = serverContent.getJSONObject("inputTranscription")
                    val transcriptText = transcription.optString("text", "")
                    if (transcriptText.isNotEmpty()) {
                        Log.d(TAG, "You: $transcriptText")
                        lastUserSpeechEnd = System.currentTimeMillis()
                        responseLatencyLogged = false
                        onInputTranscription?.invoke(transcriptText)
                    }
                }

                if (serverContent.has("outputTranscription")) {
                    val transcription = serverContent.getJSONObject("outputTranscription")
                    val transcriptText = transcription.optString("text", "")
                    if (transcriptText.isNotEmpty()) {
                        Log.d(TAG, "AI: $transcriptText")
                        onOutputTranscription?.invoke(transcriptText)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}")
        }
    }
}
