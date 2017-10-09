package com.pedro.builder.rtsp;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.SurfaceView;
import com.pedro.builder.base.BuilderSurfaceModeBase;
import com.pedro.rtsp.rtsp.Protocol;
import com.pedro.rtsp.rtsp.RtspClient;
import com.pedro.rtsp.utils.ConnectCheckerRtsp;
import java.nio.ByteBuffer;

/**
 * Created by pedro on 4/06/17.
 * This builder is under test, rotation only work with hardware because use encoding surface mode.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class RtspBuilderSurfaceMode extends BuilderSurfaceModeBase {

  private RtspClient rtspClient;

  public RtspBuilderSurfaceMode(SurfaceView surfaceView, Protocol protocol,
      ConnectCheckerRtsp connectCheckerRtsp) {
    super(surfaceView, surfaceView.getContext());
    rtspClient = new RtspClient(connectCheckerRtsp, protocol);
  }

  public RtspBuilderSurfaceMode(Context context, Protocol protocol, ConnectCheckerRtsp connectCheckerRtsp) {
    super(context);
    rtspClient = new RtspClient(connectCheckerRtsp, protocol);
  }

  @Override
  public void setAuthorization(String user, String password) {
    rtspClient.setAuthorization(user, password);
  }

  @Override
  protected void prepareAudioRtp(boolean isStereo, int sampleRate) {
    rtspClient.setIsStereo(isStereo);
    rtspClient.setSampleRate(sampleRate);
  }

  @Override
  public boolean prepareAudio() {
    microphoneManager.setSampleRate(16000);
    audioEncoder.setSampleRate(16000);
    microphoneManager.createMicrophone();
    rtspClient.setSampleRate(microphoneManager.getSampleRate());
    return audioEncoder.prepareAudioEncoder();
  }

  @Override
  protected void startStreamRtp(String url) {
    rtspClient.setUrl(url);
  }

  @Override
  protected void stopStreamRtp() {
    rtspClient.disconnect();
  }

  @Override
  protected void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
    rtspClient.sendAudio(aacBuffer, info);
  }

  @Override
  protected void onSPSandPPSRtp(ByteBuffer sps, ByteBuffer pps) {
    rtspClient.setSPSandPPS(sps, pps);
    rtspClient.connect();
  }

  @Override
  protected void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
    rtspClient.sendVideo(h264Buffer, info);
  }
}

