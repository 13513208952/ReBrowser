package io.github.rebrowser.continuity;

import static org.junit.Assert.*;
import io.github.rebrowser.crypto.CryptoEncoding;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public final class ContinuityStateTest {
    @Test public void allFourWarningsMatchExactOwnerO16Bytes(){
        String[] expected={"a4cabcc02eeb7b8f0960f111d8fa51b77cfb4d5894886466503cb2aa17e6bd7e","462ab9407c44a1dd05979ee9242cf4b41a42e6db353340fb7137cba222c0372d","cc987a71058cfd393d7e53f05d6e3c3d474ee9f2a6a758c036d1f1570f4bd2ed","bc8e1fb15eb715f4208861ceb205180ede99eb91a4f622fa2bd873661f451019"};
        int i=0;for(ContinuityState.Confirmation step:ContinuityState.Confirmation.values())assertEquals(expected[i++],CryptoEncoding.sha256Hex(step.warning().getBytes(StandardCharsets.UTF_8)));assertEquals(4,i);
    }
    @Test public void onlyForceTwoAndThreeNeedSeparateAndroidVerification(){
        assertFalse(ContinuityState.Confirmation.TRUST_SOURCE.requiresSeparateAndroidOwnerVerification());assertFalse(ContinuityState.Confirmation.FORCE_1.requiresSeparateAndroidOwnerVerification());
        assertTrue(ContinuityState.Confirmation.FORCE_2.requiresSeparateAndroidOwnerVerification());assertTrue(ContinuityState.Confirmation.FORCE_3.requiresSeparateAndroidOwnerVerification());
    }
    @Test public void clickingFourStepsDoesNotProduceTrustedStateOrErasureReceipt(){
        assertEquals(ContinuityState.Confirmation.FORCE_1,ContinuityState.Confirmation.TRUST_SOURCE.next());assertEquals(ContinuityState.Confirmation.FORCE_2,ContinuityState.Confirmation.FORCE_1.next());assertEquals(ContinuityState.Confirmation.FORCE_3,ContinuityState.Confirmation.FORCE_2.next());assertNull(ContinuityState.Confirmation.FORCE_3.next());
        assertNotEquals(ContinuityState.Phase.NORMAL_TRUSTED,ContinuityState.Phase.ERASURE_AUTHORIZED_LOCKED);assertNotEquals(ContinuityState.Phase.NORMAL_TRUSTED,ContinuityState.Phase.VERIFIED_EMPTY_EPOCH_PENDING_COMMIT);
    }
}
