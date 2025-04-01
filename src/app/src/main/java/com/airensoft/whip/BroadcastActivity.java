package com.airensoft.whip;

import static android.os.Environment.DIRECTORY_MOVIES;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import org.webrtc.EglBase;
import org.webrtc.FileVideoCapturer;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.RTCStatsReport;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.util.Pair;

public class BroadcastActivity extends AppCompatActivity implements PeerConnectionEvents {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Nullable
    private PeerConnectionClient peerConnectionClient = null;

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

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_broadcast);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        _sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        createPeerConnectionClient();
    }

    private PeerConnectionParameters createPeerConnectionParameters() {
        Pair<Integer, Integer> videoSize = PeerConnectionClientUtil.GetVideoSize(_sharedPreferences.getString(Constants.INTENT_VIDEO_RES, "default"));

        return new PeerConnectionParameters(
                // videoCallEnabled
                true,
                // tracing
                false,
                // videoWidth
                videoSize.first,
                // videoHeight
                videoSize.second,
                // videoFps
                Integer.parseInt(_sharedPreferences.getString(Constants.INTENT_VIDEO_FRAMERATE, "30")),
                // videoMaxBitrate
                Integer.parseInt(_sharedPreferences.getString(Constants.INTENT_VIDEO_BITRATE, "1000000")),
                // Prefer VideoCodec
                _sharedPreferences.getString(Constants.INTENT_VIDEO_CODEC, ""),
                // maxBFrames
                Integer.parseInt(_sharedPreferences.getString(Constants.INTENT_VIDEO_BFRAMES, "0")),
                // videoCodecHwAcceleration
                true,
                // videoFlexfecEnabled
                false,
                // simulcastEnabled
                _sharedPreferences.getBoolean(Constants.INTENT_VIDEO_SIMULCAST,false),
                // audioStartBitrate
                Integer.parseInt(_sharedPreferences.getString(Constants.INTENT_AUDIO_BITRATE, "64000")),
                // PreferAudioCodec
                _sharedPreferences.getString(Constants.INTENT_AUDIO_CODEC, ""),
                // noAudioProcessing
                true,
                // disableBuiltInAEC
                true,
                // disableBuiltInAGC
                true,
                // disableBuiltInNS
                true,
                // disableWebRtcAGCAndHPF
                true,
                // enableRtcEventLog
                false,
                // enableCpuOveruseDetection
                _sharedPreferences.getBoolean(Constants.INTENT_VIDEO_CPU_OVERUSE_DETECTION, false)
        );
    }

    private List<PeerConnection.IceServer> loadTurnServer() {
        List<PeerConnection.IceServer> turnServers = new ArrayList<>();

        // Read TURN server information stored in Preference
        if (_sharedPreferences != null && _sharedPreferences.contains(Constants.INTENT_TURN_URLS)) {
            String turnUrls = _sharedPreferences.getString(Constants.INTENT_TURN_URLS, "");

            List<String> turns = Arrays.asList(turnUrls.split("\\|"));

            for (String turn : turns) {
                if (turn.isEmpty()) continue;

                String[] tokens = turn.split("; ");
                Log.d(getClass().getName(), turn);
                String url = tokens[0].replace("<", "").replace(">", "");
                String username = tokens[2].split("=")[1].replaceAll("\"", "");
                String credential = tokens[3].split("=")[1].replaceAll("\"", "");
                PeerConnection.IceServer.Builder bulder2 = PeerConnection.IceServer.builder(url);
                turnServers.add(bulder2.setUsername(username).setPassword(credential).setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_SECURE).createIceServer());
            }
        }

        return turnServers;
    }

    private void createPeerConnectionClient() {
        releasePeerConnectionClient();

        // Media Source
        try {
            String sourceUrl = Environment.getExternalStoragePublicDirectory(DIRECTORY_MOVIES) + "/" + _sharedPreferences.getString(Constants.INTENT_CAPTURER_SOURCE, "test2.y4m");

            // File Check
            File file = new File(sourceUrl);
            if(!file.exists())
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(BroadcastActivity.this)
                        .setMessage(String.format("Not found file : %s", sourceUrl))
                        .setPositiveButton("Go back", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                finish();
                            }
                        });
                AlertDialog msgDialog = builder.create();
                msgDialog.show();
                return;
            }

            _videoCapturer = new FileVideoCapturer(sourceUrl);
        } catch (IOException e) {
            Log.e(getClass().getName(), "Failed to open video file for emulated camera " + e.getMessage());
            return;
        }

        // Set Renderer
        surfaceRenderer = findViewById(R.id.surfaceView);
        surfaceRenderer.init(eglBase.getEglBaseContext(), null);
        surfaceRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL, RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        surfaceRenderer.setEnableHardwareScaler(true /* enabled */);

        // Video Sink to Surface Render
        localProxyVideoSink = new ProxyVideoSink();
        localProxyVideoSink.setTarget(surfaceRenderer);

        // Read TURN server information stored in Preference
        _turnServers = loadTurnServer();

        // Create PeerConnection
        PeerConnectionParameters peerConnectionParameters = createPeerConnectionParameters();
        peerConnectionClient = new PeerConnectionClient(getApplicationContext(), eglBase, peerConnectionParameters, BroadcastActivity.this);
        peerConnectionClient.createPeerConnection(localProxyVideoSink, _videoCapturer, _turnServers);
        peerConnectionClient.createOffer();
    }

    private static class ProxyVideoSink implements VideoSink {
        private VideoSink target;

        @Override
        synchronized public void onFrame(VideoFrame frame) {
            if (target == null) {
                return;
            }

            target.onFrame(frame);
        }

        synchronized public void setTarget(VideoSink target) {
            this.target = target;
        }
    }

    private void releasePeerConnectionClient() {
        if (peerConnectionClient != null) {
            peerConnectionClient.stopVideoSource();
            peerConnectionClient.close();
            peerConnectionClient = null;
        }
    }

    @Override
    protected void onStop() {
        releasePeerConnectionClient();

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
