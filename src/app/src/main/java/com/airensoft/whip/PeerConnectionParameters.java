package com.airensoft.whip;

/**
 * Peer connection parameters.
 */
public class PeerConnectionParameters {
    public final boolean videoCallEnabled;
    public final boolean tracing;
    public final int videoWidth;
    public final int videoHeight;
    public final int videoFps;
    public final int videoMaxBitrate;
    public final String videoCodec;
    public final int maxBFrames;
    public final boolean videoCodecHwAcceleration;
    public final boolean videoFlexfecEnabled;
    public final boolean videoSimulcastEnabled;
    public final int audioStartBitrate;
    public final String audioCodec;
    public final boolean noAudioProcessing;
    public final boolean disableBuiltInAEC;
    public final boolean disableBuiltInAGC;
    public final boolean disableBuiltInNS;
    public final boolean disableWebRtcAGCAndHPF;
    public final boolean enableRtcEventLog;
    public final boolean enableCpuOveruseDetection;

    public PeerConnectionParameters(boolean videoCallEnabled, boolean tracing,
                                    int videoWidth, int videoHeight, int videoFps, int videoMaxBitrate, String videoCodec, int maxBFrames,
                                    boolean videoCodecHwAcceleration, boolean videoFlexfecEnabled, boolean videoSimulcastEnabled,
                                    int audioStartBitrate, String audioCodec, boolean noAudioProcessing,
                                    boolean disableBuiltInAEC, boolean disableBuiltInAGC,
                                    boolean disableBuiltInNS, boolean disableWebRtcAGCAndHPF, boolean enableRtcEventLog, boolean enableCpuOveruseDetection) {
        this.videoCallEnabled = videoCallEnabled;
        this.tracing = tracing;
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.videoFps = videoFps;
        this.videoMaxBitrate = videoMaxBitrate;
        this.videoCodec = videoCodec;
        this.videoSimulcastEnabled = videoSimulcastEnabled;
        this.maxBFrames = maxBFrames;
        this.videoFlexfecEnabled = videoFlexfecEnabled;
        this.videoCodecHwAcceleration = videoCodecHwAcceleration;
        this.audioStartBitrate = audioStartBitrate;
        this.audioCodec = audioCodec;
        this.noAudioProcessing = noAudioProcessing;
        this.disableBuiltInAEC = disableBuiltInAEC;
        this.disableBuiltInAGC = disableBuiltInAGC;
        this.disableBuiltInNS = disableBuiltInNS;
        this.disableWebRtcAGCAndHPF = disableWebRtcAGCAndHPF;
        this.enableRtcEventLog = enableRtcEventLog;
        this.enableCpuOveruseDetection = enableCpuOveruseDetection;
    }
}
