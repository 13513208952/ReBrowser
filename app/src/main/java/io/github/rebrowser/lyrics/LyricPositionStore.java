package io.github.rebrowser.lyrics;

/**
 * Pure, bounded lyric-overlay POSITION record with RELATIVE-ANCHOR restore. It never writes
 * SharedPreferences/files, never reads v1 positions and never treats record validation as
 * persistence or source authorization. The record carries only geometry: no path, source or
 * identity field, and every restored origin is clamped inside the current visible area.
 */
public final class LyricPositionStore {
    public static final int MAX_COORD = 32_768;
    public static final int MIN_PANEL_DP = 160, MAX_PANEL_DP = 4096;
    public enum RestoreKind { POSITION, UNKNOWN }

    public static final class Record {
        private final int x,y,displayWidthDp,displayHeightDp,panelWidthDp,panelHeightDp;private final long revision;
        public Record(int x,int y,int displayWidthDp,int displayHeightDp,int panelWidthDp,int panelHeightDp,long revision){
            if(revision<1)throw new IllegalArgumentException("Invalid position revision");
            this.x=coord(x);this.y=coord(y);
            if(displayWidthDp<1 || displayHeightDp<1 || displayWidthDp>MAX_COORD || displayHeightDp>MAX_COORD)throw new IllegalArgumentException("Invalid display bounds");
            if(panelWidthDp<MIN_PANEL_DP || panelHeightDp<MIN_PANEL_DP || panelWidthDp>MAX_PANEL_DP || panelHeightDp>MAX_PANEL_DP)throw new IllegalArgumentException("Invalid panel size");
            if(panelWidthDp>displayWidthDp || panelHeightDp>displayHeightDp)throw new IllegalArgumentException("Panel larger than display");
            this.displayWidthDp=displayWidthDp;this.displayHeightDp=displayHeightDp;this.panelWidthDp=panelWidthDp;this.panelHeightDp=panelHeightDp;this.revision=revision;
        }
        public int x(){return x;} public int y(){return y;}
        public int displayWidthDp(){return displayWidthDp;} public int displayHeightDp(){return displayHeightDp;}
        public int panelWidthDp(){return panelWidthDp;} public int panelHeightDp(){return panelHeightDp;}
        public long revision(){return revision;}
        /** Relative anchor in [0,1] (0 when the panel fully covers that axis). */
        public double horizontalAnchor(){return anchor(x,displayWidthDp-panelWidthDp);}
        public double verticalAnchor(){return anchor(y,displayHeightDp-panelHeightDp);}
        private static double anchor(int value,int range){return range<=0?0d:Math.min(1d,Math.max(0d,(double)value/(double)range));}
    }
    public static final class Restore {
        private final RestoreKind kind;private final int x,y;
        private Restore(RestoreKind kind,int x,int y){this.kind=kind;this.x=x;this.y=y;}
        public RestoreKind kind(){return kind;}
        public boolean hasPosition(){return kind==RestoreKind.POSITION;}
        public int x(){if(!hasPosition())throw new IllegalStateException("No restored position");return x;}
        public int y(){if(!hasPosition())throw new IllegalStateException("No restored position");return y;}
    }

    /** Clamp a requested origin so the whole panel stays on the own display. */
    public static Record clamp(int requestedX,int requestedY,int displayWidthDp,int displayHeightDp,int panelWidthDp,int panelHeightDp,long revision){
        if(displayWidthDp<1 || displayHeightDp<1)throw new IllegalArgumentException("Invalid display bounds");
        int maxX=Math.max(0,displayWidthDp-panelWidthDp),maxY=Math.max(0,displayHeightDp-panelHeightDp);
        int clampedX=Math.min(Math.max(0,requestedX),maxX),clampedY=Math.min(Math.max(0,requestedY),maxY);
        return new Record(clampedX,clampedY,displayWidthDp,displayHeightDp,panelWidthDp,panelHeightDp,revision);
    }

    /**
     * Restore the stored RELATIVE anchor onto the current bounds (rotation/split-screen safe).
     * A panel that now exceeds the display, a negative anchor or an extreme ratio all clamp into
     * the visible area; a missing record stays UNKNOWN rather than a saved default.
     */
    public static Restore restore(Record stored,int displayWidthDp,int displayHeightDp,int panelWidthDp,int panelHeightDp){
        if(displayWidthDp<1 || displayHeightDp<1 || displayWidthDp>MAX_COORD || displayHeightDp>MAX_COORD)
            throw new IllegalArgumentException("Invalid display bounds");
        if(panelWidthDp<1 || panelHeightDp<1 || panelWidthDp>MAX_PANEL_DP || panelHeightDp>MAX_PANEL_DP)
            throw new IllegalArgumentException("Invalid panel size");
        if(stored==null)return new Restore(RestoreKind.UNKNOWN,0,0);
        int rangeX=Math.max(0,displayWidthDp-panelWidthDp),rangeY=Math.max(0,displayHeightDp-panelHeightDp);
        int restoredX=(int)Math.round(stored.horizontalAnchor()*(double)rangeX);
        int restoredY=(int)Math.round(stored.verticalAnchor()*(double)rangeY);
        restoredX=Math.min(Math.max(0,restoredX),rangeX);
        restoredY=Math.min(Math.max(0,restoredY),rangeY);
        return new Restore(RestoreKind.POSITION,restoredX,restoredY);
    }

    /** Exact-bounds restore used when the display/panel geometry did not change at all. */
    public static Restore restoreExact(Record stored,int displayWidthDp,int displayHeightDp,int panelWidthDp,int panelHeightDp){
        Restore clamped=restore(stored,displayWidthDp,displayHeightDp,panelWidthDp,panelHeightDp);
        if(stored==null)return clamped;
        if(stored.displayWidthDp()!=displayWidthDp || stored.displayHeightDp()!=displayHeightDp
                || stored.panelWidthDp()!=panelWidthDp || stored.panelHeightDp()!=panelHeightDp)
            return new Restore(RestoreKind.UNKNOWN,0,0);
        return clamped;
    }

    /** Save INTENT only; the native preference owner decides persistence. No path/source identity. */
    public static String saveIntentLabel(Record record){
        if(record==null)throw new IllegalArgumentException("Missing position record");
        return "位置记录待原生保存（" + record.x() + "," + record.y() + "）· 未写入";
    }
    static int coord(int value){if(value< -MAX_COORD || value>MAX_COORD)throw new IllegalArgumentException("Coordinate out of range");return value;}
    private LyricPositionStore(){}
}
