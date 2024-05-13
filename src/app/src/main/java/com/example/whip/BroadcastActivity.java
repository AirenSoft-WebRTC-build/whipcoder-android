package com.example.whip;

import static android.os.Environment.DIRECTORY_MOVIES;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import org.webrtc.EglBase;
import org.webrtc.FileVideoCapturer;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RTCStatsReport;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BroadcastActivity extends AppCompatActivity implements PeerConnectionClient.PeerConnectionEvents {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    private PeerConnectionClient peerConnectionClient = null;

    @Nullable
    private PeerConnectionParameters peerConnectionParameters = null;

    @Nullable
    private SurfaceViewRenderer surfaceRenderer;

    private VideoCapturer _videoCapturer = null;

    private ProxyVideoSink localProxyVideoSink = null;

    private WHIPClient whipClient = null;

    List<PeerConnection.IceServer> _turnServers = null;

    final EglBase eglBase = EglBase.create();

    SharedPreferences _sharedPreferences = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_broadcast);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        _sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        createPeerConnectionClient();
    }

    private void createPeerConnectionClient() {
        if (peerConnectionClient != null) {
            peerConnectionClient.stopVideoSource();
            peerConnectionClient.close();
            peerConnectionClient = null;
        }

        int videoWidth = 0;
        int videoHeight = 0;
        switch (_sharedPreferences.getString(Constants.INTENT_VIDEO_RES, "default")) {
            case "3840x2160":
                videoWidth = 3840;
                videoHeight = 2160;
                break;
            case "1920x1080":
                videoWidth = 1920;
                videoHeight = 1080;
                break;
            case "1280x720":
                videoWidth = 1280;
                videoHeight = 720;
                break;
            case "640x480":
                videoWidth = 640;
                videoHeight = 480;
                break;
            case "320x240":
                videoWidth = 320;
                videoHeight = 240;
                break;
            default:
                break;
        }

        peerConnectionParameters = new PeerConnectionParameters(
                true,               // videoCallEnabled
                false,              // tracing
                videoWidth,                // videoWidth
                videoHeight,               // videoHeight
                Integer.parseInt(_sharedPreferences.getString(Constants.INTENT_VIDEO_FRAMERATE, "30")),       // videoFps
                Integer.parseInt(_sharedPreferences.getString(Constants.INTENT_VIDEO_BITRATE, "1000000")),    // videoMaxBitrate
                _sharedPreferences.getString(Constants.INTENT_VIDEO_CODEC, ""),        // videoCodec
                Integer.parseInt(_sharedPreferences.getString(Constants.INTENT_VIDEO_BFRAMES, "0")),          // maxBFrames
                true,               // videoCodecHwAcceleration
                false,              // videoFlexfecEnabled
                Integer.parseInt(_sharedPreferences.getString(Constants.INTENT_AUDIO_BITRATE, "64000")),      // audioStartBitrate
                _sharedPreferences.getString(Constants.INTENT_AUDIO_CODEC, ""),        // audioCodec
                true,               // noAudioProcessing
                false,              // saveInputAudioToFile
                true,               // disableBuiltInAEC
                true,               // disableBuiltInAGC
                true,               // disableBuiltInNS
                true,               // disableWebRtcAGCAndHPF
                false,              // enableRtcEventLog
                _sharedPreferences.getBoolean(Constants.INTENT_VIDEO_CPU_OVERUSE_DETECTION, false) // enableCpuOveruseDetection
        );

        peerConnectionClient = new PeerConnectionClient(getApplicationContext(), eglBase, peerConnectionParameters, BroadcastActivity.this);
        peerConnectionClient.createPeerConnectionFactory(new PeerConnectionFactory.Options());

        try {
            _videoCapturer = new FileVideoCapturer(Environment.getExternalStoragePublicDirectory(DIRECTORY_MOVIES) + "/test.y4m");
        } catch (IOException e) {
            Log.e(getClass().getName(), e.getMessage());
            Log.e(getClass().getName(), "Failed to open video file for emulated camera");
            return;
        }

        surfaceRenderer = findViewById(R.id.surfaceView);
        surfaceRenderer.init(eglBase.getEglBaseContext(), null);
        surfaceRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_BALANCED);
        surfaceRenderer.setEnableHardwareScaler(true /* enabled */);

        // Video Sink to Surface Render
        localProxyVideoSink = new ProxyVideoSink();
        localProxyVideoSink.setTarget(surfaceRenderer);

        // Read TURN server information stored in Preference
        _turnServers = loadTurnServer();

        peerConnectionClient.createPeerConnection(localProxyVideoSink, _videoCapturer, _turnServers);
        peerConnectionClient.createOffer();
    }

    private List<PeerConnection.IceServer> loadTurnServer() {
        List<PeerConnection.IceServer> turnServers = new ArrayList<>();

        // Read TURN server information stored in Preference
        if (_sharedPreferences != null && _sharedPreferences.contains(Constants.INTENT_TURN_URLS)) {
            String turnUrls = _sharedPreferences.getString(Constants.INTENT_TURN_URLS, "");
            Log.e(getClass().getName(), turnUrls);

            List<String> turns = Arrays.asList(turnUrls.split("\\|"));


            for (String turn : turns) {
                if (turn.isEmpty())
                    continue;

                Log.e(getClass().getName(), turn);

                String[] tokens = turn.split("; ");
                Log.d(getClass().getName(), turn);
                String url = tokens[0].replace("<", "").replace(">", "");
                String username = tokens[2].split("=")[1].replaceAll("\"", "");
                String credential = tokens[3].split("=")[1].replaceAll("\"", "");

                Log.e(getClass().getName(), String.format("%s %s %s", url, username, credential));
                PeerConnection.IceServer.Builder bulder2 = PeerConnection.IceServer.builder(url);
                turnServers.add(bulder2.setUsername(username).setPassword(credential).setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_SECURE).createIceServer());
            }
        }

        return _turnServers;
    }

    private static class ProxyVideoSink implements VideoSink {
        private VideoSink target;

        @Override
        synchronized public void onFrame(VideoFrame frame) {
            if (target == null) {
//                Logging.d(getClass().getName(), "Dropping frame in proxy because target is null.");
                return;
            }

            target.onFrame(frame);
        }

        synchronized public void setTarget(VideoSink target) {
            this.target = target;
        }
    }


    @Override
    protected void onStop() {
        if (peerConnectionClient != null) {
            peerConnectionClient.stopVideoSource();
            peerConnectionClient.close();
            peerConnectionClient = null;
        }

        if (whipClient != null) {
            whipClient.Delete();
        }
        super.onStop();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /***********************************************************************************************
     * PEER CONNECTION HANDLER
     **********************************************************************************************/
    @Override
    public void onLocalDescription(SessionDescription sdp) {
        Log.d(getClass().getName(), "onLocalDescription");
        Log.d(getClass().getName(), sdp.description);

        // Received TURN server information from WHIP
        whipClient = new WHIPClient();
        whipClient.setURL(_sharedPreferences.getString(Constants.INTENT_STREAM_URL, ""));
        whipClient.setLocalSDP(sdp.description);
        if (!whipClient.Create()) {
            return;
        }

        //Store TURN server in Preference
        String join = String.join("|", whipClient.GetLink());
        if (_sharedPreferences != null) {
            SharedPreferences.Editor editor = _sharedPreferences.edit();
            editor.putString("turn_urls", join);
            editor.commit();
        }

        SessionDescription remoteSdp = new SessionDescription(SessionDescription.Type.ANSWER, whipClient.getRemoteSDP());
        peerConnectionClient.setRemoteDescription(remoteSdp);
    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {
        Log.d(getClass().getName(), "onIceCandidate: " + candidate.toString());
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {
        Log.d(getClass().getName(), "onIceCandidatesRemoved");
    }

    @Override
    public void onIceConnected() {
        Log.d(getClass().getName(), "onIceConnected");
    }

    @Override
    public void onIceDisconnected() {
        Log.d(getClass().getName(), "onIceDisconnected");
    }

    @Override
    public void onConnected() {
        Log.d(getClass().getName(), "onConnected");
    }

    @Override
    public void onDisconnected() {
        Log.d(getClass().getName(), "onDisconnected");
    }

    @Override
    public void onPeerConnectionClosed() {
        Log.d(getClass().getName(), "onPeerConnectionClosed");
    }

    @Override
    public void onPeerConnectionStatsReady(RTCStatsReport report) {
        Log.d(getClass().getName(), "onPeerConnectionStatsReady");
    }

    @Override
    public void onPeerConnectionError(String description) {
        Log.d(getClass().getName(), "onPeerConnectionError");

    }
}
