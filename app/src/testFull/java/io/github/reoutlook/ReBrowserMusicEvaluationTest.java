package io.github.reoutlook;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReBrowserMusicEvaluationTest {
    @Test public void timedOutRequestStillOwnsItsSlotUntilRealCompletion() {
        ReBrowserMusicEvaluation gate = new ReBrowserMusicEvaluation();
        int token = gate.begin(); assertNotEquals(0, token);
        assertTrue(gate.timeout(token)); assertTrue(gate.expired());
        for (int i = 0; i < 10_000; i++) assertEquals(0, gate.begin());
        assertFalse(gate.timeout(token)); assertTrue(gate.finish(token));
        assertFalse(gate.expired()); assertNotEquals(0, gate.begin());
    }
    @Test public void oldDocumentCannotReleaseNewDocumentsRequest() {
        ReBrowserMusicEvaluation gate = new ReBrowserMusicEvaluation();
        int old = gate.begin(); gate.reset(); int current = gate.begin();
        assertNotEquals(old, current); assertFalse(gate.finish(old)); assertFalse(gate.timeout(old));
        assertEquals(0, gate.begin()); assertTrue(gate.finish(current)); assertFalse(gate.finish(current));
    }
}
