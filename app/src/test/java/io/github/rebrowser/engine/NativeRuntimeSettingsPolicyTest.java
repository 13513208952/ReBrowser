package io.github.rebrowser.engine;
import static org.junit.Assert.*;
import org.junit.Test;
import org.mozilla.geckoview.GeckoRuntimeSettings;
/** Executes pinned SDK settings construction only; no runtime, filesystem config or native API. */
public final class NativeRuntimeSettingsPolicyTest {
 @Test public void externalDebugConfigurationIsExplicitlyDisabledNotDefaultNull(){assertNull(new GeckoRuntimeSettings.Builder().build().getConfigFilePath());GeckoRuntimeSettings settings=GeckoRuntimeOwner.sourceBoundSettings();assertEquals("",settings.getConfigFilePath());assertFalse(settings.getRemoteDebuggingEnabled());assertFalse(settings.getConsoleOutputEnabled());}
 @Test public void callersCannotContaminateLaterNativeRuntimeSettings(){GeckoRuntimeSettings first=GeckoRuntimeOwner.sourceBoundSettings();first.setRemoteDebuggingEnabled(true);first.setConsoleOutputEnabled(true);GeckoRuntimeSettings second=GeckoRuntimeOwner.sourceBoundSettings();assertNotSame(first,second);assertEquals("",second.getConfigFilePath());assertFalse(second.getRemoteDebuggingEnabled());assertFalse(second.getConsoleOutputEnabled());}
}
