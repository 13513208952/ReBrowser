package io.github.rebrowser.core;

import static org.junit.Assert.*;
import org.junit.Test;

/** Actual production projection model only; it has no receipt/authority semantics. */
public final class DomainResultTest {
    @Test public void waitingRequiresAConditionText(){
        DomainResult waiting=new DomainResult(DomainResult.Kind.WAITING,DomainResult.Evidence.NATIVE_REPORTED,DomainResult.Reason.NONE,"原生窗口");
        assertEquals("等待原生状态",waiting.label());
        assertThrows(IllegalArgumentException.class,()->new DomainResult(DomainResult.Kind.WAITING,DomainResult.Evidence.UNVERIFIED,DomainResult.Reason.NONE,null));
        assertThrows(IllegalArgumentException.class,()->new DomainResult(DomainResult.Kind.OBSERVATION,DomainResult.Evidence.UNVERIFIED,DomainResult.Reason.NONE,"x"));
    }
    @Test public void labelsNeverClaimCompletionOrAuthority(){
        for(DomainResult.Kind kind:DomainResult.Kind.values()){
            DomainResult result=new DomainResult(kind,DomainResult.Evidence.UNVERIFIED,DomainResult.Reason.OUTCOME_UNKNOWN,kind==DomainResult.Kind.WAITING?"等待":null);
            String label=result.label();
            assertFalse(label.contains("已完成"));
            assertFalse(label.contains("已成功"));
            assertFalse(label.contains("已授权"));
            assertFalse(label.contains("授权成功"));
        }
    }
    @Test public void boundedTextRejectsControlAndSurrogates(){
        assertEquals("a\tb",DomainResult.text("a\tb",3,true));
        assertThrows(IllegalArgumentException.class,()->DomainResult.text("a\tb",3,false));
        assertThrows(IllegalArgumentException.class,()->DomainResult.text("a\nb",3,false));
        assertThrows(IllegalArgumentException.class,()->DomainResult.text("\ud800",2,true));
        assertThrows(IllegalArgumentException.class,()->DomainResult.text("abcdef",5,true));
    }
}
