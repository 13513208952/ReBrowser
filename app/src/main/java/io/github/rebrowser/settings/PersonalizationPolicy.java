package io.github.rebrowser.settings;

/**
 * Pure presentation suggestions for the APPROVED built-in look: center-crop geometry, bounded
 * scale and text/background contrast choice. It neither parses nor opens a URI, obtains a
 * persistable grant or claims that a background was saved/authorized. Unknown external artwork
 * is reported UNAVAILABLE until the native owner supplies authorized content.
 */
public final class PersonalizationPolicy {
    public static final int MAX_VIEWPORT_DP = 4096;
    public static final long MAX_SOURCE_PIXELS = 8192L * 8192L;
    public enum Fit { CENTER_CROP, CONTAIN }
    public enum TextTone { LIGHT, DARK }

    public static final class Crop {
        private final int sourceWidth,sourceHeight,cropX,cropY,cropWidth,cropHeight,outputWidth,outputHeight;
        private Crop(int sw,int sh,int x,int y,int cw,int ch,int ow,int oh){sourceWidth=sw;sourceHeight=sh;cropX=x;cropY=y;cropWidth=cw;cropHeight=ch;outputWidth=ow;outputHeight=oh;}
        public int sourceWidth(){return sourceWidth;}
        public int sourceHeight(){return sourceHeight;}
        public int cropX(){return cropX;}
        public int cropY(){return cropY;}
        public int cropWidth(){return cropWidth;}
        public int cropHeight(){return cropHeight;}
        public int outputWidth(){return outputWidth;}
        public int outputHeight(){return outputHeight;}
    }

    /** Center crop of a bounded decoded image onto the own viewport; never upscales beyond source. */
    public static Crop centerCrop(int sourceWidth,int sourceHeight,int viewportWidthDp,int viewportHeightDp,float density){
        if(sourceWidth<1 || sourceHeight<1 || (long)sourceWidth*sourceHeight>MAX_SOURCE_PIXELS)throw new IllegalArgumentException("Invalid background source size");
        if(viewportWidthDp<1 || viewportHeightDp<1 || viewportWidthDp>MAX_VIEWPORT_DP || viewportHeightDp>MAX_VIEWPORT_DP)throw new IllegalArgumentException("Invalid viewport");
        if(!Float.isFinite(density) || density<=0 || density>16)throw new IllegalArgumentException("Invalid density");
        int targetW=Math.max(1,Math.round(viewportWidthDp*density)),targetH=Math.max(1,Math.round(viewportHeightDp*density));
        long byHeight=(long)targetW*sourceHeight/targetH;
        int cropW,cropH;
        if(byHeight<=sourceWidth){cropH=sourceHeight;cropW=(int)Math.max(1,byHeight);}
        else{cropW=sourceWidth;cropH=(int)Math.max(1,(long)targetH*sourceWidth/targetW);}
        cropW=Math.min(cropW,sourceWidth);cropH=Math.min(cropH,sourceHeight);
        double scale=Math.min(1d,Math.min((double)targetW/cropW,(double)targetH/cropH));
        int outputW=Math.min(cropW,Math.max(1,(int)Math.round(cropW*scale))),outputH=Math.min(cropH,Math.max(1,(int)Math.round(cropH*scale)));
        if((long)outputW*outputH>MAX_SOURCE_PIXELS)throw new IllegalArgumentException("Output pixels exceed budget");
        return new Crop(sourceWidth,sourceHeight,(sourceWidth-cropW)/2,(sourceHeight-cropH)/2,cropW,cropH,outputW,outputH);
    }

    /** WCAG-style relative luminance of an opaque sRGB color; no image is read. */
    public static double relativeLuminance(int argb){
        if((argb>>>24)!=255)throw new IllegalArgumentException("Background alpha needs explicit native compositing");
        double r=channel((argb>>>16)&0xff),g=channel((argb>>>8)&0xff),b=channel(argb&0xff);
        return 0.2126*r+0.7152*g+0.0722*b;
    }
    private static double channel(int value){double s=value/255.0;return s<=0.03928?s/12.92:Math.pow((s+0.055)/1.055,2.4);}

    /** Contrast ratio in [1,21]; used only to pick a readable built-in text tone. */
    public static double contrastRatio(int first,int second){
        double a=relativeLuminance(first),b=relativeLuminance(second);
        double lighter=Math.max(a,b),darker=Math.min(a,b);
        return (lighter+0.05)/(darker+0.05);
    }

    /** Choose the better of the two approved tones against the observed background. */
    public static TextTone textToneFor(int backgroundArgb){
        double light=contrastRatio(backgroundArgb,0xffffffff),dark=contrastRatio(backgroundArgb,0xff000000);
        return light>=dark?TextTone.LIGHT:TextTone.DARK;
    }
    public static int textColor(TextTone tone){if(tone==null)throw new IllegalArgumentException("Missing text tone");return tone==TextTone.LIGHT?0xffffffff:0xff000000;}

    /** External artwork is UNAVAILABLE unless the native owner already authorized concrete content. */
    public static String externalArtworkHint(boolean nativeAuthorizedContentProvided){
        return nativeAuthorizedContentProvided?"已提供原生授权内容":"外部背景不可用（未获得原生授权资源）";
    }
    private PersonalizationPolicy(){}
}
