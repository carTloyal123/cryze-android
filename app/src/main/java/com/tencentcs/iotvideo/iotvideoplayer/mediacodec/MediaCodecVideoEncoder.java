package com.tencentcs.iotvideo.iotvideoplayer.mediacodec;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.view.Surface;
import com.tencentcs.iotvideo.iotvideoplayer.codec.AVData;
import com.tencentcs.iotvideo.iotvideoplayer.codec.AVHeader;
import com.tencentcs.iotvideo.iotvideoplayer.codec.IVideoEncoder;
import com.tencentcs.iotvideo.utils.IoTYUVKits;
import com.tencentcs.iotvideo.utils.LogUtils;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
/* loaded from: classes2.dex */
public class MediaCodecVideoEncoder implements IVideoEncoder {
    private static final boolean DEBUG_SAVE_ENCODE_DATA = false;
    private static final boolean DEBUG_SAVE_YUV_DATA = false;
    private static final int DEQUEUE_TIMEOUT = 100;
    private static final String ENCODE_DATA_PATH = "/sdcard/Android/data/com.tencentcs.iotvideo.test/files/Movies/encodeRet.h264";
    private static final int RECEIVE_FRAME_MAX_COUNT = 20;
    private static final int SEND_FRAME_MAX_COUNT = 20;
    private static final String TAG = "MediaCodecVideoEncoder";
    private static final String YUV_DATA_PATH = "/sdcard/Android/data/com.tencentcs.iotvideo.test/files/Movies/waitEncoder.yuv";
    private ByteBuffer buffer;
    private IMediaCodecCheckListener checkListener;
    private IMediaCodecListener codecListener;
    private int currentReceiveFrameCount;
    private int currentSendFrameCount;
    private int height;
    private final boolean isSupportEncode;
    private final MediaCodec.BufferInfo mBufferInfo;
    private FileOutputStream mEncodeStream;
    private MediaCodec mEncoder;
    private FileOutputStream mIoStream;
    private long mPts;
    private int mSupportTypes;
    private byte[] nv21Bytes;
    private int width;

    public MediaCodecVideoEncoder() {
        this.mPts = 0L;
        this.mBufferInfo = new MediaCodec.BufferInfo();
        this.nv21Bytes = null;
        this.buffer = null;
        this.currentSendFrameCount = 0;
        this.currentReceiveFrameCount = 0;
        this.isSupportEncode = true;
    }

    private int prepareEncode(AVHeader aVHeader) {
        LogUtils.i(TAG, "prepareEncode");
        if (this.mEncoder != null) {
            LogUtils.e(TAG, "prepareEncode repeat init");
            return 0;
        }
        try {
            this.mEncoder = MediaCodec.createEncoderByType("video/avc");
            int integer = aVHeader.getInteger(AVHeader.KEY_WIDTH, 0);
            int integer2 = aVHeader.getInteger(AVHeader.KEY_HEIGHT, 0);
            int integer3 = aVHeader.getInteger(AVHeader.KEY_FRAME_RATE, 0);
            this.width = integer;
            this.height = integer2;
            LogUtils.i(TAG, "width：" + integer + ", height：" + integer2);
            int supportTypes = getSupportTypes();
            this.mSupportTypes = supportTypes;
            if (supportTypes == 21) {
                int i10 = integer * integer2;
                this.nv21Bytes = new byte[(i10 / 2) + i10];
            }
            MediaFormat createVideoFormat = MediaFormat.createVideoFormat("video/avc", integer, integer2);
            createVideoFormat.setInteger("bitrate-mode", 2);
            createVideoFormat.setInteger(AVHeader.KEY_BIT_RATE, 2000000);
            createVideoFormat.setInteger(AVHeader.KEY_FRAME_RATE, integer3);
            createVideoFormat.setInteger("color-format", this.mSupportTypes);
            createVideoFormat.setInteger("i-frame-interval", 2);
            try {
                this.mEncoder.configure(createVideoFormat, (Surface) null, (MediaCrypto) null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                this.mEncoder.start();
                IMediaCodecListener iMediaCodecListener = this.codecListener;
                if (iMediaCodecListener != null) {
                    iMediaCodecListener.onInitResult(0, "");
                }
                return 0;
            } catch (Exception e10) {
                LogUtils.e(TAG, "prepareEncode configure exception:" + e10.getMessage());
                IMediaCodecListener iMediaCodecListener2 = this.codecListener;
                if (iMediaCodecListener2 != null) {
                    iMediaCodecListener2.onInitResult(101, e10.getMessage());
                }
                this.mEncoder = null;
                return -1;
            }
        } catch (IOException e11) {
            LogUtils.e(TAG, "prepareEncode create codec exception:" + e11.getMessage());
            IMediaCodecListener iMediaCodecListener3 = this.codecListener;
            if (iMediaCodecListener3 != null) {
                iMediaCodecListener3.onInitResult(100, e11.getMessage());
            }
            this.mEncoder = null;
            return -1;
        }
    }

    private void releaseEncode() {
        LogUtils.i(TAG, "releaseEncode");
        this.mPts = 0L;
        ByteBuffer byteBuffer = this.buffer;
        if (byteBuffer != null) {
            byteBuffer.clear();
            this.buffer = null;
        }
        MediaCodec mediaCodec = this.mEncoder;
        if (mediaCodec == null) {
            return;
        }
        try {
            mediaCodec.stop();
            this.mEncoder.release();
            this.mEncoder = null;
        } catch (Exception e10) {
            LogUtils.e(TAG, "releaseEncode exception:" + e10.getMessage());
            this.mEncoder = null;
        }
    }

    public int getSupportTypes() {
        MediaCodecInfo[] codecInfos;
        String[] supportedTypes;
        for (MediaCodecInfo mediaCodecInfo : new MediaCodecList(-1).getCodecInfos()) {
            if (mediaCodecInfo.isEncoder()) {
                for (String str : mediaCodecInfo.getSupportedTypes()) {
                    if (str.equals("video/avc")) {
                        LogUtils.d(TAG, "编码器名称:" + mediaCodecInfo.getName() + "  " + str);
                        int[] iArr = mediaCodecInfo.getCapabilitiesForType("video/avc").colorFormats;
                        int length = iArr.length;
                        for (int i10 = 0; i10 < length; i10++) {
                            int i11 = iArr[i10];
                            if (i11 != 39) {
                                switch (i11) {
                                    case 17:
                                    case 18:
                                    case 19:
                                    case 20:
                                    case 21:
                                        break;
                                    default:
                                }
                            }
                            LogUtils.d(TAG, "支持的格式::" + i11);
                            return i11;
                        }
                        continue;
                    }
                }
                continue;
            }
        }
        return 19;
    }

    @Override // com.tencentcs.iotvideo.iotvideoplayer.codec.IVideoEncoder
    public int init(AVHeader aVHeader) {
        this.mPts = System.nanoTime();
        return prepareEncode(aVHeader);
    }

    @Override // com.tencentcs.iotvideo.iotvideoplayer.codec.IVideoEncoder
    public int receive_packet(AVData aVData) {
        IMediaCodecCheckListener iMediaCodecCheckListener;
        try {
            int dequeueOutputBuffer = this.mEncoder.dequeueOutputBuffer(this.mBufferInfo, 100L);
            if (dequeueOutputBuffer < 0) {
                return -1;
            }
            ByteBuffer outputBuffer = this.mEncoder.getOutputBuffer(dequeueOutputBuffer);
            if (outputBuffer != null && aVData != null) {
                int i10 = this.mBufferInfo.size;
                if (this.buffer == null) {
                    int i11 = this.width;
                    int i12 = this.height;
                    this.buffer = ByteBuffer.allocateDirect((i11 * i12) + ((i11 * i12) / 2));
                }
                this.buffer.clear();
                this.buffer.put(outputBuffer);
                this.buffer.rewind();
                byte[] bArr = new byte[i10];
                aVData.data = this.buffer.get(bArr);
                aVData.size = i10;
                MediaCodec.BufferInfo bufferInfo = this.mBufferInfo;
                aVData.pts = bufferInfo.presentationTimeUs / 1000;
                aVData.keyFrame = bufferInfo.flags & 1;
                this.mEncoder.releaseOutputBuffer(dequeueOutputBuffer, false);
                FileOutputStream fileOutputStream = this.mEncodeStream;
                if (fileOutputStream != null) {
                    fileOutputStream.write(bArr);
                }
                this.currentReceiveFrameCount = 0;
                return 0;
            }
            return -11;
        } catch (Exception e10) {
            LogUtils.e(TAG, "receive_packet cache " + e10.getMessage());
            e10.printStackTrace();
            int i13 = this.currentReceiveFrameCount + 1;
            this.currentReceiveFrameCount = i13;
            if (i13 > 20 && (iMediaCodecCheckListener = this.checkListener) != null) {
                iMediaCodecCheckListener.onCodecCheckFail(-1);
                this.checkListener = null;
            }
            return -1;
        }
    }

    @Override // com.tencentcs.iotvideo.iotvideoplayer.codec.IVideoEncoder
    public void release() {
        FileOutputStream fileOutputStream = this.mIoStream;
        if (fileOutputStream != null) {
            try {
                fileOutputStream.close();
            } catch (IOException e10) {
                LogUtils.e(TAG, "release exception:" + e10.getMessage());
            }
            this.mIoStream = null;
        }
        FileOutputStream fileOutputStream2 = this.mEncodeStream;
        if (fileOutputStream2 != null) {
            try {
                fileOutputStream2.close();
            } catch (IOException e11) {
                LogUtils.e(TAG, "release exception:" + e11.getMessage());
            }
            this.mEncodeStream = null;
        }
        releaseEncode();
    }

    @Override // com.tencentcs.iotvideo.iotvideoplayer.codec.IVideoEncoder
    public synchronized int send_frame(AVData aVData) {
        IMediaCodecCheckListener iMediaCodecCheckListener;
        try {
            aVData.data.position(0);
            byte[] bArr = new byte[aVData.size];
            aVData.data.get(bArr);
            aVData.data.position(0);
            aVData.data1.position(0);
            byte[] bArr2 = new byte[aVData.size1];
            aVData.data1.get(bArr2);
            aVData.data1.position(0);
            aVData.data2.position(0);
            byte[] bArr3 = new byte[aVData.size2];
            aVData.data2.get(bArr3);
            aVData.data2.position(0);
            int i10 = aVData.size;
            byte[] bArr4 = new byte[aVData.size1 + i10 + aVData.size2];
            System.arraycopy(bArr, 0, bArr4, 0, i10);
            System.arraycopy(bArr2, 0, bArr4, aVData.size, aVData.size1);
            System.arraycopy(bArr3, 0, bArr4, aVData.size + aVData.size1, aVData.size2);
            if (this.mSupportTypes == 21) {
                IoTYUVKits.yv12ToNv21(bArr4, this.nv21Bytes, this.width, this.height);
                bArr4 = this.nv21Bytes;
            }
            FileOutputStream fileOutputStream = this.mIoStream;
            if (fileOutputStream != null) {
                fileOutputStream.write(bArr4);
            }
            int dequeueInputBuffer = this.mEncoder.dequeueInputBuffer(100L);
            if (dequeueInputBuffer >= 0) {
                ByteBuffer inputBuffer = this.mEncoder.getInputBuffer(dequeueInputBuffer);
                if (inputBuffer != null && aVData.data != null && aVData.size > 0) {
                    inputBuffer.clear();
                    inputBuffer.put(bArr4);
                    this.mEncoder.queueInputBuffer(dequeueInputBuffer, 0, bArr4.length, System.nanoTime() - this.mPts, 0);
                }
                LogUtils.e(TAG, "send_frame error, inputBuffer is null");
                return -11;
            }
            this.currentSendFrameCount = 0;
            return 0;
        } catch (Exception e10) {
            LogUtils.e(TAG, "send_frame cache " + e10.getMessage());
            e10.printStackTrace();
            int i11 = this.currentSendFrameCount + 1;
            this.currentSendFrameCount = i11;
            if (i11 > 20 && (iMediaCodecCheckListener = this.checkListener) != null) {
                iMediaCodecCheckListener.onCodecCheckFail(-1);
                this.checkListener = null;
            }
            return -1;
        }
    }

    public void setCheckCodecListener(IMediaCodecCheckListener iMediaCodecCheckListener) {
        this.checkListener = iMediaCodecCheckListener;
    }

    public void setCodecListener(IMediaCodecListener iMediaCodecListener) {
        this.codecListener = iMediaCodecListener;
    }

    public MediaCodecVideoEncoder(boolean z10) {
        this.mPts = 0L;
        this.mBufferInfo = new MediaCodec.BufferInfo();
        this.nv21Bytes = null;
        this.buffer = null;
        this.currentSendFrameCount = 0;
        this.currentReceiveFrameCount = 0;
        this.isSupportEncode = z10;
    }
}
