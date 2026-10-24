package io.github.rebrowser.engine;

import java.lang.reflect.Field;
import org.junit.Test;
import static org.junit.Assert.*;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.gecko.util.GeckoBundle;
import org.mozilla.geckoview.GeckoSession.PromptDelegate.BasePrompt;
import org.mozilla.geckoview.GeckoSession.PromptDelegate.BeforeUnloadPrompt;
import org.mozilla.geckoview.GeckoSession.PromptDelegate.RepostConfirmPrompt;

/** Actual pinned SDK response objects only, NOT Android dialogs, Gecko navigation or POST IO.
 * Private reflection is HOST inspection of the pinned result contract, never production access. */
public final class NativeNavigationPromptSdkTest {
    private static final class Unload extends BeforeUnloadPrompt {Unload(){super("host-unload",null);}}
    private static final class Repost extends RepostConfirmPrompt {Repost(){super("host-repost",null);}}
    private static Object field(BasePrompt p,String name)throws Exception{Field f=BasePrompt.class.getDeclaredField(name);f.setAccessible(true);return f.get(p);}
    private static boolean allow(BasePrompt p)throws Exception{return ((GeckoBundle)field(p,"mResult")).getBoolean("allow");}
    @Test public void dismissalProducesNoAllowResultAndCannotBeAnsweredTwice()throws Exception{
        BasePrompt[] prompts={new Unload(),new Repost()};
        for(BasePrompt prompt:prompts){assertNotNull(prompt.dismiss());assertTrue(prompt.isComplete());assertEquals(Boolean.FALSE,field(prompt,"mIsConfirmed"));assertNull(field(prompt,"mResult"));assertThrows(RuntimeException.class,prompt::dismiss);}
    }
    @Test public void explicitPositiveAndNegativeRemainDifferentOneUseSdkAnswers()throws Exception{
        Unload yes=new Unload(),no=new Unload();Repost resend=new Repost(),stay=new Repost();
        yes.confirm(AllowOrDeny.ALLOW);no.confirm(AllowOrDeny.DENY);resend.confirm(AllowOrDeny.ALLOW);stay.confirm(AllowOrDeny.DENY);
        assertTrue(allow(yes));assertFalse(allow(no));assertTrue(allow(resend));assertFalse(allow(stay));
        assertThrows(RuntimeException.class,()->yes.confirm(AllowOrDeny.ALLOW));assertThrows(RuntimeException.class,()->stay.confirm(AllowOrDeny.ALLOW));
    }
    @Test public void nullSdkDecisionIsAllowSoItMustNeverImplementCancellation()throws Exception{
        Unload unload=new Unload();Repost repost=new Repost();unload.confirm(null);repost.confirm(null);
        assertTrue(allow(unload));assertTrue(allow(repost));
    }
}
