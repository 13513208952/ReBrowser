package io.github.reoutlook;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.Rating;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.SystemClock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.json.JSONObject;

/** Self-targeting test APK only. Does not restart the Release app or read its DOM/Cookie/URLs. */
public final class ReBrowserMusicSystemInstrumentation extends Instrumentation {
    private Bundle arguments;
    private UiAutomation automation;
    private static final String PACKAGE = "io.github.reoutlook";
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); this.arguments = arguments; start(); }
    private MediaController current() {
        List<MediaController> active = getContext().getSystemService(MediaSessionManager.class).getActiveSessions(null);
        MediaController found = null;
        for (MediaController controller : active) {
            if (!PACKAGE.equals(controller.getPackageName()) || !"ReBrowserMusic".equals(controller.getTag())) continue;
            if (found != null) throw new IllegalStateException("ambiguous-music-session");
            found = controller;
        }
        return found;
    }
    private static String hash(String value) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }
    private JSONObject snapshot(MediaController controller) throws Exception {
        JSONObject out = new JSONObject().put("present", controller != null);
        if (controller == null) return out;
        PlaybackState state = controller.getPlaybackState(); MediaMetadata meta = controller.getMetadata();
        if (state != null) out.put("state", state.getState()).put("position", state.getPosition())
                .put("actions", state.getActions()).put("speed", state.getPlaybackSpeed());
        if (meta != null) {
            // Account-associated title/artist/id and artwork/URI are never returned.
            out.put("trackHash", hash(String.valueOf(meta.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)) + "\n"
                    + meta.getString(MediaMetadata.METADATA_KEY_TITLE) + "\n" + meta.getString(MediaMetadata.METADATA_KEY_ARTIST)));
            out.put("duration", meta.getLong(MediaMetadata.METADATA_KEY_DURATION));
            Rating rating = meta.getRating(MediaMetadata.METADATA_KEY_USER_RATING);
            out.put("liked", rating != null && rating.isRated() && rating.hasHeart());
            Bitmap art = meta.getBitmap(MediaMetadata.METADATA_KEY_ART);
            out.put("artWidth", art == null ? 0 : art.getWidth()).put("artHeight", art == null ? 0 : art.getHeight());
        }
        return out;
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            automation.adoptShellPermissionIdentity("android.permission.MEDIA_CONTENT_CONTROL");
            String command = arguments.getString("command", "snapshot");
            MediaController controller = current();
            if (!"snapshot".equals(command) && controller == null) throw new IllegalStateException("music-session-missing");
            if ("stale-session".equals(command)) {
                stale(controller, result);
            } else {
                JSONObject before = snapshot(controller);
                if (!"snapshot".equals(command)) {
                    MediaController.TransportControls controls = controller.getTransportControls();
                    switch (command) {
                        case "play": controls.play(); break;
                        case "pause": controls.pause(); break;
                        case "next": controls.skipToNext(); break;
                        case "previous": controls.skipToPrevious(); break;
                        case "seek": {
                            long position = Long.parseLong(arguments.getString("position", "-1"));
                            if (position < 0 || position > 86_400_000) throw new IllegalArgumentException("invalid-position");
                            controls.seekTo(position); break;
                        }
                        case "like": controls.setRating(Rating.newHeartRating(true)); break;
                        case "unlike": controls.setRating(Rating.newHeartRating(false)); break;
                        default: throw new IllegalArgumentException("unsupported-fixed-command");
                    }
                    SystemClock.sleep(1000);
                }
                result.putString("before", before.toString()); result.putString("after", snapshot(current()).toString());
                result.putString("status", "snapshot".equals(command) ? "observed" : "submitted-not-yet-accepted");
            }
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("status", "failed"); result.putString("errorClass", failure.getClass().getSimpleName());
            finish(Activity.RESULT_CANCELED, result);
        } finally { if (automation != null) automation.dropShellPermissionIdentity(); }
    }
    private void stale(MediaController old, Bundle result) throws Exception {
        MediaSession.Token token = old.getSessionToken();
        Bundle armed = new Bundle(); armed.putString("status", "old-session-held"); sendStatus(1, armed);
        long until = SystemClock.elapsedRealtime() + 90_000;
        MediaController replacement = null;
        while (SystemClock.elapsedRealtime() < until) {
            MediaController next = current();
            if (next != null && !token.equals(next.getSessionToken())) { replacement = next; break; }
            SystemClock.sleep(500);
        }
        if (replacement == null) throw new IllegalStateException("replacement-not-observed");
        JSONObject before = null;
        while (SystemClock.elapsedRealtime() < until) {
            JSONObject first = snapshot(replacement); SystemClock.sleep(2000); JSONObject second = snapshot(replacement);
            if (second.optInt("state") == PlaybackState.STATE_PAUSED && first.optInt("state") == PlaybackState.STATE_PAUSED
                    && first.optLong("position") == second.optLong("position")
                    && first.optString("trackHash").equals(second.optString("trackHash"))) { before = second; break; }
        }
        if (before == null) throw new IllegalStateException("replacement-not-stably-paused");
        old.getTransportControls().play(); old.getTransportControls().skipToNext(); old.getTransportControls().seekTo(15000);
        SystemClock.sleep(2500); MediaController next = current(); JSONObject after = snapshot(next);
        boolean same = next != null && replacement.getSessionToken().equals(next.getSessionToken())
                && after.optInt("state") == PlaybackState.STATE_PAUSED
                && before.optLong("position") == after.optLong("position")
                && before.optString("trackHash").equals(after.optString("trackHash"));
        result.putString("before", before.toString()); result.putString("after", after.toString());
        result.putString("status", same ? "passed-stale-controller-only" : "failed-stale-controller");
        // This is not a retained notification PendingIntent / nonce integration test.
    }
}
