package io.github.rebrowser.bridge;

import android.app.Application;
import android.os.Looper;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.engine.GeckoRuntimeOwner;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.WebExtension;

/** One source/runtime-bound fixed APK extension installation owner. Not a caller URI/extension
 * loader. Production bridge assets are NOT packaged/admitted yet: absent manifest fails BEFORE
 * creating Gecko. No automatic install at startup, uninstall/repair/retry or page-data pumping.
 * Actual ensureBuiltIn/SDK callbacks require separate Android/Gecko qualification. */
public final class BuiltinConnectorManager {
    public static final String EXTENSION_ID="rebrowser-builtin@io.github.rebrowser",RESOURCE="resource://android/assets/bridge/",VERSION="1.0.0";
    public enum Status { NOT_STARTED, ASSET_UNAVAILABLE, INSTALLING, READY, FAILED, SUBMISSION_UNKNOWN, RETIRED }
    public interface Listener {void changed(Status state);}
    public static final class Installed {
        private final BuiltinConnectorManager issuer;private final WebExtension extension;
        private Installed(BuiltinConnectorManager issuer,WebExtension extension){this.issuer=issuer;this.extension=extension;}
        WebExtension requireExtension(){issuer.check();if(issuer.status!=Status.READY || issuer.installed!=this)throw new SecurityException("Built-in connector unavailable");return extension;}
    }
    private final Application application;private final GeckoRuntimeOwner runtime;private final ComponentGate gate;private final ComponentGate.BusinessPermit permit;private final ComponentGate.DataEpochPin source;
    private Status status=Status.NOT_STARTED;private Installed installed;
    public BuiltinConnectorManager(Application actualApplication,GeckoRuntimeOwner nativeRuntime,ComponentGate gate,ComponentGate.BusinessPermit nativePermit){
        if(actualApplication==null || nativeRuntime==null || gate==null)throw new IllegalArgumentException("Native connector runtime required");main();application=actualApplication;runtime=nativeRuntime;this.gate=gate;permit=nativePermit;source=gate.captureDataEpoch(nativePermit);runtime.requireNativeBindings(application,gate,source);
    }
    public Status status(){main();return status;}
    public void ensure(Listener listener){
        check();if(listener==null || status!=Status.NOT_STARTED)throw new IllegalStateException("Built-in connector ensure already attempted");
        if(!packagedManifestMatches()){status=Status.ASSET_UNAVAILABLE;notify(listener);return;}
        status=Status.INSTALLING;
        try{GeckoResult<WebExtension> result=runtime.requireRuntime(permit).getWebExtensionController().ensureBuiltIn(RESOURCE,EXTENSION_ID);if(result==null)throw new IllegalStateException("Unknown built-in submission");
            result.accept(extension->{
                main();if(status!=Status.INSTALLING)return;
                try{check();if(extension==null || !extension.isBuiltIn || (extension.flags&WebExtension.Flags.ALLOW_CONTENT_MESSAGING)==0 || !EXTENSION_ID.equals(extension.id) || !RESOURCE.equals(extension.location) || extension.metaData==null || !VERSION.equals(extension.metaData.version))throw new SecurityException("Unexpected built-in connector");installed=new Installed(this,extension);status=Status.READY;}
                catch(RuntimeException stale){status=Status.FAILED;}notify(listener);
            },failure->{main();if(status==Status.INSTALLING){status=Status.FAILED;notify(listener);}});
        }catch(RuntimeException unknown){status=Status.SUBMISSION_UNKNOWN;notify(listener);} // Spend once, no second runtime/install route.
    }
    public Installed requireInstalled(){check();if(status!=Status.READY || installed==null)throw new SecurityException("Built-in connector not installed");return installed;}
    public void retire(){main();status=Status.RETIRED;installed=null;} // No native resource/end/uninstall inference.
    private boolean packagedManifestMatches(){
        try(InputStream in=application.getAssets().open("bridge/manifest.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int zeros=0;for(;;){int n=in.read(buffer);if(n<0)break;if(n==0){if(++zeros>16)return false;continue;}zeros=0;if(n>32768-out.size())return false;out.write(buffer,0,n);}String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(out.toByteArray())).toString();JSONObject manifest=new JSONObject(text);return manifest.getInt("manifest_version")==2 && VERSION.equals(manifest.getString("version")) && EXTENSION_ID.equals(manifest.getJSONObject("browser_specific_settings").getJSONObject("gecko").getString("id"));
        }catch(Exception unavailable){return false;}
    }
    private void check(){main();source.requireBindings(gate,permit);runtime.requireNativeBindings(application,gate,source);}
    private void notify(Listener listener){try{listener.changed(status);}catch(RuntimeException ignored){/* no private exception logging */}}
    private static void main(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native extension owner UI thread required");}
}
