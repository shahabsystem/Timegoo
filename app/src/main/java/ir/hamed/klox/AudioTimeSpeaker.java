package ir.hamed.klox;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Audio clock built around the three original feminine clip families:
 *
 *   bN.mp3   = exact hour (top of the hour)
 *   bN_.mp3  = hour phrase used when minutes follow
 *   bNm.mp3  = minute, rounded to five-minute steps
 *
 * The announcement time is rounded to the nearest five minutes. If rounding
 * crosses :55, the hour rolls forward and the exact-hour clip b(N+1) is used.
 * Playback is strictly serialized: Ding -> hour -> minute.
 */
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

    public void speakCurrentTime() {
        Calendar now = Calendar.getInstance(Locale.US);
        speak(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
    }

    /** Used by scheduled announcements. Ding-only affects these automatic announcements. */
    public void speakScheduledTime() {
        Calendar now = Calendar.getInstance(Locale.US);
        android.content.SharedPreferences p = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        if (p.getBoolean("dingOnlyMode", false)) {
            playSelectedDing();
            return;
        }
        speak(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
    }

    public void speak(int hour, int minute) {
        stop();
        List<Integer> sequence = buildSequence(hour, minute);
        if (sequence.isEmpty()) return;
        requestFocus();
        boostVolume();
        playAt(sequence, 0);
    }

    private List<Integer> buildSequence(int hour, int minute) {
        List<Integer> ids = new ArrayList<>();
        android.content.SharedPreferences p = context.getSharedPreferences("settings", Context.MODE_PRIVATE);

        addSelectedDing(ids, p);

        // Round the COMPLETE time to the nearest five minutes.
        // 01/02 -> :00, 03/04 -> :05, ... 58/59 -> next hour :00.
        int h = Math.floorMod(hour, 24);
        int m = Math.max(0, Math.min(59, minute));
        int rounded = ((m + 2) / 5) * 5;
        if (rounded >= 60) {
            rounded = 0;
            h = (h + 1) % 24;
        }

        boolean numericOnly = p.getBoolean("numericOnly", false);

        if (rounded == 0) {
            // At a rounded exact hour, the original bN clip is the correct
            // single hour announcement.
            int exactHour = raw("b" + h);
            if (exactHour == 0) exactHour = raw("b" + h + "_");
            addIfExists(ids, exactHour);
            return ids;
        }

        // When minutes follow, bN_ is the dedicated hour-with-minute clip.
        // Never use bN here: it is the exact-hour clip and causes duplicated
        // constructions such as "ساعت ۹ ساعت ۲۳".
        int hourWithMinute = raw("b" + h + "_");
        if (hourWithMinute == 0) hourWithMinute = raw("b" + h);
        addIfExists(ids, hourWithMinute);

        // The source audio set provides feminine minute clips only in five-
        // minute steps. Use the rounded five-minute clip directly.
        int minuteClip = raw("b" + rounded + "m");
        if (minuteClip == 0) {
            // Defensive fallback for a damaged/incomplete resource set.
            minuteClip = raw("b" + rounded + "_");
        }
        addIfExists(ids, minuteClip);

        // numericOnly is retained for backwards compatibility with the UI.
        // With the original supplied audio set there is no separate bare-hour
        // recording; therefore we keep the same audio-safe clip mapping rather
        // than silently dropping the hour.
        return ids;
    }

    public void testSelectedDing() {
        playSelectedDing();
    }

    private void playSelectedDing() {
        stop();
        android.content.SharedPreferences p = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        if ("custom".equals(p.getString("dingMode", "builtin"))) {
            String uri = p.getString("customDingUri", "");
            if (!uri.isEmpty()) {
                requestFocus();
                boostVolume();
                playUri(uri);
                return;
            }
        }
        List<Integer> one = new ArrayList<>();
        addSelectedDing(one, p);
        if (one.isEmpty()) return;
        requestFocus();
        boostVolume();
        playAt(one, 0);
    }

    private void addSelectedDing(List<Integer> ids, android.content.SharedPreferences p) {
        if ("custom".equals(p.getString("dingMode", "builtin"))) {
            String uri = p.getString("customDingUri", "");
            if (!uri.isEmpty()) { ids.add(-1); return; }
        }
        int dingNumber = p.getInt("dingNumber", 1);
        if (dingNumber < 1 || dingNumber > 5) dingNumber = 1;
        addIfExists(ids, raw("ding" + dingNumber));
    }

    private void playUri(String uri) {
        try {
            final MediaPlayer next = MediaPlayer.create(context, android.net.Uri.parse(uri));
            player = next;
            if (next == null) { restoreVolume(); releaseFocus(); return; }
            if (Build.VERSION.SDK_INT >= 21) {
                next.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            }
            next.setOnCompletionListener(mp -> { try { mp.release(); } catch (Exception ignored) {} player=null; restoreVolume(); releaseFocus(); });
            next.setOnErrorListener((mp, what, extra) -> { try { mp.release(); } catch (Exception ignored) {} player=null; restoreVolume(); releaseFocus(); return true; });
            next.start();
        } catch (Exception ignored) { restoreVolume(); releaseFocus(); }
    }

    private void addIfExists(List<Integer> ids, int id) {
        if (id != 0) ids.add(id);
    }

    private int raw(String name) {
        return context.getResources().getIdentifier(name, "raw", context.getPackageName());
    }

    private void playAt(final List<Integer> sequence, final int index) {
        if (index >= sequence.size()) {
            restoreVolume();
            releaseFocus();
            return;
        }
        releasePlayerOnly();
        try {
            final MediaPlayer next;
            if (sequence.get(index) == -1) {
                String uri = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("customDingUri", "");
                next = uri.isEmpty() ? null : MediaPlayer.create(context, android.net.Uri.parse(uri));
            } else {
                next = MediaPlayer.create(context, sequence.get(index));
            }
            player = next;
            if (next == null) {
                playAt(sequence, index + 1);
                return;
            }
            if (Build.VERSION.SDK_INT >= 21) {
                next.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
            }
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
            if (Build.VERSION.SDK_INT >= 23) {
                float speed = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .getInt("announcementSpeed", 100) / 100f;
                try {
                    next.setPlaybackParams(new android.media.PlaybackParams()
                            .setSpeed(speed)
                            .setPitch(1.0f));
                } catch (Exception ignored) { }
            }
            next.start();
        } catch (Exception ignored) {
            playAt(sequence, index + 1);
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
                    .setOnAudioFocusChangeListener(focusListener)
                    .build();
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
