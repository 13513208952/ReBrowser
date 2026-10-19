package io.github.rebrowser.continuity;

import static org.junit.Assert.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.Test;

/** Binding metadata only: no OS authentication, callback, no-lock exemption or ticket minted. */
public final class OwnerAuthenticationBindingTest {
    private static final String HASH="0000000000000000000000000000000000000000000000000000000000000000";
    @Test public void onlyFinalTwoForceStepsCanRequestFreshOwnerVerification(){
        for(ContinuityState.Confirmation step:ContinuityState.Confirmation.values()){
            if(step==ContinuityState.Confirmation.FORCE_2 || step==ContinuityState.Confirmation.FORCE_3)assertEquals(step,new OwnerAuthentication.Binding(new byte[16],HASH,HASH,step).confirmation());
            else assertThrows(IllegalArgumentException.class,()->new OwnerAuthentication.Binding(new byte[16],HASH,HASH,step));
        }
        assertThrows(IllegalArgumentException.class,()->new OwnerAuthentication.Binding(new byte[16],HASH,HASH,null));
    }
    @Test public void epochIsDefensiveAndEachRequestHasIndependentNativeChallenge(){
        byte[] epoch=new byte[16];OwnerAuthentication.Binding a=new OwnerAuthentication.Binding(epoch,HASH,HASH,ContinuityState.Confirmation.FORCE_2),b=new OwnerAuthentication.Binding(epoch,HASH,HASH,ContinuityState.Confirmation.FORCE_3);epoch[0]=1;
        assertArrayEquals(new byte[16],a.oldEpoch());byte[] copy=a.oldEpoch();copy[1]=1;assertArrayEquals(new byte[16],a.oldEpoch());assertNotEquals(a.challengeRef(),b.challengeRef());assertTrue(a.challengeRef().matches("[0-9a-f]{64}"));
        assertEquals(HASH,a.candidateRootSha256());assertEquals(HASH,a.candidateSignerSha256());
    }
    @Test public void unboundedBindingsAndPublicSuccessTicketConstructionAreForbidden(){
        assertThrows(IllegalArgumentException.class,()->new OwnerAuthentication.Binding(new byte[15],HASH,HASH,ContinuityState.Confirmation.FORCE_2));assertThrows(IllegalArgumentException.class,()->new OwnerAuthentication.Binding(new byte[16],"root",HASH,ContinuityState.Confirmation.FORCE_2));
        assertThrows(IllegalArgumentException.class,()->new OwnerAuthentication.Binding(new byte[16],HASH,"../key",ContinuityState.Confirmation.FORCE_2));
        for(Constructor<?> ctor:OwnerAuthentication.Ticket.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(ctor.getModifiers()));
        assertEquals(0,OwnerAuthentication.Ticket.class.getConstructors().length);
    }
}
