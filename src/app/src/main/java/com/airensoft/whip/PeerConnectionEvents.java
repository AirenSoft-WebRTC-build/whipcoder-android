package com.airensoft.whip;

import org.webrtc.IceCandidate;
import org.webrtc.RTCStatsReport;
import org.webrtc.SessionDescription;

/**
 * Peer connection events.
 */
public interface PeerConnectionEvents {
    /**
     * Callback fired once local SDP is created and set.
     */
    void onLocalDescription(final SessionDescription sdp);

    /**
     * Callback fired once local Ice candidate is generated.
     */
    void onIceCandidate(final IceCandidate candidate);

    /**
     * Callback fired once local ICE candidates are removed.
     */
    void onIceCandidatesRemoved(final IceCandidate[] candidates);

    /**
     * Callback fired once connection is established (IceConnectionState is
     * CONNECTED).
     */
    void onIceConnected();

    /**
     * Callback fired once connection is disconnected (IceConnectionState is
     * DISCONNECTED).
     */
    void onIceDisconnected();

    /**
     * Callback fired once DTLS connection is established (PeerConnectionState
     * is CONNECTED).
     */
    void onConnected();

    /**
     * Callback fired once DTLS connection is disconnected (PeerConnectionState
     * is DISCONNECTED).
     */
    void onDisconnected();

    /**
     * Callback fired once peer connection is closed.
     */
    void onPeerConnectionClosed();

    /**
     * Callback fired once peer connection statistics is ready.
     */
    void onPeerConnectionStatsReady(final RTCStatsReport report);

    /**
     * Callback fired once peer connection error happened.
     */
    void onPeerConnectionError(final String description);
}
