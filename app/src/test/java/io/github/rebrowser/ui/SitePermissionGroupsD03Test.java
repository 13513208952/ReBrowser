package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import org.junit.Test;

/** Stable site grouping; no prompt/grant is executed. */
public final class SitePermissionGroupsD03Test {
    private static final Object P1=new Object(),P2=new Object();
    private static SitePermissionsActivity.Row row(String host,String partition,SitePermissionsActivity.Capability capability){
        return new SitePermissionsActivity.Row(new Object(),host,partition,capability,SitePermissionsActivity.Expiry.NONE,0,SitePermissionsActivity.OsState.UNKNOWN,java.util.Collections.<SitePermissionsActivity.RequestedChange>emptySet(),partition.equals("p1")?P1:P2);
    }
    @Test public void groupingUsesExactPartitionAndHost(){
        SitePermissionsActivity.Page page=new SitePermissionsActivity.Page(java.util.Arrays.asList(
                row("a.example","p1",SitePermissionsActivity.Capability.CAMERA),
                row("a.example","p1",SitePermissionsActivity.Capability.GEOLOCATION),
                row("a.example","p2",SitePermissionsActivity.Capability.CAMERA)),4);
        java.util.List<SitePermissionsActivity.Group> groups=SitePermissionsActivity.Groups.of(page);
        assertEquals(2,groups.size());
        assertEquals(2,groups.get(0).rows().size());
        assertEquals("a.example",groups.get(0).hostLabel());
        assertEquals("p1",groups.get(0).partitionLabel());
        assertEquals(SitePermissionsActivity.Capability.GEOLOCATION,groups.get(0).rows().get(0).capability());
        assertEquals("p2",groups.get(1).partitionLabel());
    }
    @Test public void revokeOfferAndExpiryLabelsStayExplicit(){
        SitePermissionsActivity.Row offered=new SitePermissionsActivity.Row(new Object(),"a.example","p",SitePermissionsActivity.Capability.CAMERA,SitePermissionsActivity.Expiry.TIMED,120_000,SitePermissionsActivity.OsState.GRANTED,true);
        SitePermissionsActivity.Row noRevoke=new SitePermissionsActivity.Row(new Object(),"b.example","p",SitePermissionsActivity.Capability.CAMERA,SitePermissionsActivity.Expiry.UNKNOWN,0,SitePermissionsActivity.OsState.UNKNOWN,false);
        assertTrue(offered.nativeRevokeOffered());
        assertFalse(noRevoke.nativeRevokeOffered());
        assertTrue(offered.label().contains("约2分钟"));
        assertTrue(SitePermissionsActivity.expiryLabel(SitePermissionsActivity.Expiry.EXPIRED,0).contains("不代表传感器已停"));
    }
    @Test public void noPermissionsChangeIsInferredWithoutAnExplicitOffer(){
        SitePermissionsActivity.Row defaultRow=row("a.example","p",SitePermissionsActivity.Capability.CAMERA);
        for(SitePermissionsActivity.RequestedChange action:SitePermissionsActivity.RequestedChange.values())
            assertFalse(defaultRow.nativeOffers(action));
        SitePermissionsActivity.Row explicit=new SitePermissionsActivity.Row(new Object(),"a.example","p",
                SitePermissionsActivity.Capability.CAMERA,SitePermissionsActivity.Expiry.NONE,0,
                SitePermissionsActivity.OsState.UNKNOWN,
                java.util.EnumSet.of(SitePermissionsActivity.RequestedChange.GRANT,SitePermissionsActivity.RequestedChange.RESET));
        assertTrue(explicit.nativeOffers(SitePermissionsActivity.RequestedChange.GRANT));
        assertTrue(explicit.nativeOffers(SitePermissionsActivity.RequestedChange.RESET));
        assertFalse(explicit.nativeRevokeOffered());
        assertThrows(IllegalArgumentException.class,()->explicit.nativeOffers(null));
    }
}
