package com.tencentcs.iotvideo.custom

import android.util.Log
import com.google.gson.Gson
import com.tencentcs.iotvideo.utils.LogUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.internal.toLongOrDefault
import okio.ByteString
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class LoginInfoMessage(
    var accessId: Long,
    var accessToken: String,
    var deviceId: String,
    val expireTime: Long,
    val timestamp: Double
)

data class ServerLoginInfoMessage(
    var accessId: String,
    var accessToken: String,
    var deviceId: String,
    val expireTime: Long,
    val timestamp: Double
)

data class ServerControlSubscribeMessage(
    var type: String,
    var topic: String
)

data class ServerControlResponseMessage(
    var type: String,
    var message: String
)

data class UpdateStatusMessage(
    val user: String,
    val status: String
)

data class IncomingMessage<T>(
    val type: String,
    val data: T
)

class WebsocketHandler
{
    private val TAG = "WebsocketHandler"
    private val MAX_BYTES: Long = 10485760


    private var client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val gson = Gson()
    private lateinit var _ws: WebSocket
    private val executor = Executors.newSingleThreadExecutor()

    private var _url: String = ""
    private var _connectionStatus: String = "Not connected"
    private var _isOpen = false

    private var loginInfoCallback: ((LoginInfoMessage) -> Unit)? = null

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            // Connection opened
            _connectionStatus += ", Connection Opened!"
            // send subscribe to login-info topic?
            val subMessage: ServerControlSubscribeMessage = ServerControlSubscribeMessage("Subscribe", "login-info")
            val jsonStr: String = gson.toJson(subMessage)
            webSocket.send(jsonStr)
            _isOpen = true;
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // Message received
            Log.d(TAG, "New message!")
            handleMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            _connectionStatus += ", Connection Closed!"
            webSocket.close(1000, null);
            _isOpen = false
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            // Connection failed
            t.printStackTrace()
            _connectionStatus += ", Connection failed! (${response?.code}) ${response?.message}"
        }
    }

    fun Setup(url: String){
        // default _ws.Setup("ws://10.0.2.2:3030")
        if (!url.contains("ws://", ignoreCase = true))
        {
            Log.d(TAG, "URL invalid, cancelling Websocket: ${url}. Must contain ws:// for websocket connection")
            return
        }

        _url = url
        val request = Request.Builder()
            .url(url)
            .build()

        _ws = client.newWebSocket(request, listener)
        Log.d(TAG, "Created new websocket connection on: ${url}")
    }

    fun sendTextMessage(message: String) {
        if (!_isOpen)
            return

        val qSize = _ws.queueSize()
        if (qSize > MAX_BYTES)
        {
            LogUtils.w(TAG, "Queue is full at: ${qSize}")
            return
        }

        _ws.send(message)
    }

    // Method to send a binary message
    fun sendBinaryMessage(byteArray: ByteArray): Boolean {
        if (!_isOpen)
            return false

        val qSize = _ws.queueSize()
        if (qSize > MAX_BYTES)
        {
            LogUtils.w(TAG, "Queue is full at: ${qSize}")
            return false
        }

        return _ws.send(ByteString.of(*byteArray))
    }

    fun sendByteBuffer(buffer1: ByteBuffer) : Boolean
    {
        val totalSize = buffer1.remaining()

        // Create a new ByteBuffer to hold all the data
        val combinedBuffer = ByteBuffer.allocate(totalSize)

        // Put the data from the original ByteBuffers into the new buffer
        combinedBuffer.put(buffer1)

        // Prepare the buffer for reading
        combinedBuffer.flip()

        // Send the combined buffer as a binary message
        return sendBinaryMessage(combinedBuffer.array())
    }

    fun sendByteBufferThreaded(buffer1: ByteBuffer) : Boolean
    {
        var didSend = false
        executor.execute {
            val totalSize = buffer1.remaining()

            // Create a new ByteBuffer to hold all the data
            val combinedBuffer = ByteBuffer.allocate(totalSize)

            // Put the data from the original ByteBuffers into the new buffer
            combinedBuffer.put(buffer1)

            // Prepare the buffer for reading
            combinedBuffer.flip()

            // Send the combined buffer as a binary message
            didSend = sendBinaryMessage(combinedBuffer.array())
            LogUtils.d(TAG, "didSend: $didSend, send binary message of size: $totalSize")
        }
        return didSend
    }

    fun sendCombinedByteBufferThreaded(buffer1: ByteBuffer, buffer2: ByteBuffer, buffer3: ByteBuffer) : Boolean
    {
        var didSend = false
        executor.execute {
            val totalSize = buffer1.remaining() + buffer2.remaining() + buffer3.remaining()

            // Create a new ByteBuffer to hold all the data
            val combinedBuffer = ByteBuffer.allocate(totalSize)

            // Put the data from the original ByteBuffers into the new buffer
            combinedBuffer.put(buffer1)
            combinedBuffer.put(buffer2)
            combinedBuffer.put(buffer3)

            // Prepare the buffer for reading
            combinedBuffer.flip()

            // Send the combined buffer as a binary message
            didSend = sendBinaryMessage(combinedBuffer.array())
            LogUtils.d(TAG, "didSend: $didSend, send binary message of size: $totalSize")
        }
        return didSend
    }

    fun combineAndSendByteBuffers(buffer1: ByteBuffer, buffer2: ByteBuffer, buffer3: ByteBuffer) : Boolean {
        // Calculate the total size needed
        val totalSize = buffer1.remaining() + buffer2.remaining() + buffer3.remaining()

        // Create a new ByteBuffer to hold all the data
        val combinedBuffer = ByteBuffer.allocate(totalSize)

        // Put the data from the original ByteBuffers into the new buffer
        combinedBuffer.put(buffer1)
        combinedBuffer.put(buffer2)
        combinedBuffer.put(buffer3)

        // Prepare the buffer for reading
        combinedBuffer.flip()

        // Send the combined buffer as a binary message
        return sendBinaryMessage(combinedBuffer.array())
    }

    fun handleMessage(wsMessage: String) {
        // Parse the type field
        val type = JSONObject(wsMessage).getString("type")
        Log.d(TAG, "New message of type: ${type}")
        _connectionStatus += ", message(${type})"
        when (type) {
            "login-info" -> {
                val message = gson.fromJson(wsMessage, IncomingMessage::class.java)
                handleLoginInfo(gson.fromJson(gson.toJson(message.data), ServerLoginInfoMessage::class.java))
            }
            "updateStatus" -> {
                val message = gson.fromJson(wsMessage, IncomingMessage::class.java)
                handleUpdateStatus(gson.fromJson(gson.toJson(message.data), UpdateStatusMessage::class.java))
            }
            "Acknowledgement" -> {
                handleSubscribeAck(gson.fromJson(wsMessage, ServerControlResponseMessage::class.java))
            }
            // Handle other types
        }
        Log.d(TAG, "Done handling message of type: ${type}")
    }

    fun handleSubscribeAck(serverControlResponseMessage: ServerControlResponseMessage)
    {
        Log.i(TAG, "Acknowledgment from websocket server: ${serverControlResponseMessage.message})")
    }

    fun handleLoginInfo(loginMessage: ServerLoginInfoMessage)
    {
        Log.d(TAG, "New login info! ${loginMessage.deviceId}")
        if (loginInfoCallback != null)
        {
            var localLoginInfo: LoginInfoMessage = LoginInfoMessage(loginMessage.accessId.toLongOrDefault(-1), loginMessage.accessToken, loginMessage.deviceId, loginMessage.expireTime, loginMessage.timestamp)
            loginInfoCallback?.invoke(localLoginInfo)
        }
    }

    fun handleUpdateStatus(updateStatusMessage: UpdateStatusMessage) {
        Log.d(TAG, "Status update: ${updateStatusMessage.status}")
        // Handle the status update
    }

    fun SetLoginCallback(callbackFn: (LoginInfoMessage) -> Unit)
    {
        loginInfoCallback = callbackFn
    }
}

