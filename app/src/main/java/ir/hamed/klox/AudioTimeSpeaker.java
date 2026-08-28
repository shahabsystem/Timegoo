package ir.hamed.klox;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Serial audio playback for Timegoo clock announcements. */
public final class AudioTimeSpeaker {
    private final Context context;
    private final AudioManager audioManager;
    private MediaPlayer player;
    private AudioManager.OnAudioFocusChangeListener focusListener;
    private AudioFocusRequest focusRequest;
    private int originalVolume = -1;

    public AudioTimeSpeaker(Context c) {
        context = c.getApplicationContext();
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    /** Manual announcement: Ding (selected) + current time. */
    public void speakCurrentTime() {
        Calendar now = Calendar.getInstance(Locale.US);
        speak(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
    }

    /** Scheduled announcement. Ding-only mode applies ONLY to scheduled announcements. */
    public void speakScheduledTime() {
        android.content.SharedPreferences p = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        if (p.getBoolean("dingOnlyMode", false)) {
            playSelectedDing();
            return;
        }
        Calendar now = Calendar.getInstance(Locale.US);
        speak(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
    }

    public void speak(int hour, int minute) {
        stop();
        List<Integer> timeSequence = buildTimeSequence(hour, minute);
        if (timeSequence.isEmpty() && getCustomDingUri().isEmpty()) return;
        requestFocus();
        boostVolume();

        String custom = getCustomDingUri();
        if (!custom.isEmpty()) {
            playUriThenResources(custom, timeSequence);
        } else {
            List<Integer> sequence = new ArrayList<>();
            int dingNumber = getSelectedDingNumber();
            addIfExists(sequence, raw("ding" + dingNumber));
            sequence.addAll(timeSequence);
            playAt(sequence, 0);
        }
    }

    private List<Integer> buildTimeSequence(int hour, int minute) {
        List<Integer> ids = new ArrayList<>();
        int h = Math.floorMod(hour, 24);
        int m = Math.max(0, Math.min(59, minute));

        int rounded = ((m + 2) / 5) * 5;
        if (rounded >= 60) {
            rounded = 0;
            h = (h + 1) % 24;
        }

        if (rounded == 0) {
            int exactHour = raw("b" + h);
            if (exactHour == 0) exactHour = raw("b" + h + "_");
            addIfExists(ids, exactHour);
            return ids;
        }

        int hourWithMinute = raw("b" + h + "_");
        if (hourWithMinute == 0) hourWithMinute = raw("b" + h);
        addIfExists(ids, hourWithMinute);

        int minuteClip = raw("b" + rounded + "m");
        if (minuteClip == 0) minuteClip = raw("b" + rounded + "_");
        addIfExists(ids, minuteClip);
        return ids;
    }

    public void testSelectedDing() {
        playSelectedDing();
    }

    private void playSelectedDing() {
        stop();
        requestFocus();
        boostVolume();
        String custom = getCustomDingUri();
        if (!custom.isEmpty()) {
            playUriThenResources(custom, new ArrayList<>());
            return;
        }
        List<Integer> one = new ArrayList<>();
        addIfExists(one, raw("ding" + getSelectedDingNumber()));
        if (one.isEmpty()) { stop(); return; }
        playAt(one, 0);
    }

    private int getSelectedDingNumber() {
        int n = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getInt("dingNumber", 1);
        return (n < 1 || n > 5) ? 1 : n;
    }

    private String getCustomDingUri() {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("customDingUri", "");
    }

    private void addIfExists(List<Integer> ids, int id) {
        if (id != 0) ids.add(id);
    }

    private int raw(String name) {
        return context.getResources().getIdentifier(name, "raw", context.getPackageName());
    }

    private void playUriThenResources(String uriString, List<Integer> nextResources) {
        releasePlayerOnly();
        try {
            final MediaPlayer next = MediaPlayer.create(context, Uri.parse(uriString));
            player = next;
            if (next == null) {
                if (nextResources.isEmpty()) { stop(); return; }
                playAt(nextResources, 0);
                return;
            }
            configurePlayer(next);
            next.setOnCompletionListener(mp -> {
                try { mp.release(); } catch (Exception ignored) { }
                if (player == mp) player = null;
                if (nextResources.isEmpty()) stop(); else playAt(nextResources, 0);
            });
            next.setOnErrorListener((mp, what, extra) -> {
                try { mp.release(); } catch (Exception ignored) { }
                if (player == mp) player = null;
                if (nextResources.isEmpty()) stop(); else playAt(nextResources, 0);
                return true;
            });
            next.start();
        } catch (Exception ignored) {
            if (nextResources.isEmpty()) stop(); else playAt(nextResources, 0);
        }
    }

    private void playAt(final List<Integer> sequence, final int index) {
        if (index >= sequence.size()) {
            restoreVolume();
            releaseFocus();
            return;
        }
        releasePlayerOnly();
        try {
            final MediaPlayer next = MediaPlayer.create(context, sequence.get(index));
            player = next;
            if (next == null) { playAt(sequence, index + 1); return; }
            configurePlayer(next);
            next.setOnCompletionListener(mp -> {
                try { mp.release(); } catch (Exception ignored) { }
                if (player == mp) player = null;
                playAt(sequence, index + 1);
            });
            next.setOnErrorListener((mp, what, extra) -> {
                try { mp.release(); } catch (Exception ignored) { }
                if (player == mp) player = null;
                playAt(sequence, index + 1);
                return true;
            });
            next.start();
        } catch (Exception ignored) {
            playAt(sequence, index + 1);
        }
    }

    private void configurePlayer(MediaPlayer next) {
        if (Build.VERSION.SDK_INT >= 21) {
            next.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
        }
        if (Build.VERSION.SDK_INT >= 23) {
            float speed = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .getInt("announcementSpeed", 100) / 100f;
            try {
                next.setPlaybackParams(new android.media.PlaybackParams()
                        .setSpeed(speed).setPitch(1.0f));
            } catch (Exception ignored) { }
        }
    }

    private void requestFocus() {
        if (audioManager == null) return;
        focusListener = focus -> { };
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setOnAudioFocusChangeListener(focusListener).build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        }
    }

    private void releaseFocus() {
        if (audioManager == null || focusListener == null) return;
        if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
            focusRequest = null;
        } else if (Build.VERSION.SDK_INT < 26) {
            audioManager.abandonAudioFocus(focusListener);
        }
        focusListener = null;
    }

    private void boostVolume() {
        if (audioManager == null) return;
        try {
            originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int pct = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .getInt("announcementVolume", 80);
            int target = Math.round(max * (pct / 100f));
            if (target < 1) target = 1;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, Math.min(max, target), 0);
        } catch (Exception ignored) { }
    }

    private void restoreVolume() {
        if (audioManager != null && originalVolume >= 0) {
            try { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0); }
            catch (Exception ignored) { }
            originalVolume = -1;
        }
    }

    private void releasePlayerOnly() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    public void stop() {
        releasePlayerOnly();
        restoreVolume();
        releaseFocus();
    }
}
