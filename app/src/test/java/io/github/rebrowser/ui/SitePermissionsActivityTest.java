package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import org.junit.Test;

/** Read-only permission rows: expiry/OS labels never imply sensor shutdown or a prompt. */
public final class SitePermissionsActivityTest {
    private static SitePermissionsActivity.Row row(long expires){return new SitePermissionsActivity.Row(new Object(),"Example.COM","p1",SitePermissionsActivity.Capability.GEOLOCATION,SitePermissionsActivity.Expiry.TIMED,expires,SitePermissionsActivity.OsState.GRANTED);}
    @Test public void labelsKeepExpiryAndOsStateSeparate(){
        SitePermissionsActivity.Row row=row(120_000);
        assertTrue(row.label().contains("example.com"));
        assertTrue(row.label().contains("约2分钟"));
        assertTrue(row.label().contains("系统已允许"));
        assertTrue(SitePermissionsActivity.expiryLabel(SitePermissionsActivity.Expiry.EXPIRED,0).contains("不代表传感器已停"));
    }
    @Test public void timedExpiryRequiresBoundedValueAndOthersZero(){
        assertThrows(IllegalArgumentException.class,()->row(-1));
        assertThrows(IllegalArgumentException.class,()->row(366L*24*60*60*1000));
        SitePermissionsActivity.Row none=new SitePermissionsActivity.Row(new Object(),"a.com","p",SitePermissionsActivity.Capability.CAMERA,SitePermissionsActivity.Expiry.NONE,0,SitePermissionsActivity.OsState.UNKNOWN);
        assertEquals(0,none.expiresInMs());
        assertThrows(IllegalArgumentException.class,()->new SitePermissionsActivity.Row(new Object(),"a.com","p",SitePermissionsActivity.Capability.CAMERA,SitePermissionsActivity.Expiry.NONE,5,SitePermissionsActivity.OsState.UNKNOWN));
    }
    @Test public void pageBoundedAndTokenDuplicatesRejected(){
        SitePermissionsActivity.Row row=row(1000);
        SitePermissionsActivity.Page page=new SitePermissionsActivity.Page(java.util.Arrays.asList(row),2);
        assertEquals(1,page.rows().size());
        assertEquals(2,page.revision());
        assertThrows(IllegalArgumentException.class,()->new SitePermissionsActivity.Page(java.util.Arrays.asList(row,row),2));
        java.util.List<SitePermissionsActivity.Row> many=new java.util.ArrayList<>();
        for(int i=0;i<101;i++)many.add(new SitePermissionsActivity.Row(new Object(),"a.com","p",SitePermissionsActivity.Capability.CAMERA,SitePermissionsActivity.Expiry.NONE,0,SitePermissionsActivity.OsState.UNKNOWN));
        assertThrows(IllegalArgumentException.class,()->new SitePermissionsActivity.Page(many,1));
    }
}
