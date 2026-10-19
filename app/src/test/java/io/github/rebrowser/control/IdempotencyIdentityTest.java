package io.github.rebrowser.control;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;

/** Content-key invariants only; Android SQLite transactions are not executed by HOST tests. */
public final class IdempotencyIdentityTest {
    private static IdempotencyStore.ContentKey key(int policyRevision,String action,String scope,String grant,byte[] request,String args)throws Exception{
        byte[] policy=new byte[32];Arrays.fill(policy,(byte)policyRevision);
        ControlChallengeStore store=new ControlChallengeStore(new ControlChallengeStore.Context(new byte[32],new byte[16],policy,policyRevision),()->100L,8,4);
        ControlEnvelope frame=store.issue(new ControlChallengeStore.Template("public-dummy-principal",action,scope,grant,request,new byte[32],args.getBytes(StandardCharsets.UTF_8)));
        return IdempotencyStore.contentKey(ControlChallengeStoreTest.sign(frame));
    }
    @Test public void freshProofPolicyAndGrantDoNotMakeNewOperation()throws Exception{
        IdempotencyStore.ContentKey a=key(1,"host.window.back","window_synthetic","",new byte[16],"{}"),b=key(2,"host.window.back","window_synthetic","grant_new",new byte[16],"{}");
        assertEquals(a.principalFingerprint(),b.principalFingerprint());assertEquals(a.targetFingerprint(),b.targetFingerprint());assertEquals(a.dataEpoch(),b.dataEpoch());assertEquals(a.requestId(),b.requestId());assertArrayEquals(a.contentSha256(),b.contentSha256());
    }
    @Test public void changedActionScopeOrOriginalArgsConflictsButRequestIdRemainsSeparate()throws Exception{
        IdempotencyStore.ContentKey original=key(1,"host.window.back","window_synthetic","",new byte[16],"{}");
        for(IdempotencyStore.ContentKey changed:new IdempotencyStore.ContentKey[]{key(1,"host.openInstaller","window_synthetic","",new byte[16],"{}"),key(1,"host.window.back","window_other","",new byte[16],"{}"),key(1,"host.window.back","window_synthetic","",new byte[16]," { } ")})assertFalse(Arrays.equals(original.contentSha256(),changed.contentSha256()));
        byte[] id=new byte[16];id[0]=1;IdempotencyStore.ContentKey newRequest=key(1,"host.window.back","window_synthetic","",id,"{}");assertNotEquals(original.requestId(),newRequest.requestId());assertArrayEquals(original.contentSha256(),newRequest.contentSha256());
    }
    @Test public void digestCopiesAndSchemaArraysAreImmutable()throws Exception{
        IdempotencyStore.ContentKey key=key(1,"host.window.back","window_synthetic","",new byte[16],"{}");byte[] before=key.contentSha256(),copy=key.contentSha256();copy[0]^=1;assertArrayEquals(before,key.contentSha256());
        String[] schema=IdempotencyStore.schemaStatements();String first=schema[0];schema[0]="DROP TABLE synthetic";assertEquals(first,IdempotencyStore.schemaStatements()[0]);
    }
}
