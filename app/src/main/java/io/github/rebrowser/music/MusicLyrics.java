package io.github.rebrowser.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bounded pure parsing of LRC and official per-word timing text. No network, no page script and
 * no playback authorization: parsed timestamps are display data only. Unsupported input returns
 * an explicit UNSUPPORTED result instead of guessed times.
 */
public final class MusicLyrics {
    public static final int MAX_INPUT_CHARS = 1024 * 1024;
    public static final int MAX_LINE_CHARS = 4096;
    public static final int MAX_LINES = 4000;
    public static final int MAX_WORDS = 8000;
    public static final long MAX_TIME_MS = 24L * 60 * 60 * 1000;
    public enum Status { PARSED, EMPTY, UNSUPPORTED }

    public static final class Line {
        private final long timeMs; private final String text, translation;
        Line(long timeMs,String text,String translation){this.timeMs=timeMs;this.text=text;this.translation=translation;}
        public long timeMs(){return timeMs;}
        public String text(){return text;}
        public String translation(){return translation;}
    }
    public static final class Word {
        private final long startMs,endMs; private final String text;
        Word(long startMs,long endMs,String text){this.startMs=startMs;this.endMs=endMs;this.text=text;}
        public long startMs(){return startMs;}
        public long endMs(){return endMs;}
        public String text(){return text;}
    }
    public static final class Lyrics {
        private final Status status; private final List<Line> lines; private final List<Word> words;
        private final boolean overlappingWords;
        private Lyrics(Status status,List<Line> lines,List<Word> words,boolean overlapping){this.status=status;this.lines=Collections.unmodifiableList(lines);this.words=Collections.unmodifiableList(words);overlappingWords=overlapping;}
        public Status status(){return status;}
        public boolean available(){return status==Status.PARSED;}
        public List<Line> lines(){return lines;}
        public List<Word> words(){return words;}
        /** Reported per-word ranges overlap; the UI must not invent a merged timing. */
        public boolean overlappingWords(){return overlappingWords;}
        /** Latest line whose reported start has passed; -1 before the first line. */
        public int lineIndexAt(long positionMs){
            if(positionMs<0)throw new IllegalArgumentException("Negative lyric position");
            int index=-1;
            for(int i=0;i<lines.size();i++){if(lines.get(i).timeMs<=positionMs)index=i;else break;}
            return index;
        }
        /** Index of the latest word whose reported start passed, or -1; never extrapolates past it. */
        public int wordIndexAt(long positionMs){
            if(positionMs<0)throw new IllegalArgumentException("Negative lyric position");
            int index=-1;
            for(int i=0;i<words.size();i++){if(words.get(i).startMs<=positionMs)index=i;else break;}
            return index;
        }
    }

    private static final java.util.Comparator<Word> CHRONOLOGICAL_WORDS=new java.util.Comparator<Word>(){public int compare(Word a,Word b){return Long.compare(a.startMs(),b.startMs());}};
    public static Lyrics parseLrc(String text){return parse(text,Collections.<Word>emptyList());}

    /** Karaoke text lines are separate; per-word timing is parsed by {@link #parseWords}. */
    public static Lyrics parse(String lrcText,List<Word> wordTimings){
        if(wordTimings==null || wordTimings.size()>MAX_WORDS)throw new IllegalArgumentException("Invalid word timing count");
        List<Line> parsed=new ArrayList<>();
        if(lrcText!=null){
            text(lrcText,MAX_INPUT_CHARS);
            List<long[]> times=new ArrayList<>();
            List<String> bodies=new ArrayList<>();
            for(String raw:lrcText.split("\n",-1)){
                if(raw.length()>MAX_LINE_CHARS)throw new IllegalArgumentException("Lyric line too long");
                String line=raw.endsWith("\r")?raw.substring(0,raw.length()-1):raw;
                List<Long> stamps=new ArrayList<>();int cursor=0;
                while(cursor<line.length() && line.charAt(cursor)=='['){
                    int close=line.indexOf(']',cursor+1);if(close<0)break;
                    String tag=line.substring(cursor+1,close);
                    Long stamp=stamp(tag);
                    if(stamp==null)break;
                    stamps.add(stamp);cursor=close+1;
                }
                String body=line.substring(cursor).trim();
                if(stamps.isEmpty())continue; // metadata tags and non-timed text are ignored, never guessed
                for(Long stamp:stamps){times.add(new long[]{stamp});bodies.add(body);}
                if(times.size()>MAX_LINES)throw new IllegalArgumentException("Too many lyric lines");
            }
            Integer[] order=new Integer[times.size()];
            for(int i=0;i<order.length;i++)order[i]=i;
            java.util.Arrays.sort(order,new java.util.Comparator<Integer>(){public int compare(Integer a,Integer b){int c=Long.compare(times.get(a)[0],times.get(b)[0]);return c!=0?c:Integer.compare(a,b);}});
            for(Integer index:order)parsed.add(new Line(times.get(index)[0],bodies.get(index),null));
        }
        // Stable chronological order is required by wordIndexAt and overlap detection.
        List<Word> sortedWords=new ArrayList<>(wordTimings);
        for(Word word:sortedWords)
            if(word==null || word.startMs()<0 || word.endMs()<word.startMs() || word.endMs()>MAX_TIME_MS)
                throw new IllegalArgumentException("Invalid word timing");
        java.util.Collections.sort(sortedWords,CHRONOLOGICAL_WORDS); // stable for ties
        boolean overlap=false;
        long previousEnd=-1;
        for(Word word:sortedWords){
            if(word.startMs()<previousEnd)overlap=true;
            previousEnd=Math.max(previousEnd,word.endMs());
        }
        Status status=parsed.isEmpty() && sortedWords.isEmpty()?(lrcText==null || lrcText.trim().isEmpty()?Status.EMPTY:Status.UNSUPPORTED):Status.PARSED;
        return new Lyrics(status,parsed,sortedWords,overlap);
    }

    public static List<Word> parseWords(String officialWordText){ // native-normalized TSV only; NOT a raw official YRC decoder
        if(officialWordText==null)return Collections.emptyList();
        text(officialWordText,MAX_INPUT_CHARS);
        List<Word> words=new ArrayList<>();
        for(String raw:officialWordText.split("\n",-1)){
            String line=raw.endsWith("\r")?raw.substring(0,raw.length()-1):raw;
            if(line.trim().isEmpty())continue;
            if(line.length()>MAX_LINE_CHARS)throw new IllegalArgumentException("Word timing line too long");
            String[] parts=line.split("\t",-1);
            if(parts.length!=3)throw new IllegalArgumentException("Malformed word timing");
            long start=Long.parseLong(parts[0].trim()),end=Long.parseLong(parts[1].trim());
            if(start<0 || end<start || end>MAX_TIME_MS)throw new IllegalArgumentException("Invalid word timing range");
            words.add(new Word(start,end,text(parts[2],MAX_LINE_CHARS)));
            if(words.size()>MAX_WORDS)throw new IllegalArgumentException("Too many word timings");
        }
        java.util.Collections.sort(words,CHRONOLOGICAL_WORDS); // stable chronological projection
        return Collections.unmodifiableList(words);
    }

    private static Long stamp(String tag){
        int colon=tag.indexOf(':');
        if(colon<1 || colon>3)return null;
        String minutes=tag.substring(0,colon),rest=tag.substring(colon+1);
        if(!digits(minutes,1,3))return null;
        int dot=rest.indexOf('.');
        String secondsText=dot<0?rest:rest.substring(0,dot), fraction=dot<0?"":rest.substring(dot+1);
        if(!digits(secondsText,1,2) || !digits(fraction,dot<0?0:1,3))return null; // a dot REQUIRES 1..3 digits
        long minutesValue=Long.parseLong(minutes),secondsValue=Long.parseLong(secondsText);
        if(secondsValue>59)return null;
        // LRC 1/2/3 fractional digits carry 100/10/1 millisecond precision.
        long millis=dot<0?0:Long.parseLong((fraction+"000").substring(0,3));
        long total=minutesValue*60000L+secondsValue*1000L+millis;
        if(total>MAX_TIME_MS)throw new IllegalArgumentException("Lyric time out of range");
        return total;
    }
    private static boolean digits(String value,int minimum,int maximum){if(value.length()<minimum || value.length()>maximum)return false;for(int i=0;i<value.length();i++)if(value.charAt(i)<'0' || value.charAt(i)>'9')return false;return true;}
    static String text(String value,int max){
        if(value==null || value.length()>max)throw new IllegalArgumentException("Unbounded lyric text");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Broken UTF16");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Broken UTF16");}
        return value;
    }
}
