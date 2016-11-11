package com.jkokosa.oscixer;

import java.util.ArrayList;

/**
 * Created by jkokosa on 11/8/16.
 */

public class FeedbackTracker {
    /*
    Audio Tracks
    Midi Tracks
    Audio + Midi Tracks
    Audio Busses
    Midi Busses
    VCA Masters
    Master 2-Bus
    Monitor Section
     */

    private static int STATUS_UNKNOWN = 0;
    private static int STATUS_DISPLAYED = 1;
    private static int STATUS_CACHED = 2;

    protected ArrayList<FeedbackChannel> audio_tracks;

    public FeedbackTracker() {
        audio_tracks = new ArrayList<>(256);
    }

    public float getTrackGain(int stripID) {
        float gain = -999.99f;

        for (FeedbackChannel track : audio_tracks) {
            if (track.getId() == stripID) {
                gain = track.getGain();
            }
        }
        return gain;
    }

    public void setTrackGain(int trackID, float gain) {
        boolean found = false;
        for (FeedbackChannel track : audio_tracks) {
            if (track.getId() == trackID) {
                track.setGain(gain);
                found = true;
            }
        }
        if (!found) {
            FeedbackChannel trk = new FeedbackChannel(trackID);
            trk.setGain(gain);
            audio_tracks.add(trk);
        }
    }

    /*/strip/name
V/listener: /strip/mute
V/listener: /strip/solo
V/listener: /strip/monitor_input
V/listener: /strip/monitor_disk
V/listener: /strip/recenable
V/listener: /strip/record_safe
V/listener: /strip/select
V/listener: /strip/trimdB
V/listener: /strip/pan_stereo_position*/
    private class FeedbackChannel {
        private int id;
        private int status;
        private String name;
        private int mute;
        private int solo;
        private int solo_iso;
        private int solo_safe;
        private int ploarity;
        private int monitor_input;
        private int monitor_disk;
        private int recenable;
        private int record_safe;
        private int select;
        private int expand;
        private float trimdB;
        private float fader;
        private float pan_stero_position;
        private float pan_stero_width;
        private float gain;
        private float send_gain;
        private float send_fader;
        private float send_enable;

        public FeedbackChannel() {

        }

        public FeedbackChannel(int trackID) {
            setId(trackID);
            setStatus(STATUS_UNKNOWN);

        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        protected int getId() {
            return id;
        }

        protected void setId(int id) {
            this.id = id;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public float getGain() {
            return gain;
        }

        public void setGain(float gain) {
            this.gain = gain;
        }

    }

}
