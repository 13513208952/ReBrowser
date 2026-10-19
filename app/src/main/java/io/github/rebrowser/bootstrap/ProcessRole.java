package io.github.rebrowser.bootstrap;

/** Process classification is local startup routing, never administrator identity. */
public enum ProcessRole {
    MAIN, GECKO_CHILD, AUXILIARY, UNKNOWN;
    public static ProcessRole classify(String packageName, String processName) {
        if (packageName == null || packageName.isEmpty() || processName == null || processName.isEmpty()) return UNKNOWN;
        if (packageName.equals(processName)) return MAIN;
        if (!processName.startsWith(packageName + ":")) return AUXILIARY;
        String suffix = processName.substring(packageName.length() + 1);
        if (suffix.equals("media") || suffix.equals("crashhelper_disable_art_image_")
                || suffix.equals("gmplugin_disable_art_image_") || suffix.equals("socket_disable_art_image_")
                || suffix.equals("gpu_disable_art_image_") || suffix.equals("rdd_disable_art_image_")
                || suffix.equals("utility_disable_art_image_") || suffix.equals("ipdlunittest_disable_art_image_")) return GECKO_CHILD;
        if (numbered(suffix, "tab_disable_art_image_") || numbered(suffix, "isolatedTab_disable_art_image_")) return GECKO_CHILD;
        return AUXILIARY;
    }
    private static boolean numbered(String value, String prefix) {
        if (!value.startsWith(prefix) || value.length() == prefix.length()) return false;
        for (int i = prefix.length(); i < value.length(); i++) if (value.charAt(i) < '0' || value.charAt(i) > '9') return false;
        return true;
    }
}
