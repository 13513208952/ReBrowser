package io.github.reoutlook;

/** Prints the compiled document-start policy for the standalone JavaScript regression tests. */
public final class DumpPermissionScript {
    public static void main(String[] args) {
        System.out.print(ReBrowserPermissionScript.DOCUMENT_START);
    }
}
