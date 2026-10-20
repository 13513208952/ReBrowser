package io.github.rebrowser.crypto;

/**
 * Fixed key-domain and native private-record vocabulary ONLY. Nothing here generates, imports,
 * derives, wraps or destroys a key; a Domain value is not permission to use it. Existing
 * ObjectAead/RBMOBJ1 mail purposes and their byte format are deliberately untouched.
 */
public final class KeyPurpose {
    public enum Domain {
        APK_SIGN, ADMIN_ROOT, ADMIN_HIGH, ADMIN_LOW, DEVICE_INSTALL, DATA_WRAP, DATA_OBJECT
    }

    /** Native private record classes with fixed IDs and conservative size ceilings. */
    public enum RecordKind {
        BROWSER_CATALOG(1, 4L * 1024 * 1024),
        GECKO_CHECKPOINT(2, 2L * 1024 * 1024);

        private final int code;
        private final long maxBytes;
        RecordKind(int code, long maxBytes) { this.code = code; this.maxBytes = maxBytes; }
        public int code() { return code; }
        public long maxBytes() { return maxBytes; }

        public static RecordKind fromCode(int code) {
            for (RecordKind kind : values()) if (kind.code == code) return kind;
            throw invalid();
        }
    }

    /** Reject an object that would exceed the record class budget before any staging. */
    public static RecordKind requireWithin(RecordKind kind, long nativeBytes) {
        if (kind == null) throw invalid();
        if (nativeBytes < 0 || nativeBytes > kind.maxBytes) throw new IllegalArgumentException("Private record exceeds class budget");
        return kind;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Unknown private record class");
    }

    private KeyPurpose() {}
}
