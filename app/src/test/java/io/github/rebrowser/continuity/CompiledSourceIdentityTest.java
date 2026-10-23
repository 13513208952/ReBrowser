package io.github.rebrowser.continuity;

import static org.junit.Assert.*;
import io.github.rebrowser.authority.CompiledPrincipals;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.junit.Test;

public final class CompiledSourceIdentityTest {
    @Test public void actualProvisionedPublicRootDoesNotProveSourceOrUnlock(){
        CompiledSourceIdentity id=CompiledSourceIdentity.current();assertTrue(id.configured());assertEquals(CompiledPrincipals.production().rootSpkiSha256(),id.requireRootSpkiSha256());
        assertTrue(id.rootMatchesLastGood(id.requireRootSpkiSha256()));assertFalse(id.rootMatchesLastGood("0000000000000000000000000000000000000000000000000000000000000000"));
        assertEquals(SourceBootstrap.Status.UNAVAILABLE,SourceBootstrap.inspectOwn(null,null).status());
    }
    @Test public void explicitlyAbsentRootFixtureStillCannotBeUsed()throws Exception{
        Constructor<CompiledSourceIdentity> ctor=CompiledSourceIdentity.class.getDeclaredConstructor(String.class);ctor.setAccessible(true);CompiledSourceIdentity missing=ctor.newInstance((Object)null);assertFalse(missing.configured());assertThrows(IllegalStateException.class,missing::requireRootSpkiSha256);
    }
    @Test public void exactRootEqualityDoesNotSayAnythingAboutSignerOrPolicy()throws Exception{
        // Reflection only in HOST fixture; production exposes no dynamic root registration.
        String a="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",b="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        Constructor<CompiledSourceIdentity> ctor=CompiledSourceIdentity.class.getDeclaredConstructor(String.class);ctor.setAccessible(true);CompiledSourceIdentity id=ctor.newInstance(a);
        assertTrue(id.configured());assertEquals(a,id.requireRootSpkiSha256());assertTrue(id.rootMatchesLastGood(a));assertFalse(id.rootMatchesLastGood(b));
    }
    @Test public void arbitraryLabelsAndUnknownMarkersCannotBeRootFingerprints()throws Exception{
        CompiledSourceIdentity id=CompiledSourceIdentity.current();for(String value:new String[]{null,"","ROOT","../key","AA","AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})assertThrows(IllegalArgumentException.class,()->id.rootMatchesLastGood(value));
        Constructor<CompiledSourceIdentity> ctor=CompiledSourceIdentity.class.getDeclaredConstructor(String.class);ctor.setAccessible(true);assertThrows(InvocationTargetException.class,()->ctor.newInstance("not-a-key"));
    }
}
