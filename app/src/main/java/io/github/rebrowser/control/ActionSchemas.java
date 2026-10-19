package io.github.rebrowser.control;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Concrete typed argument schemas for FOUR understood native window actions only.
 * All other action IDs fail closed, even if present in the broader policy registry.
 * Syntax/type success is NOT key proof, target ownership, effective authority, current
 * presentation/source revision, URI grant, owner consent or permission to dispatch.
 * Native UI/admin must reach the same domain service after those current checks.
 */
public final class ActionSchemas {
    public static final long MAX_SAFE_INTEGER=9_007_199_254_740_991L;
    private static final Pattern WINDOW=Pattern.compile("win_[0-9a-f]{32}");
    private static final Pattern APK=Pattern.compile("apk_[0-9a-f]{32}");
    public enum Kind {
        OPEN_DEFAULT_BROWSER_FLOW("host.openDefaultBrowserFlow"),
        OPEN_PERMISSION_SETTINGS("host.openPermissionSettings"),
        OPEN_INSTALLER("host.openInstaller"), WINDOW_BACK("host.window.back");
        private final String id;
        Kind(String id){this.id=id;}
        public String actionId(){return id;}
    }
    public enum PermissionPurpose {
        CAMERA("camera"), MICROPHONE("microphone"), LOCATION("location"), NOTIFICATIONS("notifications"), OVERLAY("overlay"), INSTALL_SOURCE("install-source");
        private final String value;
        PermissionPurpose(String value){this.value=value;}
        public String wireValue(){return value;}
    }
    /** Parsed claims only. Preserve the original signed frame; never reserialize args. */
    public static final class ParsedAction {
        private final ControlDecoder.UnverifiedProof proof;
        private final Kind kind;
        private final String window,source;
        private final long presentationRevision,sourceRevision;
        private final PermissionPurpose purpose;
        private ParsedAction(ControlDecoder.UnverifiedProof proof,Kind kind,String window,long presentationRevision,String source,long sourceRevision,PermissionPurpose purpose){this.proof=proof;this.kind=kind;this.window=window;this.presentationRevision=presentationRevision;this.source=source;this.sourceRevision=sourceRevision;this.purpose=purpose;}
        public ControlDecoder.UnverifiedProof unverifiedRequest(){return proof;}
        public Kind kind(){return kind;}
        public String windowRef(){return window;}
        public long expectedPresentationRevision(){return presentationRevision;}
        public PermissionPurpose permissionPurpose(){if(kind!=Kind.OPEN_PERMISSION_SETTINGS)throw wrongView();return purpose;}
        public boolean hasApkSource(){if(kind!=Kind.OPEN_INSTALLER)throw wrongView();return source!=null;}
        /** Installer only: null means own OS picker, NOT permission to guess a page/download/path. */
        public String apkSourceRef(){if(kind!=Kind.OPEN_INSTALLER)throw wrongView();return source;}
        public long expectedSourceRevision(){if(source==null)throw wrongView();return sourceRevision;}
    }
    private ActionSchemas(){}
    public static ParsedAction parse(ControlDecoder.UnverifiedProof request){
        if(request==null)throw invalid();Kind kind=null;
        String action=request.frame().ascii(ControlEnvelope.Field.ACTION_ID);
        for(Kind candidate:Kind.values())if(candidate.id.equals(action)){kind=candidate;break;}
        if(kind==null)throw invalid();Map<String,ControlDecoder.Value> args=request.args().object();
        for(String key:args.keySet()){
            boolean common=key.equals("windowRef") || key.equals("expectedPresentationRevision");
            boolean optionalSource=kind==Kind.OPEN_INSTALLER && (key.equals("apkSourceRef") || key.equals("expectedSourceRevision"));
            boolean permission=kind==Kind.OPEN_PERMISSION_SETTINGS && key.equals("purpose");
            if(!common && !optionalSource && !permission)throw invalid();
        }
        String window=ref(required(args,"windowRef"),WINDOW);
        long revision=integer(required(args,"expectedPresentationRevision"));
        PermissionPurpose purpose=null;
        if(kind==Kind.OPEN_PERMISSION_SETTINGS){
            String value=required(args,"purpose").string();
            for(PermissionPurpose candidate:PermissionPurpose.values())if(candidate.value.equals(value)){purpose=candidate;break;}
            if(purpose==null)throw invalid();
        }
        String source=null;long sourceRevision=0;
        if(kind==Kind.OPEN_INSTALLER){
            boolean hasSource=args.containsKey("apkSourceRef"),hasRevision=args.containsKey("expectedSourceRevision");
            if(hasSource!=hasRevision)throw invalid();
            if(hasSource){source=ref(args.get("apkSourceRef"),APK);sourceRevision=integer(args.get("expectedSourceRevision"));}
        }
        return new ParsedAction(request,kind,window,revision,source,sourceRevision,purpose);
    }
    private static ControlDecoder.Value required(Map<String,ControlDecoder.Value> args,String name){ControlDecoder.Value value=args.get(name);if(value==null)throw invalid();return value;}
    private static String ref(ControlDecoder.Value value,Pattern pattern){String text=value.string();if(!pattern.matcher(text).matches())throw invalid();return text;}
    private static long integer(ControlDecoder.Value value){
        String token=value.numberToken();if(token.equals("-0"))return 0; // JSON integer zero, no numeric coercion
        if(token.length()==0 || token.length()>16)throw invalid();
        for(int i=0;i<token.length();i++)if(token.charAt(i)<'0' || token.charAt(i)>'9')throw invalid();
        long result=Long.parseLong(token);if(result>MAX_SAFE_INTEGER)throw invalid();return result;
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Unsupported action or invalid typed arguments");}
    private static IllegalStateException wrongView(){return new IllegalStateException("Field not applicable to parsed action");}
}
