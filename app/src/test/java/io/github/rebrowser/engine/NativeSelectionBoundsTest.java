package io.github.rebrowser.engine;
import static org.junit.Assert.*;
import org.junit.Test;
/** Actual app-side native-selection text admission helper, NOT clipboard or Android UI. */
public final class NativeSelectionBoundsTest {
 @Test public void caretAndMultilineNativeSelectionsRemainEligible(){assertTrue(SelectionDelegate.validSelectionText(""));assertTrue(SelectionDelegate.validSelectionText("line one\nline two\t😀"));}
 @Test public void malformedOrMissingNativeSelectionTextRejects(){assertFalse(SelectionDelegate.validSelectionText(null));assertFalse(SelectionDelegate.validSelectionText("\ud800"));assertFalse(SelectionDelegate.validSelectionText("\udc00"));assertFalse(SelectionDelegate.validSelectionText("x\ud800x"));}
 @Test public void explicitExactBudgetDoesNotSilentlyTruncate(){String exact="x".repeat(SelectionDelegate.MAX_SELECTION_CHARS);assertTrue(SelectionDelegate.validSelectionText(exact));assertFalse(SelectionDelegate.validSelectionText(exact+"x"));}
}
