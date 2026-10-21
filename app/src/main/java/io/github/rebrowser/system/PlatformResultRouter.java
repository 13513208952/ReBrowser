package io.github.rebrowser.system;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.data.NativeRecordCodec;
import java.security.SecureRandom;
import java.util.*;

/** Actual owning Activity result routing for OWN explicit document pickers. Not an exported
 * Intent-result authenticator or generic action/URI launcher. Late/foreign request codes cannot
 * become a newer request; unknown submission keeps the slot. Retirement is not OS-work end. */
public final class PlatformResultRouter {
    public interface Callback {void selected(Selection exactSelection);void cancelledOrUnavailable();}
    public static final class Selection {
        private final PlatformResultRouter issuer;private final Uri uri;private final int flags;private final boolean save;
        private Selection(PlatformResultRouter issuer,Uri uri,int flags,boolean save){this.issuer=issuer;this.uri=uri;this.flags=flags;this.save=save;}
        public Uri requireCurrentUri(){issuer.source.requireCurrent();if(issuer.retired)throw denied();return uri;}
        public int returnedGrantFlags(){requireCurrentUri();return flags;}
        public boolean isSaveSelection(){requireCurrentUri();return save;}
    }
    private static final class Pending {final int code;final Callback callback;final boolean save;boolean delivered;Pending(int code,Callback callback,boolean save){this.code=code;this.callback=callback;this.save=save;}}
    private final Activity activity;private final ComponentGate.DataEpochPin source;private final Set<Integer> used=new HashSet<>();private final SecureRandom random=new SecureRandom();private Pending active;private volatile boolean retired;
    public PlatformResultRouter(Activity actualOwnedActivity,ComponentGate.DataEpochPin nativeSource){if(actualOwnedActivity==null || nativeSource==null)throw new IllegalArgumentException("Native picker owner required");main();activity=actualOwnedActivity;source=nativeSource;source.requireCurrent();}
    public void openDocument(String mime,Callback callback){start(false,mime,null,callback);}
    public void createDocument(String mime,String displayName,Callback callback){start(true,mime,displayName,callback);}
    private void start(boolean save,String mime,String name,Callback callback){
        main();source.requireCurrent();if(retired || active!=null || callback==null || activity.isFinishing() || activity.isDestroyed() || !activity.hasWindowFocus() || used.size()>=256)throw denied();
        if(mime==null || mime.length()>96 || !mime.matches("[a-zA-Z0-9!#$&^_.+*-]+/[a-zA-Z0-9!#$&^_.+*-]+"))throw denied();if(save){NativeRecordCodec.validateText(name,640,false);if(name.contains("/") || name.contains("\\") || name.equals(".") || name.equals(".."))throw denied();}
        int code=-1;for(int n=0;n<32;n++){int candidate=0x4000+random.nextInt(0x4000);if(!used.contains(candidate)){code=candidate;break;}}if(code<0)throw denied();used.add(code);active=new Pending(code,callback,save);
        Intent intent=new Intent(save?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);if(save)intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION).putExtra(Intent.EXTRA_TITLE,name);
        activity.startActivityForResult(intent,code); // Any exception is UNKNOWN; retain exact pending owner, never second route.
    }
    /** Called ONLY by the actual native Activity.onActivityResult adapter, never IPC/extras. */
    public boolean onNativeActivityResult(int requestCode,int resultCode,Intent untrustedData){
        main();Pending pending=active;if(pending==null || pending.code!=requestCode || pending.delivered)return false;pending.delivered=true;active=null;Selection selection=null;
        try{source.requireCurrent();if(!retired && resultCode==Activity.RESULT_OK && untrustedData!=null){Uri uri=untrustedData.getData();if(uri!=null && "content".equals(uri.getScheme()) && uri.getAuthority()!=null && uri.getUserInfo()==null){int flags=untrustedData.getFlags();int required=pending.save?Intent.FLAG_GRANT_WRITE_URI_PERMISSION:Intent.FLAG_GRANT_READ_URI_PERMISSION;if((flags&required)==required)selection=new Selection(this,uri,flags,pending.save);}}}catch(RuntimeException stale){/* suppress positive, do not log URI */}
        if(selection==null)pending.callback.cancelledOrUnavailable();else pending.callback.selected(selection);return true;
    }
    public void retire(){main();retired=true;} // No release/negative result fabrication for outstanding OS work.
    public boolean ownsPendingOsRequest(){main();return active!=null;}
    private static void main(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Native Activity result thread required");}
    private static SecurityException denied(){return new SecurityException("Native document picker unavailable");}
}
