package io.github.rebrowser.control;

import static org.junit.Assert.*;
import io.github.rebrowser.crypto.CryptoEncoding;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/** Syntactic DER r=s=1 is deliberately NOT a valid authenticated proof. No effects. */
public final class ActionSchemasTest {
    private static final String WINDOW="win_00000000000000000000000000000000";
    private static final String SOURCE="apk_11111111111111111111111111111111";
    private static final String COMMON="\"windowRef\":\""+WINDOW+"\",\"expectedPresentationRevision\":1";
    private static ControlDecoder.UnverifiedProof request(String action,String args)throws Exception{
        ControlChallengeStore server=new ControlChallengeStore(new ControlChallengeStore.Context(new byte[32],new byte[16],new byte[32],0),()->0L,1,1);
        ControlEnvelope frame=server.issue(new ControlChallengeStore.Template("unverified-syntax-fixture",action,"scope_synthetic","",new byte[16],new byte[32],args.getBytes(StandardCharsets.UTF_8)));
        String wire="{\"protocol\":\"rebrowser.control/1\",\"frameB64\":\""+CryptoEncoding.base64Url(frame.signedBytes())+"\",\"signatureB64\":\""+CryptoEncoding.base64Url(new byte[]{0x30,6,2,1,1,2,1,1})+"\"}";
        return ControlDecoder.decode(wire.getBytes(StandardCharsets.UTF_8));
    }
    private static ActionSchemas.ParsedAction parse(String action,String args)throws Exception{return ActionSchemas.parse(request(action,args));}
    private static void bad(String action,String args){assertThrows(IllegalArgumentException.class,()->parse(action,args));}
    @Test public void fourTypedActionsAndPermissionPurposesOnly()throws Exception{
        for(ActionSchemas.Kind kind:ActionSchemas.Kind.values()){
            String extra=kind==ActionSchemas.Kind.OPEN_PERMISSION_SETTINGS?",\"purpose\":\"camera\"":"";
            ActionSchemas.ParsedAction a=parse(kind.actionId(),"{"+COMMON+extra+"}");assertEquals(kind,a.kind());assertEquals(WINDOW,a.windowRef());assertEquals(1,a.expectedPresentationRevision());
        }
        for(ActionSchemas.PermissionPurpose purpose:ActionSchemas.PermissionPurpose.values())assertEquals(purpose,parse("host.openPermissionSettings","{"+COMMON+",\"purpose\":\""+purpose.wireValue()+"\"}").permissionPurpose());
        bad("host.openPermissionSettings","{"+COMMON+",\"purpose\":\"all-permissions\"}");
    }
    @Test public void unknownActionsNeverFallThroughToPolicyRegistryOrStringDispatch(){
        for(String id:new String[]{"music.play","host.window.Back","host.window.back.extra","host.openAnything"})bad(id,"{"+COMMON+"}");
        assertThrows(IllegalArgumentException.class,()->ActionSchemas.parse(null));
    }
    @Test public void fieldsAreExactRequiredAndNeverCoerced(){
        bad("host.window.back","{}");bad("host.window.back","{\"windowRef\":null,\"expectedPresentationRevision\":1}");
        for(String extra:new String[]{"\"package\":\"other.app\"","\"uri\":\"file:///private\"","\"intentAction\":\"anything\"","\"purpose\":\"camera\"","\"apkSourceRef\":\""+SOURCE+"\""})bad("host.window.back","{"+COMMON+","+extra+"}");
        bad("host.window.back","{\"windowRef\":\""+WINDOW+"\"}");bad("host.openPermissionSettings","{"+COMMON+"}");
    }
    @Test public void exactSafeIntegerTokensAndBounds()throws Exception{
        for(String number:new String[]{"0","-0","9007199254740991"})assertTrue(parse("host.window.back","{\"windowRef\":\""+WINDOW+"\",\"expectedPresentationRevision\":"+number+"}").expectedPresentationRevision()>=0);
        for(String number:new String[]{"-1","1.0","1e0","9007199254740992","99999999999999999999999999999999","true","\"1\"","null","[]","{}"})bad("host.window.back","{\"windowRef\":\""+WINDOW+"\",\"expectedPresentationRevision\":"+number+"}");
    }
    @Test public void opaqueRefSyntaxDoesNotAllowPathsUppercaseOrWrongNamespaces(){
        for(String ref:new String[]{"../private",SOURCE,"win_ABCDEF00000000000000000000000000","win_0",WINDOW+"0",WINDOW+"/child"})bad("host.window.back","{\"windowRef\":\""+ref+"\",\"expectedPresentationRevision\":1}");
    }
    @Test public void installerPairIsAllOrNothingPickerIsExplicitAndViewsAreTyped()throws Exception{
        ActionSchemas.ParsedAction back=parse("host.window.back","{"+COMMON+"}");assertThrows(IllegalStateException.class,back::hasApkSource);assertThrows(IllegalStateException.class,back::apkSourceRef);
        ActionSchemas.ParsedAction picker=parse("host.openInstaller","{"+COMMON+"}");assertFalse(picker.hasApkSource());assertNull(picker.apkSourceRef());assertThrows(IllegalStateException.class,picker::expectedSourceRevision);assertThrows(IllegalStateException.class,picker::permissionPurpose);
        ActionSchemas.ParsedAction source=parse("host.openInstaller","{"+COMMON+",\"apkSourceRef\":\""+SOURCE+"\",\"expectedSourceRevision\":4}");assertTrue(source.hasApkSource());assertEquals(SOURCE,source.apkSourceRef());assertEquals(4,source.expectedSourceRevision());
        for(String extra:new String[]{"\"apkSourceRef\":\""+SOURCE+"\"","\"expectedSourceRevision\":1","\"apkSourceRef\":null,\"expectedSourceRevision\":1","\"apkSourceRef\":\""+WINDOW+"\",\"expectedSourceRevision\":1","\"apkSourceRef\":\""+SOURCE+"\",\"expectedSourceRevision\":1.0"})bad("host.openInstaller","{"+COMMON+","+extra+"}");
    }
    @Test public void originalSignedArgsArePreservedWithoutJsonNormalization()throws Exception{
        String args=" {\n  "+COMMON+" } ";ControlDecoder.UnverifiedProof proof=request("host.window.back",args);ActionSchemas.ParsedAction a=ActionSchemas.parse(proof);
        assertSame(proof,a.unverifiedRequest());assertArrayEquals(args.getBytes(StandardCharsets.UTF_8),a.unverifiedRequest().frame().bytes(ControlEnvelope.Field.ARGS_BYTES));
        bad("host.window.back","{"+COMMON+",\"window\\u0052ef\":\""+WINDOW+"\"}");
    }
}
