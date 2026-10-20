package io.github.rebrowser.bootstrap;

/** Process classification is local startup routing, never administrator identity. */
public enum ProcessRole {
    MAIN, GECKO_CHILD, AUXILIARY, UNKNOWN;
    public enum StartupRoute { MAIN_NATIVE_OWNER, SDK_CHILD_NO_APP_SERVICES, NONPRIMARY_NO_APP_SERVICES, UNAVAILABLE_FAIL_CLOSED }
    public StartupRoute startupRoute(){return this==MAIN?StartupRoute.MAIN_NATIVE_OWNER:this==GECKO_CHILD?StartupRoute.SDK_CHILD_NO_APP_SERVICES:this==AUXILIARY?StartupRoute.NONPRIMARY_NO_APP_SERVICES:StartupRoute.UNAVAILABLE_FAIL_CLOSED;}
    /** Actual own package routing only; never use this as an administrator caller-package check. */
    public static ProcessRole classifyOwnApplication(String packageName,String processName){
        if(!"io.github.rebrowser".equals(packageName) && !"io.github.rebrowser.development".equals(packageName))return UNKNOWN;
        return classify(packageName,processName);
    }
    public static ProcessRole classify(String packageName, String processName) {
        if (!validName(packageName,255,false) || !validName(processName,512,true)) return UNKNOWN;
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
    private static boolean validName(String name,int limit,boolean process){
        if(name==null || name.isEmpty() || name.length()>limit)return false;
        int colons=0;for(int i=0;i<name.length();i++){char c=name.charAt(i);if(c==':' && process){if(++colons>1 || i==0 || i==name.length()-1)return false;}else if(!(c>='a' && c<='z' || c>='A' && c<='Z' || c>='0' && c<='9' || c=='_' || c=='.'))return false;}return true;
    }
    private static boolean numbered(String value, String prefix) {
        if (!value.startsWith(prefix) || value.length() == prefix.length()) return false;
        for (int i = prefix.length(); i < value.length(); i++) if (value.charAt(i) < '0' || value.charAt(i) > '9') return false;
        return true;
    }
}
