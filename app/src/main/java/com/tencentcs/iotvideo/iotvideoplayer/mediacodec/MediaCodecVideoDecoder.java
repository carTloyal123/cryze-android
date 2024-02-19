package com.tencentcs.iotvideo.iotvideoplayer.mediacodec;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.view.Surface;
import com.tencentcs.iotvideo.iotvideoplayer.codec.AVData;
import com.tencentcs.iotvideo.iotvideoplayer.codec.AVHeader;
import com.tencentcs.iotvideo.iotvideoplayer.codec.IVideoDecoder;
import com.tencentcs.iotvideo.utils.LogUtils;
import java.io.IOException;
import java.nio.ByteBuffer;

/* loaded from: classes11.dex */
public class MediaCodecVideoDecoder implements IVideoDecoder {
    private static final long DEFAULT_TIMEOUT_US = 20000;
    private static final String TAG = "MediaCodecVideoDecoder";
    private static final boolean VERBOSE = false;
    private MediaCodec codec;
    private IMediaCodecListener codecListener;
    private boolean hasNotifyCodecSupportState = false;
    private MediaFormat mMediaFormat;

    private void image2AVData(Image image, AVData aVData) {
        LogUtils.d(TAG, "image2AVData: image: " + image.getFormat() + ", h: " + image.getHeight() + ", w: " + image.getWidth()+  "avdata: " + aVData.toString());
        int i11;
        Rect cropRect = image.getCropRect();
        int format = image.getFormat();
        int width = cropRect.width();
        int height = cropRect.height();
        aVData.width = width;
        aVData.height = height;
        Image.Plane[] planes = image.getPlanes();
        int i12 = width * height;
        byte[] bArr = new byte[(ImageFormat.getBitsPerPixel(format) * i12) / 8];
        int i13 = 0;
        byte[] bArr2 = new byte[planes[0].getRowStride()];
        int i14 = 0;
        int i15 = 0;
        while (i14 < planes.length) {
            if (i14 == 0) {
                i15 = i13;
            } else if (i14 == 1) {
                i15 = i12;
            } else if (i14 == 2) {
                i15 = (int) (i12 * 1.25d);
            }
            ByteBuffer buffer = planes[i14].getBuffer();
            int rowStride = planes[i14].getRowStride();
            int pixelStride = planes[i14].getPixelStride();
            int i16 = i14 == 0 ? i13 : 1;
            int i17 = width >> i16;
            int i18 = height >> i16;
            buffer.position(((cropRect.top >> i16) * rowStride) + ((cropRect.left >> i16) * pixelStride));
            int i20 = 0;
            while (i20 < i18) {
                if (pixelStride == 1) {
                    buffer.get(bArr, i15, i17);
                    i15 += i17;
                    i11 = i17;
                } else {
                    i11 = ((i17 - 1) * pixelStride) + 1;
                    buffer.get(bArr2, 0, i11);
                    for (int i21 = 0; i21 < i17; i21++) {
                        bArr[i15] = bArr2[i21 * pixelStride];
                        i15++;
                    }
                }
                if (i20 < i18 - 1) {
                    buffer.position((buffer.position() + rowStride) - i11);
                }
                i20++;
            }
            ByteBuffer byteBuffer = aVData.data;
            if (byteBuffer == null) {
                LogUtils.e(TAG, "null == outFrame.data");
                return;
            }
            if (i14 == 0) {
                aVData.size = i15;
                byteBuffer.position(0);
                aVData.data.put(bArr, 0, aVData.size);
                aVData.data.position(0);
            } else {
                if (i14 == 1) {
                    aVData.size1 = i15 - aVData.size;
                    aVData.data1.position(0);
                    aVData.data1.put(bArr, aVData.size, aVData.size1);
                    aVData.data1.position(0);
                } else if (i14 == 2) {
                    aVData.size2 = (i15 - aVData.size) - aVData.size1;
                    aVData.data2.position(0);
                    aVData.data2.put(bArr, aVData.size + aVData.size1, aVData.size2);
                    aVData.data2.position(0);
                }
            }
            i14++;
        }
    }

    private void notifyMediaCodecSupportState(int i10, String str) {
        IMediaCodecListener iMediaCodecListener = this.codecListener;
        if (iMediaCodecListener == null || this.hasNotifyCodecSupportState) {
            return;
        }
        iMediaCodecListener.onInitResult(i10, str);
        this.hasNotifyCodecSupportState = true;
    }

    @Override // com.tencentcs.iotvideo.iotvideoplayer.codec.IVideoDecoder
    public void init(AVHeader aVHeader) {
        LogUtils.i(TAG, "MediaCodecVideoDecoder init!");
        try {
            this.codec = null;
            try {
                String videoMineByAVHeader = MediaConstant.getVideoMineByAVHeader(aVHeader);
                try {
                    this.codec = MediaCodec.createDecoderByType(videoMineByAVHeader);
                    int integer = aVHeader.getInteger("width", 0);
                    int integer2 = aVHeader.getInteger("height", 0);
                    int integer3 = aVHeader.getInteger(AVHeader.KEY_FRAME_RATE, 20);
                    MediaFormat createVideoFormat = MediaFormat.createVideoFormat(videoMineByAVHeader, integer, integer2);
                    createVideoFormat.setInteger(AVHeader.KEY_FRAME_RATE, integer3);
                    createVideoFormat.setInteger("color-format", 19);
                    try {
                        this.codec.configure(createVideoFormat, (Surface) null, (MediaCrypto) null, 0);
                        LogUtils.i(TAG, "init format = " + createVideoFormat);
                        this.mMediaFormat = this.codec.getOutputFormat();
                        this.codec.start();
                    } catch (Exception e10) {
                        LogUtils.e(TAG, "init configure exception:" + e10.getMessage());
                        notifyMediaCodecSupportState(101, e10.getMessage());
                    }
                } catch (IOException e11) {
                    LogUtils.e(TAG, "init create codec exception:" + e11.getMessage());
                    notifyMediaCodecSupportState(100, e11.getMessage());
                }
            } catch (IllegalArgumentException e12) {
                LogUtils.e(TAG, "getVideoMineByAVHeader exception:" + e12.getMessage());
                notifyMediaCodecSupportState(100, e12.getMessage());
            }
        } catch (Exception e13) {
            LogUtils.e(TAG, "init exception:" + e13.getMessage());
            this.codec = null;
            notifyMediaCodecSupportState(102, e13.getMessage());
        }
    }

    @Override // com.tencentcs.iotvideo.iotvideoplayer.codec.IVideoDecoder
    public int receive_frame(AVData aVData) {
        LogUtils.d(TAG, "MediaCodecVideoDecoder receive_frame! " + aVData.toString());
        MediaCodec.BufferInfo bufferInfo = null;
        int dequeueOutputBuffer = 0;
        try {
            bufferInfo = new MediaCodec.BufferInfo();
            dequeueOutputBuffer = this.codec.dequeueOutputBuffer(bufferInfo, 20000L);
        } catch (Exception e10) {
            LogUtils.e(TAG, "receive_frame exception:" + e10.getMessage());
            notifyMediaCodecSupportState(102, e10.getMessage());
        }
        if (dequeueOutputBuffer < 0) {
            if (dequeueOutputBuffer != -1 && dequeueOutputBuffer == -2) {
                this.mMediaFormat = this.codec.getOutputFormat();
            }
            return -11;
        }
        Image outputImage = this.codec.getOutputImage(dequeueOutputBuffer);
        if (outputImage == null) {
            LogUtils.e(TAG, "receive_frame error, image is null");
            return -11;
        }
        image2AVData(outputImage, aVData);
        long j10 = bufferInfo.presentationTimeUs;
        aVData.pts = j10;
        aVData.dts = j10;
        aVData.keyFrame = bufferInfo.flags & 1;
        this.codec.releaseOutputBuffer(dequeueOutputBuffer, false);
        if (!this.hasNotifyCodecSupportState) {
            notifyMediaCodecSupportState(0, "");
        }
        return 0;
    }

    @Override // com.tencentcs.iotvideo.iotvideoplayer.codec.IVideoDecoder
    public void release() {
        LogUtils.i(TAG, "release");
        this.hasNotifyCodecSupportState = false;
        MediaCodec mediaCodec = this.codec;
        if (mediaCodec != null) {
            mediaCodec.stop();
            this.codec.release();
        }
    }

    @Override // com.tencentcs.iotvideo.iotvideoplayer.codec.IVideoDecoder
    public int send_packet(AVData aVData) {
        LogUtils.d(TAG, "MediaCodecVideoDecoder send_packet! " + aVData.toString());

        try {
            MediaCodec mediaCodec = this.codec;
            if (mediaCodec == null) {
                LogUtils.e(TAG, "send_packet error:codec is null");
                return -11;
            }
            int dequeueInputBuffer = mediaCodec.dequeueInputBuffer(20000L);
            if (dequeueInputBuffer >= 0) {
                ByteBuffer inputBuffer = this.codec.getInputBuffer(dequeueInputBuffer);
                if (inputBuffer == null) {
                    LogUtils.e(TAG, "send_packet error, inputBuffer is null");
                    return -11;
                }
                inputBuffer.clear();
                inputBuffer.put(aVData.data);
                this.codec.queueInputBuffer(dequeueInputBuffer, 0, aVData.size, aVData.pts, 0);
                return 0;
            }
            LogUtils.e(TAG, "send_packet error, try again later " + aVData.pts);
            return -1;
        } catch (Exception e10) {
            LogUtils.e(TAG, "send_packet exception:" + e10.getMessage());
            notifyMediaCodecSupportState(102, e10.getMessage());
            return -11;
        }
    }

    public void setCodecListener(IMediaCodecListener iMediaCodecListener) {
        this.codecListener = iMediaCodecListener;
    }
}
