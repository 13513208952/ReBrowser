package io.github.reoutlook;

import static org.junit.Assert.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.Test;

public final class ReBrowserMusicSeekDiagnosticsTest {
    private static String dump(ReBrowserMusicSeekDiagnostics d) { var s=new StringWriter();d.dump(new PrintWriter(s));return s.toString(); }
    @Test public void receiptSubmissionAndSdkAcknowledgmentAreSeparateEvidence() {
        var d=new ReBrowserMusicSeekDiagnostics();d.received();d.result("queued");d.result("submitted");
        String s=dump(d);assertTrue(s.contains("received=1 submitted=1 queued=1 rejected=0"));assertTrue(s.contains("sdkCompleted=0"));
        d.observed(1,0);assertTrue(dump(d).contains("sdkCompleted=1 sdkFailed=0"));
    }
    @Test public void untrustedValuesCannotEnterFixedDumpOutput() {
        var d=new ReBrowserMusicSeekDiagnostics();d.result("private-song/account/url");
        assertTrue(dump(d).contains("last=unknown"));assertFalse(dump(d).contains("private-song"));
        for(String value:new String[]{null,"{}","null","\"submitted\\nprivate\"","x".repeat(81)})
            assertEquals("unknown",ReBrowserMusicSeekDiagnostics.javascriptResult(value));
        assertEquals("submitted",ReBrowserMusicSeekDiagnostics.javascriptResult("\"submitted\""));
        assertEquals("outside-playable-range",ReBrowserMusicSeekDiagnostics.javascriptResult("\"outside-playable-range\""));
    }
    @Test public void countersSaturateInsteadOfRetainingRequestsOrOverflowing() {
        var d=new ReBrowserMusicSeekDiagnostics();for(int i=0;i<1_000_002;i++){d.received();d.result("submitted");}
        assertTrue(dump(d).contains("received=1000000 submitted=1000000"));
    }
    @Test public void engineProbeKeepsOnlyBoundedFlagsAndKnownErrorNames() {
        var d=new ReBrowserMusicSeekDiagnostics();d.engine(true,true,true,2,2,0,false,"NotAllowedError");
        assertTrue(dump(d).contains("engine known=true paused=true seeking=true ready=2 network=2 error=0 advancing=false play=NotAllowedError"));
        d.engine(true,false,false,999,-4,999,true,"private-url/account");
        assertTrue(dump(d).contains("ready=-1 network=-1 error=-1 advancing=true play=other"));
        assertFalse(dump(d).contains("private-url"));
    }
    @Test public void documentCountersAreBoundedAndResetIndependentlyOfNativeReceipt() {
        var d=new ReBrowserMusicSeekDiagnostics();d.received();d.result("track-changed");d.observed(Integer.MAX_VALUE,-10);
        assertTrue(dump(d).contains("sdkCompleted=1000000 sdkFailed=0"));d.observed(0,0);
        assertTrue(dump(d).contains("received=1"));assertTrue(dump(d).contains("rejected=1 last=track-changed sdkCompleted=0 sdkFailed=0"));
    }
}
