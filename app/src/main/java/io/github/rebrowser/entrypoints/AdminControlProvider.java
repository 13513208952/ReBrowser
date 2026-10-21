package io.github.rebrowser.entrypoints;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import io.github.rebrowser.ReBrowserApplication;
import io.github.rebrowser.authority.AdminProofVerifier;
import io.github.rebrowser.bootstrap.StartupCoordinator;
import io.github.rebrowser.control.*;

/** Source-neutral bounded CALL transport subset: public status + explicit KEY-PROOF inspection.
 * No package/ADB/signature allowlist, arbitrary SQL/URI/path, fake current Actor or command effect.
 * Not registered/exported in the product manifest until current durable authority/target adapters
 * and actual Provider IPC tests qualify. Inspection NEVER spends a challenge/reserves/dispatches.
 * Unsupported challenge/admission requests stay UNAVAILABLE rather than minting memory authority. */
public final class AdminControlProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;} // Never initialize runtime/DB/keys from Provider startup.
    @Override public Bundle call(String method,String arg,Bundle extras){
        Bundle result=new Bundle();result.putString("protocol","rebrowser.control/1");
        if(arg!=null || method==null){result.putString("status","INVALID_REQUEST");return result;}
        try{
            StartupCoordinator startup=startup();
            if("publicStatus".equals(method)){
                if(extras!=null && !extras.isEmpty())throw new IllegalArgumentException();result.putString("status","PUBLIC_METADATA");result.putString("source",startup.inspectNativeEntry().source().name());result.putBoolean("formalRootConfigured",startup.hasCompiledAdministratorPolicy());result.putBoolean("commandAdmissionAvailable",false);return result;
            }
            if("inspectProof".equals(method)){
                if(extras==null || extras.size()!=1 || !extras.containsKey("proof"))throw new IllegalArgumentException();extras.setClassLoader(AdminControlProvider.class.getClassLoader());byte[] input=extras.getByteArray("proof");if(input==null || input.length>65536)throw new IllegalArgumentException();
                ControlDecoder.UnverifiedProof decoded=ControlDecoder.decode(input);AdminProofVerifier.KeyProof key=startup.proofVerifier().verifyCompiledKey(decoded);ActionSchemas.ParsedAction schema=ActionSchemas.parse(decoded);
                result.putString("status","KEY_PROOF_ONLY_NOT_ADMITTED");result.putInt("compiledIdentityLevel",key.compiledPrincipal().level());result.putString("typedAction",schema.kind().actionId());result.putBoolean("currentAuthorityVerified",false);return result;
            }
            result.putString("status","NATIVE_ADMISSION_UNAVAILABLE");return result;
        }catch(Exception rejected){result.putString("status","UNAVAILABLE_OR_INVALID");return result;} // No raw key/account/args/source exceptions in IPC.
    }
    private StartupCoordinator startup(){if(getContext()==null || !(getContext().getApplicationContext() instanceof ReBrowserApplication))throw new IllegalStateException("Native application unavailable");return ((ReBrowserApplication)getContext().getApplicationContext()).primaryStartup();}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){throw new UnsupportedOperationException("Use bounded control calls");}
    @Override public String getType(Uri uri){return "application/vnd.rebrowser.control";}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("No control CRUD");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("No control CRUD");}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("No control CRUD");}
}
