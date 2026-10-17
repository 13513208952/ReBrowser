package io.github.reoutlook;

import android.content.Context;
import java.net.URI;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.function.Consumer;

/** Optional, current-origin quota estimates from an ALREADY existing official page. No bridge or login dependency. */
final class ReBrowserStorageEstimate {
    static final long TIMEOUT_MS = 3000;
    private static final long MAX_BYTES = 9_007_199_254_740_991L;
    private static Store store;
    private ReBrowserStorageEstimate() {}
    // Only these two numeric fields are returned. No cookies, storage keys, DOM, URLs or account contents.
    static final String START = """
        (()=>{const token=__RB_TOKEN__,key='__rbStorageEstimateV1';let own=null;
          try {
            if(location.origin!==__RB_ORIGIN__)return JSON.stringify({token,status:'stale'});
            const old=window[key];
            if(old&&old.busy)return JSON.stringify({token,status:'busy'});
            const r={token,status:'pending',busy:true,usage:null,caches:null};own=r;window[key]=r;
            if(window[key]!==r)return JSON.stringify({token,status:'unavailable'});
            const number=v=>typeof v==='number'&&Number.isSafeInteger(v)&&v>=0?v:null;
            if(!window.isSecureContext||!navigator.storage||typeof navigator.storage.estimate!=='function'){
              r.busy=false;r.status='unsupported';return JSON.stringify(r);
            }
            Promise.resolve(navigator.storage.estimate()).then(v=>{try{
              r.usage=number(v&&v.usage);r.caches=number(v&&v.usageDetails&&v.usageDetails.caches);
              if(r.caches===null&&r.usage===0&&v.usageDetails&&typeof v.usageDetails==='object')r.caches=0;
              r.status=r.usage!==null||r.caches!==null?'ok':'unavailable';
            }catch(_){r.status='unavailable';r.usage=null;r.caches=null;}finally{r.busy=false;}
            },()=>{r.status='unavailable';r.busy=false;});
            return JSON.stringify(r);
          }catch(_){if(own){own.busy=false;own.status='unavailable';}return JSON.stringify({token,status:'unavailable'});}
        })()
        """;
    static final String READ = """
        (()=>{const token=__RB_TOKEN__,key='__rbStorageEstimateV1';try{
          if(location.origin!==__RB_ORIGIN__)return JSON.stringify({token,status:'stale'});
          const r=window[key];if(!r||r.token!==token)return JSON.stringify({token,status:'stale'});
          const value=JSON.stringify({token,status:r.status,usage:r.usage,caches:r.caches});
          if(!r.busy)delete window[key];return value;
        }catch(_){return JSON.stringify({token,status:'unavailable'});}})()
        """;
    interface Driver {
        String url();
        void evaluate(String script, ValueCallback<String> callback);
    }
    private static final class WebDriver implements Driver {
        final WeakReference<WebView> view;
        WebDriver(WebView view) { this.view = new WeakReference<>(view); }
        @Override public String url() { WebView web = view.get(); return web == null ? null : web.getUrl(); }
        @Override public void evaluate(String script, ValueCallback<String> callback) {
            WebView web = view.get();
            if (web == null) throw new IllegalStateException("page-unavailable");
            web.evaluateJavascript(script, callback);
        }
    }
    private static final class Store {
        final Context app;
        final Endpoint[] endpoints = new Endpoint[2];
        Store(Context context) { app = context.getApplicationContext(); }
    }
    private static final class Endpoint {
        final Driver driver;
        long generation;
        volatile Sample sample = new Sample("unavailable", "", null, null);
        Request active;
        Endpoint(Driver driver) { this.driver = driver; }
    }
    private static final class Sample {
        final String status, origin;
        final Long usage, caches;
        final long at = SystemClock.elapsedRealtime();
        Sample(String status, String origin, Long usage, Long caches) {
            this.status = status; this.origin = origin; this.usage = usage; this.caches = caches;
        }
        JSONObject json() throws Exception {
            return new JSONObject().put("status", status).put("origin", origin)
                    .put("siteStorageBytes", usage == null ? JSONObject.NULL : usage)
                    .put("cacheStorageBytes", caches == null ? JSONObject.NULL : caches)
                    .put("approximate", true).put("scope", "current-origin-only")
                    .put("includesHttpCache", false).put("ageMs", Math.max(0, SystemClock.elapsedRealtime() - at));
        }
    }
    private static synchronized Store state(Context context) {
        Context app = context.getApplicationContext();
        if (store == null || store.app != app) store = new Store(app);
        return store;
    }
    private static int index(String id) {
        if (ReBrowserApplications.OUTLOOK.equals(id)) return 0;
        if (ReBrowserApplications.MUSIC.equals(id)) return 1;
        throw new IllegalArgumentException("Unsupported fixed application");
    }
    private static void ui() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("UI thread required");
    }
    static void register(Context context, String id, WebView web) { register(context, id, new WebDriver(web)); }
    static void register(Context context, String id, Driver driver) {
        ui(); Store s = state(context); int i = index(id);
        if (s.endpoints[i] != null && s.endpoints[i].active != null) s.endpoints[i].active.abandon();
        s.endpoints[i] = new Endpoint(driver);
    }
    static void navigating(Context context, String id, WebView web) {
        ui(); Endpoint e = state(context).endpoints[index(id)];
        if (matches(e, web)) invalidate(context, id);
    }
    static void unregister(Context context, String id, WebView web) {
        ui(); Store s = state(context); int i = index(id); Endpoint e = s.endpoints[i];
        if (!matches(e, web)) return;
        if (e.active != null) e.active.abandon(); s.endpoints[i] = null;
    }
    private static boolean matches(Endpoint e, WebView web) {
        return web != null && e != null && e.driver instanceof WebDriver && ((WebDriver) e.driver).view.get() == web;
    }
    static void invalidate(Context context, String id) {
        ui(); Endpoint e = state(context).endpoints[index(id)];
        if (e == null) return;
        e.generation++; e.sample = new Sample("unavailable", "", null, null);
        if (e.active != null) e.active.abandon();
    }
    static JSONObject snapshot(Context context, String id) throws Exception {
        Endpoint e = state(context).endpoints[index(id)];
        return (e == null ? new Sample("unavailable", "", null, null) : e.sample).json();
    }
    static String origin(String id, String value) {
        if (value == null || value.length() > 8192) return null;
        try {
            URI u = new URI(value); String host = u.getHost();
            if (!"https".equals(u.getScheme()) || u.getRawUserInfo() != null || (u.getPort() != -1 && u.getPort() != 443) || host == null) return null;
            host = host.toLowerCase(java.util.Locale.ROOT);
            if (ReBrowserApplications.MUSIC.equals(id)) {
                if (!ReBrowserMusicPolicy.playerDocument(value) && !ReBrowserMusicPolicy.loginDocument(value)) return null;
            } else if (ReBrowserApplications.OUTLOOK.equals(id)) {
                if (!java.util.Set.of("outlook.office.com", "outlook.office365.com", "outlook.live.com", "login.microsoftonline.com", "login.live.com").contains(host)) return null;
            } else return null;
            return "https://" + host;
        } catch (Exception invalid) { return null; }
    }
    static Long bytes(Object value) {
        if (!(value instanceof Number)) return null;
        double d = ((Number) value).doubleValue();
        return Double.isFinite(d) && d >= 0 && d <= MAX_BYTES && d == Math.rint(d) ? (long) d : null;
    }
    static JSONObject decode(String value) throws Exception {
        if (value == null || value.length() > 2048) throw new IllegalArgumentException("invalid-estimate");
        Object decoded = new JSONTokener(value).nextValue();
        return decoded instanceof String ? new JSONObject((String) decoded) : (JSONObject) decoded;
    }
    static void request(Context context, String id, Consumer<JSONObject> reply) {
        ui(); Store s = state(context); int i = index(id); Endpoint e = s.endpoints[i];
        try {
            if (e == null || ReBrowserSubapplicationGate.blocked(context, id)) { reply.accept(new Sample("unavailable", "", null, null).json()); return; }
            if (e.active != null) { reply.accept(new Sample("busy", "", null, null).json()); return; }
            String allowed = origin(id, e.driver.url());
            if (allowed == null) { e.sample = new Sample("unavailable", "", null, null); reply.accept(e.sample.json()); return; }
            Request r = new Request(s, i, id, e, allowed, reply); e.active = r; r.start();
        } catch (Exception unavailable) {
            if (e != null) e.sample = new Sample("unavailable", "", null, null);
            try { reply.accept(new Sample("unavailable", "", null, null).json()); } catch (Exception ignored) { /* Optional only. */ }
        }
    }
    private static final class Request {
        final Store owner;
        final int index;
        final String id, origin, token = UUID.randomUUID().toString();
        final Endpoint endpoint;
        final long generation;
        final Consumer<JSONObject> reply;
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable timeout = this::abandon;
        boolean evaluating, ended, notified;
        Request(Store owner, int index, String id, Endpoint e, String origin, Consumer<JSONObject> reply) {
            this.owner = owner; this.index = index; this.id = id; endpoint = e; this.origin = origin; this.reply = reply; generation = e.generation;
        }
        boolean current() {
            try { return owner.endpoints[index] == endpoint && endpoint.generation == generation && origin.equals(ReBrowserStorageEstimate.origin(id, endpoint.driver.url())); }
            catch (RuntimeException missingPage) { return false; }
        }
        void start() { handler.postDelayed(timeout, TIMEOUT_MS); evaluate(START); }
        void evaluate(String script) {
            if (ended || !current()) { abandon(); return; }
            evaluating = true;
            try {
                endpoint.driver.evaluate(script.replace("__RB_TOKEN__", JSONObject.quote(token)).replace("__RB_ORIGIN__", JSONObject.quote(origin)), value -> {
                    evaluating = false;
                    if (ended) { release(); return; }
                    if (!current()) { abandon(); return; }
                    try {
                        JSONObject r = decode(value);
                        if (!token.equals(r.opt("token"))) { finish(new Sample("unavailable", origin, null, null)); return; }
                        String status = r.optString("status");
                        if ("pending".equals(status)) { handler.postDelayed(() -> evaluate(READ), 200); return; }
                        Long usage = bytes(r.opt("usage")), caches = bytes(r.opt("caches"));
                        boolean available = "ok".equals(status) && (usage != null || caches != null);
                        finish(new Sample(available ? "ok" : "unsupported".equals(status) ? "unsupported" : "unavailable", origin,
                                available ? usage : null, available ? caches : null));
                    } catch (Exception invalid) { finish(new Sample("unavailable", origin, null, null)); }
                });
            } catch (RuntimeException unavailable) { evaluating = false; abandon(); }
        }
        void finish(Sample value) {
            if (ended) return;
            ended = true; handler.removeCallbacksAndMessages(null);
            if (current()) endpoint.sample = value;
            release(); notifyOnce(value);
        }
        void abandon() {
            if (!ended) {
                ended = true; handler.removeCallbacksAndMessages(null);
                if (owner.endpoints[index] == endpoint) endpoint.sample = new Sample("unavailable", "", null, null);
                // An outstanding native evaluation keeps its slot until its real callback arrives.
                if (!evaluating) release();
                notifyOnce(new Sample("unavailable", "", null, null));
            }
        }
        void release() { if (endpoint.active == this) endpoint.active = null; }
        void notifyOnce(Sample value) {
            if (notified) return; notified = true;
            try { reply.accept(value.json()); } catch (Exception ignored) { /* Estimates never govern other functionality. */ }
        }
    }
}
