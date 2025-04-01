package com.airensoft.whip;

class PeerConnectionConstant {
    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "ARDAMSa0";
    public static final String VIDEO_TRACK_TYPE = "video";

    public static final String VIDEO_CODEC_VP8 = "VP8";
    public static final String VIDEO_CODEC_VP9 = "VP9";
    public static final String VIDEO_CODEC_H264 = "H264";
    public static final String VIDEO_CODEC_H265 = "H265";
    public static final String VIDEO_CODEC_H264_BASELINE = "H264 Baseline";
    public static final String VIDEO_CODEC_H264_HIGH = "H264 High";
    public static final String VIDEO_CODEC_AV1 = "AV1";
    public static final String AUDIO_CODEC_OPUS = "opus";

    public static final String VIDEO_CODEC_PARAM_START_BITRATE = "x-google-start-bitrate";
    public static final String VIDEO_FLEXFEC_FIELDTRIAL = "WebRTC-FlexFEC-03-Advertised/Enabled/WebRTC-FlexFEC-03/Enabled/";
    public static final String DISABLE_WEBRTC_AGC_FIELDTRIAL = "WebRTC-Audio-MinimizeResamplingOnMobile/Enabled/";
    public static final String LEGACY_SIMULCAST_LAYER_LIMIT_FIELDTRIAL = "WebRTC-LegacySimulcastLayerLimit/Disabled/";
    public static final String SPSPPSIDR_IS_H264_KEYFRAME_FIELDTRIAL = "WebRTC-SpsPpsIdrIsH264Keyframe/Enabled/";
    public static final String VIDEO_LAYERS_ALLOCATION_ADVERTISED_FIELDTRIAL = "WebRTC-VideoLayersAllocationAdvertised/Enabled/";
    public static final String AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate";
    public static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    public static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
    public static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";
    public static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";
    public static final int DEFAULT_VIDEO_WIDTH = 1920;
    public static final int DEFAULT_VIDEO_HEIGHT = 1080;
    public static final int DEFAULT_VIDEO_FPS = 15;
    public static final int BPS_IN_KBPS = 1000;
}
