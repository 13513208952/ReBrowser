package io.github.reoutlook;

/** Packaged brand graphics, shared with the same four actual search-engine preference values. */
final class ReBrowserSearchEngineAssets {
    private ReBrowserSearchEngineAssets() {}

    static int icon(String engine) {
        switch (engine) {
            case ReBrowserPreferences.SEARCH_BAIDU: return R.drawable.rb_brand_baidu_icon;
            case ReBrowserPreferences.SEARCH_DUCKDUCKGO: return R.drawable.rb_brand_duckduckgo_icon;
            case ReBrowserPreferences.SEARCH_GOOGLE: return R.drawable.rb_brand_google_icon;
            default: return R.drawable.rb_brand_bing_icon;
        }
    }

    static int logo(String engine, boolean photoBackground) {
        switch (engine) {
            case ReBrowserPreferences.SEARCH_BAIDU: return R.drawable.rb_brand_baidu_logo;
            case ReBrowserPreferences.SEARCH_DUCKDUCKGO: return R.drawable.rb_brand_duckduckgo_logo;
            case ReBrowserPreferences.SEARCH_GOOGLE:
                return photoBackground ? R.drawable.rb_brand_google_logo_light : R.drawable.rb_brand_google_logo;
            default: return R.drawable.rb_brand_bing_logo;
        }
    }
}
