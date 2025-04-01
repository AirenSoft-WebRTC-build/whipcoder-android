/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package com.airensoft.whip;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.util.Log;

import androidx.annotation.Nullable;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.BuiltinAudioEncoderFactoryFactory;
import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.SimulcastVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.IceCandidateErrorEvent;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnection.PeerConnectionState;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RTCStatsCollectorCallback;
import org.webrtc.RTCStatsReport;
import org.webrtc.RtpCapabilities;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule.AudioTrackErrorCallback;
import org.webrtc.audio.JavaAudioDeviceModule.AudioTrackStateCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeerConnectionClient {
    private static final String TAG = "PCRTCClient";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final PCObserver pcObserver = new PCObserver();
    private final SDPObserver sdpObserver = new SDPObserver();
    private final Timer statsTimer = new Timer();
    private final EglBase rootEglBase;
    private final Context appContext;
    private final PeerConnectionParameters peerConnectionParameters;
    private final PeerConnectionEvents events;
    public List<PeerConnection.IceServer> _iceServers = new ArrayList<>();
    @Nullable
    private PeerConnectionFactory factory;
    @Nullable
    private PeerConnection peerConnection;
    @Nullable
    private AudioSource audioSource;
    @Nullable
    private SurfaceTextureHelper surfaceTextureHelper;
    @Nullable
    private VideoSource videoSource;
    private boolean videoCapturerStopped;
    private boolean isError;
    @Nullable
    private VideoSink localRender;
    private MediaConstraints audioConstraints;
    private MediaConstraints sdpMediaConstraints;
    private boolean isInitiator;
    @Nullable
    private SessionDescription localDescription; // either offer or answer description
    @Nullable
    private VideoCapturer videoCapturer;
    // enableVideo is set to true if video should be rendered and sent.
    private boolean renderVideo = true;
    @Nullable
    private VideoTrack localVideoTrack;
    // enableAudio is set to true if audio should be sent.
    private boolean enableAudio = true;
    @Nullable
    private AudioTrack localAudioTrack;

    public PeerConnectionClient(Context appContext,
                                EglBase eglBase,
                                PeerConnectionParameters parameters,
                                PeerConnectionEvents events) {
        this.rootEglBase = eglBase;
        this.appContext = appContext;
        this.events = events;
        this.peerConnectionParameters = parameters;

        // Initialize PeerConnectionFactory
        final String fieldTrials = PeerConnectionClientUtil.getFieldTrials(peerConnectionParameters);
        executor.execute(() -> {
            Log.d(TAG, "Initialize WebRTC. Field trials: " + fieldTrials);
            PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(appContext)
                            .setFieldTrials(fieldTrials)
                            .setEnableInternalTracer(true)
                            .createInitializationOptions());

            if (factory != null) {
                throw new IllegalStateException("PeerConnectionFactory has already been constructed");
            }
            createPeerConnectionFactoryInternal(new PeerConnectionFactory.Options());
        });
    }

    public void createPeerConnection(final VideoSink localRender, final VideoCapturer videoCapturer, List<PeerConnection.IceServer> iceServers) {
        if (peerConnectionParameters == null) {
            Log.e(TAG, "Creating peer connection without initializing factory.");
            return;
        }
        this.localRender = localRender;
        this.videoCapturer = videoCapturer;
        if (iceServers != null) {
            _iceServers.clear();
            _iceServers.addAll(iceServers);
        }
        executor.execute(() -> {
            try {
                createMediaConstraintsInternal();
                createPeerConnectionInternal();
            } catch (Exception e) {
                reportError("Failed to create peer connection: " + e.getMessage());
                throw e;
            }
        });
    }

    public void close() {
        executor.execute(this::closeInternal);
    }

    private boolean isVideoCallEnabled() {
        return peerConnectionParameters.videoCallEnabled && videoCapturer != null;
    }

    private void createPeerConnectionFactoryInternal(PeerConnectionFactory.Options options) {
        isError = false;

        final AudioDeviceModule adm = createJavaAudioDevice();

        // Create peer connection factory.
        if (options != null) {
            Log.d(TAG, "Factory networkIgnoreMask option: " + options.networkIgnoreMask);
        }

        final VideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
        final VideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(), false, true);
        final SimulcastVideoEncoderFactory simulcastFactory = new SimulcastVideoEncoderFactory(encoderFactory);

        factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(simulcastFactory) //.setVideoEncoderFactory(encoderFactory)
                .setAudioEncoderFactoryFactory(new BuiltinAudioEncoderFactoryFactory())
                .createPeerConnectionFactory();
        Log.d(TAG, "Peer connection factory created.");
    }

    AudioDeviceModule createJavaAudioDevice() {
        // Set audio record error callbacks.

        AudioTrackErrorCallback audioTrackErrorCallback = new AudioTrackErrorCallback() {
            @Override
            public void onWebRtcAudioTrackInitError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackInitError: " + errorMessage);
                reportError(errorMessage);
            }

            @Override
            public void onWebRtcAudioTrackStartError(
                    JavaAudioDeviceModule.AudioTrackStartErrorCode errorCode, String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackStartError: " + errorCode + ". " + errorMessage);
                reportError(errorMessage);
            }

            @Override
            public void onWebRtcAudioTrackError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackError: " + errorMessage);
                reportError(errorMessage);
            }
        };

        // Set audio track state callbacks.
        AudioTrackStateCallback audioTrackStateCallback = new AudioTrackStateCallback() {
            @Override
            public void onWebRtcAudioTrackStart() {
                Log.i(TAG, "Audio playout starts");
            }

            @Override
            public void onWebRtcAudioTrackStop() {
                Log.i(TAG, "Audio playout stops");
            }
        };

        return JavaAudioDeviceModule.builder(appContext)
                .setUseHardwareAcousticEchoCanceler(!peerConnectionParameters.disableBuiltInAEC)
                .setUseHardwareNoiseSuppressor(!peerConnectionParameters.disableBuiltInNS)
                .setAudioTrackErrorCallback(audioTrackErrorCallback)
                .setAudioTrackStateCallback(audioTrackStateCallback)
                .createAudioDeviceModule();
    }

    private void createMediaConstraintsInternal() {

        // Create audio constraints.
        audioConstraints = new MediaConstraints();
        // added for audio performance measurements
        if (peerConnectionParameters.noAudioProcessing) {
            Log.d(TAG, "Disabling audio processing");
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(PeerConnectionConstant.AUDIO_ECHO_CANCELLATION_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(PeerConnectionConstant.AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(PeerConnectionConstant.AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "false"));
            audioConstraints.mandatory.add(
                    new MediaConstraints.KeyValuePair(PeerConnectionConstant.AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "false"));
        }

        // Create SDP constraints.
        sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNumSimulcastLayers", "3"));

    }

    private PeerConnection.RTCConfiguration getRTCConfiguration() {
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(_iceServers);

        if (!_iceServers.isEmpty()) {
            Log.d(TAG, "Enable tcpCandidatePolicy");
            rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.RELAY;
            rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;
        } else {
            rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        }
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rtcConfig.suspendBelowMinBitrate = false;
        rtcConfig.enableCpuOveruseDetection = this.peerConnectionParameters.enableCpuOveruseDetection;
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        return rtcConfig;
    }

    private void createPeerConnectionInternal() {
        Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO);

        if (factory == null || isError) {
            Log.e(TAG, "Peerconnection factory is not created");
            return;
        }

        peerConnection = factory.createPeerConnection(getRTCConfiguration(), pcObserver);
        if (peerConnection == null) {
            Log.e(TAG, "Peerconnection is not created");
            return;
        }

        isInitiator = false;

//        AudioTrack audioTrack = createAudioTrack();
//        peerConnection.addTransceiver(audioTrack);

        if (isVideoCallEnabled()) {
            VideoTrack videoTrack = createVideoTrack(videoCapturer);

            //---------------------------------
            // Simulcast
            //---------------------------------
            RtpTransceiver.RtpTransceiverInit transceiverInit = null;

            if (peerConnectionParameters.videoSimulcastEnabled) {
                List<String> streamIds = new ArrayList<>();
                List<RtpParameters.Encoding> encodings = new ArrayList<>();
                RtpParameters.Encoding encodingLo = new RtpParameters.Encoding("low", true, 4.0);
                RtpParameters.Encoding encodingMid = new RtpParameters.Encoding("mid", true, 2.0);
                RtpParameters.Encoding encodingHi = new RtpParameters.Encoding("high", true, 1.0);
                encodingHi.minBitrateBps = 100_000;
                encodingHi.maxBitrateBps = 2_000_000;
                encodingHi.maxFramerate = 30;
                encodingMid.minBitrateBps = 100_000;
                encodingMid.maxBitrateBps = 1_000_000;
                encodingMid.maxFramerate = 15;
                encodingLo.minBitrateBps = 100_000;
                encodingLo.maxBitrateBps = 500_000;
                encodingLo.maxFramerate = 15;
                encodings.clear();
                encodings.add(encodingLo);
                encodings.add(encodingMid);
                encodings.add(encodingHi);
                transceiverInit = new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY, streamIds, encodings);
            }

            // Added Transceiver
            RtpTransceiver transceiver = peerConnection.addTransceiver(videoTrack, transceiverInit);
            if (transceiver == null || transceiver.getSender() == null) {
                Log.e(TAG, "RtpTransceiver is not created");
                return;
            }

            //---------------------------------
            // Get Available Codec List
            //---------------------------------
            final MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
            for (MediaCodecInfo info : mediaCodecList.getCodecInfos()) {
                if (info == null || !info.isEncoder()) {
                    continue;
                }
                Log.e(TAG, String.format("%s / %s / hw(%s)", info.getName(), info.getCanonicalName(), info.isHardwareAccelerated() ? "true" : "false"));
            }

            //---------------------------------
            // SetCodecPreference
            //---------------------------------
            List<RtpCapabilities.CodecCapability> codecPreference = new ArrayList<>(factory.getRtpSenderCapabilities(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO).codecs);
            for (RtpCapabilities.CodecCapability codec : codecPreference) {
                if (codec.name.equals(PeerConnectionClientUtil.getSdpVideoCodecName(peerConnectionParameters.videoCodec))) {
                    String profile_level_id = codec.parameters.get("profile-level-id");
                    String prefix_profile_level_id = PeerConnectionClientUtil.getProfileLevelId(peerConnectionParameters.videoCodec);
                    if (profile_level_id != null && prefix_profile_level_id != null) { // h.264 baseline / high
                        if (profile_level_id.contains(prefix_profile_level_id)) {
                            codecPreference.remove(codec); // move to first
                            codecPreference.add(0, codec);
                            break;
                        }
                    } else {    // other codec
                        codecPreference.remove(codec); // move to first
                        codecPreference.add(0, codec);
                        break;
                    }
                }
            }
            transceiver.setCodecPreferences(codecPreference);
        } // isVideoCallEnabled

        Log.d(TAG, "Peer connection created.");
    }

    private void closeInternal() {
        Log.d(TAG, "Closing peer connection.");
        statsTimer.cancel();

        if (peerConnection != null) {
            peerConnection.dispose();
            peerConnection = null;
        }
        Log.d(TAG, "Closing audio source.");
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }
        Log.d(TAG, "Stopping capture.");
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            videoCapturerStopped = true;
            videoCapturer.dispose();
            videoCapturer = null;
        }
        Log.d(TAG, "Closing video source.");
        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
        localRender = null;
        Log.d(TAG, "Closing peer connection factory.");
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
        rootEglBase.release();
        Log.d(TAG, "Closing peer connection done.");
        events.onPeerConnectionClosed();
        PeerConnectionFactory.stopInternalTracingCapture();
        PeerConnectionFactory.shutdownInternalTracer();
    }

    private void getStats() {
        if (peerConnection == null || isError) {
            return;
        }
        peerConnection.getStats(new RTCStatsCollectorCallback() {
            @Override
            public void onStatsDelivered(RTCStatsReport report) {
                events.onPeerConnectionStatsReady(report);
            }
        });
    }

    public void enableStatsEvents(boolean enable, int periodMs) {
        if (enable) {
            try {
                statsTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        executor.execute(() -> getStats());
                    }
                }, 0, periodMs);
            } catch (Exception e) {
                Log.e(TAG, "Can not schedule statistics timer", e);
            }
        } else {
            statsTimer.cancel();
        }
    }

    public void createOffer() {
        executor.execute(() -> {
            if (peerConnection != null && !isError) {
                isInitiator = true;
                peerConnection.createOffer(sdpObserver, sdpMediaConstraints);
                Log.d(TAG, "PC create OFFSER\n" + sdpMediaConstraints.toString());
            }
        });
    }

    public void setRemoteDescription(final SessionDescription desc) {
        executor.execute(() -> {
            if (peerConnection == null || isError) {
                return;
            }
            String sdp = desc.description;
            Log.d(TAG, "Set remote SDP.\n" + sdp);
            SessionDescription sdpRemote = new SessionDescription(desc.type, sdp);
            peerConnection.setRemoteDescription(sdpObserver, sdpRemote);
        });
    }

    public void stopVideoSource() {
        executor.execute(() -> {
            if (videoCapturer != null && !videoCapturerStopped) {
                Log.d(TAG, "Stop video source.");
                try {
                    videoCapturer.stopCapture();
                } catch (InterruptedException e) {
                }
                videoCapturerStopped = true;
            }
        });
    }

    private void reportError(final String errorMessage) {
        Log.e(TAG, "Peerconnection error: " + errorMessage);
        executor.execute(() -> {
            if (!isError) {
                events.onPeerConnectionError(errorMessage);
                isError = true;
            }
        });
    }

    @Nullable
    private AudioTrack createAudioTrack() {
        audioSource = factory.createAudioSource(audioConstraints);
        localAudioTrack = factory.createAudioTrack(PeerConnectionConstant.AUDIO_TRACK_ID, audioSource);
        localAudioTrack.setEnabled(enableAudio);
        return localAudioTrack;
    }

    @Nullable
    private VideoTrack createVideoTrack(VideoCapturer capturer) {
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
        videoSource = factory.createVideoSource(false);

        int videoWidth = peerConnectionParameters.videoWidth;
        int videoHeight = peerConnectionParameters.videoHeight;
        int videoFps = peerConnectionParameters.videoFps;

        // If video resolution is not specified, default to HD.
        if (videoWidth == 0 || videoHeight == 0) {
            videoWidth = PeerConnectionConstant.DEFAULT_VIDEO_WIDTH;
            videoHeight = PeerConnectionConstant.DEFAULT_VIDEO_HEIGHT;
        }
        // If fps is not specified, default to 30.
        if (videoFps == 0) {
            videoFps = PeerConnectionConstant.DEFAULT_VIDEO_FPS;
        }

        Logging.d(TAG, "Capturing format: " + videoWidth + "x" + videoHeight + "@" + videoFps);

        capturer.initialize(surfaceTextureHelper, appContext, videoSource.getCapturerObserver());
        capturer.startCapture(videoWidth, videoHeight, videoFps);

        localVideoTrack = factory.createVideoTrack(PeerConnectionConstant.VIDEO_TRACK_ID, videoSource);
        localVideoTrack.setEnabled(renderVideo);
        localVideoTrack.addSink(localRender);
        return localVideoTrack;
    }

    private class PCObserver implements PeerConnection.Observer {
        @Override
        public void onIceCandidate(final IceCandidate candidate) {
            executor.execute(() -> events.onIceCandidate(candidate));
        }

        @Override
        public void onIceCandidateError(final IceCandidateErrorEvent event) {
            Log.d(TAG, "IceCandidateError address: " + event.address + ", port: " + event.port + ", url: "
                    + event.url + ", errorCode: " + event.errorCode + ", errorText: " + event.errorText);
        }

        @Override
        public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
            executor.execute(() -> events.onIceCandidatesRemoved(candidates));
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState newState) {
            Log.d(TAG, "SignalingState: " + newState);
        }

        @Override
        public void onIceConnectionChange(final PeerConnection.IceConnectionState newState) {
            executor.execute(() -> {
                Log.d(TAG, "IceConnectionState: " + newState);
                if (newState == IceConnectionState.CONNECTED) {
                    events.onIceConnected();
                } else if (newState == IceConnectionState.DISCONNECTED) {
                    events.onIceDisconnected();
                } else if (newState == IceConnectionState.FAILED) {
                    reportError("ICE connection failed.");
                }
            });
        }

        @Override
        public void onConnectionChange(final PeerConnection.PeerConnectionState newState) {
            executor.execute(() -> {
                Log.d(TAG, "PeerConnectionState: " + newState);
                if (newState == PeerConnectionState.CONNECTED) {
                    events.onConnected();
                } else if (newState == PeerConnectionState.DISCONNECTED) {
                    events.onDisconnected();
                } else if (newState == PeerConnectionState.FAILED) {
                    reportError("DTLS connection failed.");
                }
            });
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
            Log.d(TAG, "IceGatheringState: " + newState);
        }

        @Override
        public void onIceConnectionReceivingChange(boolean receiving) {
            Log.d(TAG, "IceConnectionReceiving changed to " + receiving);
        }

        @Override
        public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {
            Log.d(TAG, "Selected candidate pair changed because: " + event);
        }

        @Override
        public void onAddStream(final MediaStream stream) {
        }

        @Override
        public void onRemoveStream(final MediaStream stream) {
        }

        @Override
        public void onDataChannel(final DataChannel dc) {
            Log.d(TAG, "New Data channel " + dc.label());
        }

        @Override
        public void onRenegotiationNeeded() {
            // No need to do anything; AppRTC follows a pre-agreed-upon signaling/negotiation protocol.
        }

        @Override
        public void onAddTrack(final RtpReceiver receiver, final MediaStream[] mediaStreams) {
        }

        @Override
        public void onRemoveTrack(final RtpReceiver receiver) {
        }
    }

    private class SDPObserver implements SdpObserver {
        @Override
        public void onCreateSuccess(final SessionDescription desc) {
            if (localDescription != null) {
                reportError("Multiple SDP create.");
                return;
            }
            String sdp = desc.description;

            final SessionDescription newDesc = new SessionDescription(desc.type, sdp);
            localDescription = newDesc;
            executor.execute(() -> {
                if (peerConnection != null && !isError) {
                    Log.d(TAG, "Set local SDP from  \n" + newDesc.description);
                    peerConnection.setLocalDescription(sdpObserver, newDesc);
                }
            });
        }

        @Override
        public void onSetSuccess() {
            executor.execute(() -> {
                if (peerConnection == null || isError) {
                    return;
                }
                if (isInitiator) {
                    if (peerConnection.getRemoteDescription() == null) {
                        Log.d(TAG, "Local SDP set succesfully");
                        events.onLocalDescription(localDescription);
                    } else {
                        Log.d(TAG, "Remote SDP set succesfully");
                    }
                } else {
                    if (peerConnection.getLocalDescription() != null) {
                        Log.d(TAG, "Local SDP set succesfully");
                        events.onLocalDescription(localDescription);
                    } else {
                        Log.d(TAG, "Remote SDP set succesfully");
                    }
                }
            });
        }

        @Override
        public void onCreateFailure(final String error) {
            reportError("createSDP error: " + error);
        }

        @Override
        public void onSetFailure(final String error) {
            reportError("setSDP error: " + error);
        }
    }
}
