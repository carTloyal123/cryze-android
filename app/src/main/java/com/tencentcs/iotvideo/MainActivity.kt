package com.tencentcs.iotvideo

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tencentcs.iotvideo.custom.LoginInfoMessage
import com.tencentcs.iotvideo.custom.WebsocketHandler
import com.tencentcs.iotvideo.iotvideoplayer.IAudioRender
import com.tencentcs.iotvideo.iotvideoplayer.IConnectDevStateListener
import com.tencentcs.iotvideo.iotvideoplayer.IVideoRender
import com.tencentcs.iotvideo.iotvideoplayer.IoTVideoPlayer
import com.tencentcs.iotvideo.iotvideoplayer.codec.AVData
import com.tencentcs.iotvideo.iotvideoplayer.codec.AVHeader
import com.tencentcs.iotvideo.iotvideoplayer.codec.IAudioDecoder
import com.tencentcs.iotvideo.iotvideoplayer.codec.IVideoDecoder
import com.tencentcs.iotvideo.iotvideoplayer.mediacodec.MediaCodecVideoDecoder
import com.tencentcs.iotvideo.iotvideoplayer.player.PlayerUserData
import com.tencentcs.iotvideo.messagemgr.MessageMgr
import com.tencentcs.iotvideo.ui.theme.CustomNativeIotVideoTheme
import com.tencentcs.iotvideo.utils.LogUtils
import com.tencentcs.iotvideo.utils.rxjava.IResultListener


class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val userInfo = hashMapOf<String, Any>()
        userInfo["IOT_HOST"] = "|wyze-mars-asrv.wyzecam.com"
        IoTVideoSdk.init(this, userInfo)
    }
}


class MainActivity : ComponentActivity() {

    private val TAG: String = "MainActivityIot"

    private var statusString: String = ""

    private var _ws: WebsocketHandler = WebsocketHandler()

    private var iotVideoPlayer: IoTVideoPlayer = IoTVideoPlayer()

    private var framesSent: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _ws.Setup("ws://10.0.2.2:3030")
        _ws.SetLoginCallback(::mainLoginCallback)

        setContent {
            CustomNativeIotVideoTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Version: " + IoTVideoSdk.getP2PVersion() + ". Status: " + framesSent)
                }
            }
        }
    }

    private var deviceIdList: ArrayList<String> = ArrayList()

    private fun registerIotVideoSdk(loginInfo: LoginInfoMessage)
    {
        IoTVideoSdk.register(loginInfo.accessId, loginInfo.accessToken, 3)
        IoTVideoSdk.getMessageMgr().removeModelListeners()
        IoTVideoSdk.getMessageMgr().addModelListener {
            // do something with model info here?
            LogUtils.d("CLModelListener", "new model message! ${it.device}, path: ${it.path}, data: ${it.data}")
            deviceIdList.add(it.device)
        }
    }

    private fun unregisterIotVideoSdk()
    {
        IoTVideoSdk.unRegister()
        IoTVideoSdk.getMessageMgr().removeAppLinkListeners()
        IoTVideoSdk.getMessageMgr().removeModelListeners()
    }

    private fun addAppLinkListener(loginInfo: LoginInfoMessage)
    {
        if (isSdkRegistered())
        {
            LogUtils.i(TAG, "appLinkListener() iot is register")
            addSubscribeDevice(loginInfo)
            return
        }

        IoTVideoSdk.getMessageMgr().addAppLinkListener {
            LogUtils.i(TAG, "appLinkListener state = $it")
            var shouldExit = false
            if (it == 1) {
                LogUtils.i(TAG, "Reg success, app online, start live")
                addSubscribeDevice(loginInfo)
                shouldExit = true
            }
            if (!shouldExit)
            {
                var z = true
                if (it != 6 && it != 13 && (12 > it || it >= 18))
                {
                    z = false
                }
                if (z)
                {
                    unregisterIotVideoSdk()
                }
            }

        }
    }

    private fun addSubscribeDevice(loginInfo: LoginInfoMessage)
    {
        if (deviceIdList.contains(loginInfo.deviceId))
        {
            IoTVideoSdk.getNetConfig().subscribeDevice(loginInfo.accessToken, loginInfo.deviceId, object : IResultListener<Boolean> {
                override fun onError(i10: Int, str: String?) {
                    LogUtils.e("DeviceResultIOT", "on Error: $i10 with messsage: $str")
                }

                override fun onStart() {
                    LogUtils.e("DeviceResultIOT", "onStart")
                }

                override fun onSuccess(t10: Boolean?) {
                    LogUtils.e("DeviceResultIOT", "onSuccess: $t10")
                    var videoPlayer: IoTVideoPlayer = iotVideoPlayer
                    setupIotVideoPlayer(loginInfo)
                    videoPlayer.play()
                    LogUtils.i(TAG, "Player state: ${videoPlayer.playState}");
                }
            })
        } else {
            LogUtils.w(TAG, "Deviceid not in device list of models so far")
            LogUtils.w(TAG, deviceIdList.toString())
        }
    }

    private fun isSdkRegistered() : Boolean
    {
        return MessageMgr.getSdkStatus() == 1
    }

    private fun setupIotVideoPlayer(loginInfo: LoginInfoMessage)
    {
        val ioTVideoPlayer: IoTVideoPlayer = iotVideoPlayer

        ioTVideoPlayer.mute(true)
        ioTVideoPlayer.setDataResource("_@." + loginInfo.deviceId, 1, PlayerUserData(2))
        ioTVideoPlayer.setConnectDevStateListener(object : IConnectDevStateListener
        {
            override fun onStatus(i10: Int) {
                LogUtils.i(TAG, "onStatus for iotvideo player: $i10")
            }

        })
        ioTVideoPlayer.setAudioRender(object : IAudioRender{
            override fun flushRender() {
                LogUtils.d(TAG, "IAudioRender flushRender for iotvideo player")
            }

            override fun getWaitRenderDuration(): Long {
                return 0L
            }

            override fun onFrameUpdate(aVData: AVData?) {
                LogUtils.d(TAG, "IAudioRender onFrameUpdate for iotvideo player, size: ${aVData.toString()}")
            }

            override fun onInit(aVHeader: AVHeader?) {
                LogUtils.d(TAG, "IAudioRender override fun onInit(aVHeader: AVHeader?) for iotvideo player, size: ${aVHeader.toString()}")
            }

            override fun onRelease() {
                LogUtils.d(TAG, "IAudioRender onRelease for iotvideo player")
            }

            override fun setPlayerVolume(f10: Float) {
                LogUtils.d(TAG, "IAudioRender setPlayerVolume for iotvideo player}")
            }

        })

        // Setting the video render will only get a frame if you use a decoder in android like MediaCodecVideoDecoder,
        // if you use a custom IVideoDecoder then nothing will get sent to onFrameUpdate
        ioTVideoPlayer.setVideoRender(object : IVideoRender {
            override fun onFrameUpdate(aVData: AVData?) {
                LogUtils.d(TAG, "CustomVideoRender onFrameUpdate for iotvideo player, size: ${aVData.toString()}")
                // send frame?
                if (aVData != null) {
//                    _ws.sendByteBufferThreaded(aVData.data)
                    _ws.sendCombinedByteBufferThreaded(aVData.data, aVData.data1, aVData.data2)
                }
            }

            override fun onInit(aVHeader: AVHeader?) {
                LogUtils.d(TAG, "IVideoRender override fun onInit(aVHeader: AVHeader?) {\n for iotvideo player, size: ${aVHeader.toString()}")
            }

            override fun onPause() {
                LogUtils.d(TAG, "IVideoRender onPause for iotvideo player")
            }

            override fun onRelease() {
                LogUtils.d(TAG, "IVideoRender onRelease for iotvideo player")
            }

            override fun onResume() {
                LogUtils.d(TAG, "IVideoRender onResume for iotvideo player")
            }

        })

        // MediaCodecVideoDecoder uses the built-in android video processing tools to decode the H.264 raw stream and produce images
        // If MediaCodecVideoDecoder is used then you can intercept the full resolution frame on the onFrameData callback in the IVideoRender method
        ioTVideoPlayer.setVideoDecoder(MediaCodecVideoDecoder())

        // The custom IVideoDecoder lets us grab raw H264 frames and do whatever we want with them
        ioTVideoPlayer.setVideoDecoder(object : IVideoDecoder {
            override fun init(aVHeader: AVHeader?) {
                LogUtils.i("CustomVideoDecoder", "CustomVideoDecoder init!")
            }

            override fun receive_frame(aVData: AVData?): Int {
                // this gives us a way to set the data on the avData bytebuffers that contains the final image, not useful unless using an android codec scheme
                LogUtils.i("CustomVideoDecoder", "receive_frame! ${aVData.toString()})")
                return 0;
            }

            override fun release() {
                LogUtils.i("CustomVideoDecoder", "CustomVideoDecoder init!")
            }

            override fun send_packet(aVData: AVData?): Int {
                // This gets raw h264 frames stored in the avData.data buffer
                LogUtils.i("CustomVideoDecoder", "send_packet! ${aVData.toString()})")
                if (aVData != null) {
                    _ws.sendByteBufferThreaded(aVData.data)
                }
                return 0;
            }

        })
        ioTVideoPlayer.setErrorListener { i10 ->
            LogUtils.i(
                TAG,
                "errorListener onError for iotvideo player: $i10"
            )
        }
        ioTVideoPlayer.setStatusListener { i10 ->
            LogUtils.i(
                TAG,
                "IStatusListener onStatus for iotvideo player: $i10"
            )
        }
    }

    private fun mainLoginCallback(loginInfo: LoginInfoMessage)
    {
            LogUtils.e(TAG, "LOGIN INFO accessId: ${loginInfo.accessId}, token: ${loginInfo.accessToken}, deviceId: ${loginInfo.deviceId}")
            registerIotVideoSdk(loginInfo)
            Thread.sleep(2200)
            addAppLinkListener(loginInfo)
    }

}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CustomNativeIotVideoTheme {
        Greeting("Android")
    }
}