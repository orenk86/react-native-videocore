package com.pedro.builder.base;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.SurfaceView;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAacData;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.audio.MicrophoneManager;
import com.pedro.encoder.input.video.Camera1ApiManager;
import com.pedro.encoder.input.video.CameraOpenException;
import com.pedro.encoder.input.video.EffectManager;
import com.pedro.encoder.input.video.GetCameraData;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetH264Data;
import com.pedro.encoder.video.VideoEncoder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by pedro on 7/07/17.
 */

public abstract class BuilderBase
    implements GetAacData, GetCameraData, GetH264Data, GetMicrophoneData {

  private Camera1ApiManager cameraManager;
  protected VideoEncoder videoEncoder;
  protected MicrophoneManager microphoneManager;
  protected AudioEncoder audioEncoder;
  private boolean streaming;
  private boolean videoEnabled = true;
  //record
  private MediaMuxer mediaMuxer;
  private int videoTrack = -1;
  private int audioTrack = -1;
  private boolean recording = false;
  private MediaFormat videoFormat;
  private MediaFormat audioFormat;

  public BuilderBase(SurfaceView surfaceView) {
    cameraManager = new Camera1ApiManager(surfaceView, this);
    videoEncoder = new VideoEncoder(this);
    microphoneManager = new MicrophoneManager(this);
    audioEncoder = new AudioEncoder(this);
    streaming = false;
  }

  public abstract void setAuthorization(String user, String password);

  public boolean prepareVideo(int width, int height, int fps, int bitrate, boolean hardwareRotation,
      int rotation) {
    int imageFormat = ImageFormat.NV21; //supported nv21 and yv12
    cameraManager.prepareCamera(width, height, fps, imageFormat);
    videoEncoder.setImageFormat(imageFormat);
    return videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation, hardwareRotation,
        FormatVideoEncoder.YUV420Dynamical);
  }

  protected abstract void prepareAudioRtp(boolean isStereo, int sampleRate);

  public boolean prepareAudio(int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
      boolean noiseSuppressor) {
    microphoneManager.createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor);
    prepareAudioRtp(isStereo, sampleRate);
    return audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo);
  }

  public boolean prepareVideo() {
    cameraManager.prepareCamera();
    return videoEncoder.prepareVideoEncoder();
  }

  public abstract boolean prepareAudio();

  /*Need be called while stream*/
  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void startRecord(String path) throws IOException {
    if (streaming) {
      mediaMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
      videoTrack = mediaMuxer.addTrack(videoFormat);
      audioTrack = mediaMuxer.addTrack(audioFormat);
      mediaMuxer.start();
      recording = true;
    } else {
      throw new IOException("Need be called while stream");
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  public void stopRecord() {
    recording = false;
    if (mediaMuxer != null) {
      mediaMuxer.stop();
      mediaMuxer.release();
      mediaMuxer = null;
    }
    videoTrack = -1;
    audioTrack = -1;
  }

  public void startCamera() {
    cameraManager.start();
  }

  public void stopCamera() {
    cameraManager.stop();
  }

  protected abstract void startStreamRtp(String url);

  public void startStream(String url) {
    startStreamRtp(url);
    videoEncoder.start();
    audioEncoder.start();
    cameraManager.stop();
    cameraManager.start();
    microphoneManager.start();
    streaming = true;
  }

  protected abstract void stopStreamRtp();

  public void stopStream() {
//    cameraManager.stop();
    microphoneManager.stop();
    stopStreamRtp();
    videoEncoder.stop();
    audioEncoder.stop();
    streaming = false;
  }

  public List<String> getResolutions() {
    List<Camera.Size> list = cameraManager.getPreviewSize();
    List<String> resolutions = new ArrayList<>();
    for (Camera.Size size : list) {
      resolutions.add(size.width + "X" + size.height);
    }
    return resolutions;
  }

  public void disableAudio() {
    microphoneManager.mute();
  }

  public void enableAudio() {
    microphoneManager.unMute();
  }

  public boolean isAudioMuted() {
    return microphoneManager.isMuted();
  }

  public boolean isVideoEnabled() {
    return videoEnabled;
  }

  public void disableVideo() {
    videoEncoder.startSendBlackImage();
    videoEnabled = false;
  }

  public void enableVideo() {
    videoEncoder.stopSendBlackImage();
    videoEnabled = true;
  }

  public void switchCamera() throws CameraOpenException {
    if (isVideoEnabled()) {
      cameraManager.switchCamera();
    }
  }

  /** need min API 19 */
  public void setVideoBitrateOnFly(int bitrate) {
    if (Build.VERSION.SDK_INT >= 19) {
      videoEncoder.setVideoBitrateOnFly(bitrate);
    }
  }

  public boolean isStreaming() {
    return streaming;
  }

  public boolean isRecording() {
    return recording;
  }

  public void setEffect(EffectManager effect) {
    if (isStreaming()) {
      cameraManager.setEffect(effect);
    }
  }

  protected abstract void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info);

  @Override
  public void getAacData(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && recording) {
      mediaMuxer.writeSampleData(audioTrack, aacBuffer, info);
    }
    getAacDataRtp(aacBuffer, info);
  }

  protected abstract void onSPSandPPSRtp(ByteBuffer sps, ByteBuffer pps);

  @Override
  public void onSPSandPPS(ByteBuffer sps, ByteBuffer pps) {
    onSPSandPPSRtp(sps, pps);
  }

  protected abstract void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info);

  @Override
  public void getH264Data(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && recording) {
      mediaMuxer.writeSampleData(videoTrack, h264Buffer, info);
    }
    getH264DataRtp(h264Buffer, info);
  }

  @Override
  public void inputPcmData(byte[] buffer, int size) {
    audioEncoder.inputPcmData(buffer, size);
  }

  @Override
  public void inputYv12Data(byte[] buffer) {
    videoEncoder.inputYv12Data(buffer);
  }

  @Override
  public void inputNv21Data(byte[] buffer) {
    videoEncoder.inputNv21Data(buffer);
  }

  @Override
  public void onVideoFormat(MediaFormat mediaFormat) {
    videoFormat = mediaFormat;
  }

  @Override
  public void onAudioFormat(MediaFormat mediaFormat) {
    audioFormat = mediaFormat;
  }

  public void toggleTorch() {
    if (cameraManager.isLanternEnable()) {
      cameraManager.disableLantern();
    } else {
      cameraManager.enableLantern();
    }
  }
}
