package io.github.rebrowser.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded pure parsing of LRC and official per-word timing text, exact-time translation
 * alignment and binary-search line/word lookup. No network, no page script and no playback
 * authorization: parsed timestamps are display data only. Unsupported input returns an explicit
 * result instead of guessed times; translations are attached only on an EXACT time match and
 * ambiguous duplicates stay untranslated rather than approximating.
 */
public final class MusicLyrics {
    public static final int MAX_INPUT_CHARS = 1024 * 1024;
    public static final int MAX_LINE_CHARS = 4096;
    public static final int MAX_LINES = 4000;
    public static final int MAX_WORDS = 8000;
    public static final int MAX_AMBIGUOUS_TIMES = 256;
    // Avoid LambdaMetafactory on the Android bootclasspath-only compile path.
    private static final java.util.Comparator<Word> WORD_ORDER = new java.util.Comparator<Word>(){
        @Override public int compare(Word a,Word b){return Long.compare(a.startMs(),b.startMs());}
    };
    public static final long MAX_TIME_MS = 24L * 60 * 60 * 1000;
    public enum Status { PARSED, EMPTY, UNSUPPORTED }

    public static final class Line {
        private final long timeMs; private final String text, translation;
        Line(long timeMs,String text,String translation){this.timeMs=timeMs;this.text=text;this.translation=translation;}
        public long timeMs(){return timeMs;}
        public String text(){return text;}
        /** Only an exactly aligned approved translation; null = none/ambiguous. */
        public String translation(){return translation;}
        Line withTranslation(String translated){return new Line(timeMs,text,translated);}
    }
    public static final class Word {
        private final long startMs,endMs; private final String text;
        Word(long startMs,long endMs,String text){this.startMs=startMs;this.endMs=endMs;this.text=text;}
        public long startMs(){return startMs;}
        public long endMs(){return endMs;}
        public String text(){return text;}
    }
    /** Explicit native-normalized character mapping; validated against exact line/word text.
     * Parsed timing alone NEVER supplies character offsets or picks a repeated-word occurrence. */
    public static final class WordSpan {
        private final int wordIndex,lineIndex,start,end;
        public WordSpan(int wordIndex,int lineIndex,int start,int end){if(wordIndex<0 || lineIndex<0 || start<0 || end<=start)throw new IllegalArgumentException("Invalid lyric span");this.wordIndex=wordIndex;this.lineIndex=lineIndex;this.start=start;this.end=end;}
        public int wordIndex(){return wordIndex;}public int lineIndex(){return lineIndex;}public int start(){return start;}public int end(){return end;}
    }
    public static final class Lyrics {
        private final List<WordSpan> spans;
        private final Status status; private final List<Line> lines; private final List<Word> words;
        private final boolean overlappingWords;private final List<Long> ambiguousTimes;
        private Lyrics(Status status,List<Line> lines,List<Word> words,boolean overlapping,List<Long> ambiguousTimes){this(status,lines,words,overlapping,ambiguousTimes,Collections.<WordSpan>emptyList());}
        private Lyrics(Status status,List<Line> lines,List<Word> words,boolean overlapping,List<Long> ambiguousTimes,List<WordSpan> spans){
            this.spans=Collections.unmodifiableList(new ArrayList<>(spans));
            this.status=status;this.lines=Collections.unmodifiableList(lines);this.words=Collections.unmodifiableList(words);
            this.overlappingWords=overlapping;this.ambiguousTimes=Collections.unmodifiableList(ambiguousTimes);
        }
        public Status status(){return status;}
        public boolean available(){return status==Status.PARSED;}
        public List<Line> lines(){return lines;}
        public List<Word> words(){return words;}
        /** Reported per-word ranges overlap; the UI must not invent a merged timing. */
        public boolean overlappingWords(){return overlappingWords;}
        /** Exact times whose translation was ambiguous or had no unique original line. */
        public List<Long> ambiguousTranslationTimes(){return ambiguousTimes;}
        /** Latest line whose reported start has passed; stable for equal timestamps (last tie). */
        public int lineIndexAt(long positionMs){
            if(positionMs<0)throw new IllegalArgumentException("Negative lyric position");
            int low=0,high=lines.size()-1,result=-1;
            while(low<=high){
                int mid=(low+high)>>>1;
                if(lines.get(mid).timeMs()<=positionMs){result=mid;low=mid+1;}else high=mid-1;
            }
            return result;
        }
        /** Latest word whose reported start passed (legacy semantic); never extrapolates. */
        public int wordIndexAt(long positionMs){
            if(positionMs<0)throw new IllegalArgumentException("Negative lyric position");
            int low=0,high=words.size()-1,result=-1;
            while(low<=high){
                int mid=(low+high)>>>1;
                if(words.get(mid).startMs<=positionMs){result=mid;low=mid+1;}else high=mid-1;
            }
            return result;
        }
        /** Strict [start,end) active word; gaps and positions past the last end return -1. */
        public int activeWordAt(long positionMs){
            if(positionMs<0)throw new IllegalArgumentException("Negative lyric position");
            int index=wordIndexAt(positionMs);
            if(index<0)return -1;
            Word word=words.get(index);
            if(!overlappingWords)return positionMs>=word.startMs && positionMs<word.endMs?index:-1;
            int unique=-1;for(int i=0;i<=index;i++)if(positionMs>=words.get(i).startMs && positionMs<words.get(i).endMs){if(unique>=0)return -1;unique=i;}return unique;
        }
        public WordSpan activeSpanAt(long positionMs){int word=activeWordAt(positionMs),line=lineIndexAt(positionMs);if(word<0 || line<0)return null;for(WordSpan span:spans)if(span.wordIndex==word && span.lineIndex==line)return span;return null;}
        public Lyrics withNativeSpans(List<WordSpan> nativeSpans){
            if(nativeSpans==null || nativeSpans.size()>MAX_WORDS)throw new IllegalArgumentException("Invalid lyric mappings");java.util.Set<Integer> seen=new java.util.HashSet<>();
            for(WordSpan span:nativeSpans){if(span==null || span.wordIndex>=words.size() || span.lineIndex>=lines.size() || !seen.add(span.wordIndex))throw new IllegalArgumentException("Unknown/duplicate lyric mapping");String line=lines.get(span.lineIndex).text();if(span.end>line.length() || span.start>line.length() || span.start>0 && Character.isLowSurrogate(line.charAt(span.start)) || span.end<line.length() && Character.isLowSurrogate(line.charAt(span.end)) || !line.substring(span.start,span.end).equals(words.get(span.wordIndex).text()))throw new IllegalArgumentException("Lyric mapping does not match exact text");}
            return new Lyrics(status,lines,words,overlappingWords,ambiguousTimes,nativeSpans);
        }
        public Line lineAt(long positionMs){
            int index=lineIndexAt(positionMs);
            return index<0?null:lines.get(index);
        }
        Lyrics withLines(List<Line> replaced,List<Long> ambiguous){
            return new Lyrics(status,replaced,words,overlappingWords,ambiguous,spans);
        }
    }

    public static Lyrics parseLrc(String text){return parse(text,Collections.<Word>emptyList());}

    /** Karaoke text lines are separate; per-word timing is parsed by {@link #parseWords}. */
    public static Lyrics parse(String lrcText,List<Word> wordTimings){
        if(wordTimings==null || wordTimings.size()>MAX_WORDS)throw new IllegalArgumentException("Invalid word timing count");
        List<Line> parsed=new ArrayList<>();
        if(lrcText!=null){
            text(lrcText,MAX_INPUT_CHARS);
            List<long[]> times=new ArrayList<>();
            List<String> bodies=new ArrayList<>();
            int rawLines=0;
            for(int offset=0;offset<=lrcText.length();){
                if(++rawLines>MAX_LINES*2)throw new IllegalArgumentException("Too many source lyric lines");
                int end=lrcText.indexOf('\n',offset);if(end<0)end=lrcText.length();String raw=lrcText.substring(offset,end);offset=end+1;
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
            java.util.Arrays.sort(order,new java.util.Comparator<Integer>(){
                @Override public int compare(Integer a,Integer b){int c=Long.compare(times.get(a)[0],times.get(b)[0]);return c!=0?c:Integer.compare(a,b);}
            });
            for(Integer index:order)parsed.add(new Line(times.get(index)[0],bodies.get(index),null));
        }
        List<Word> sortedWords=new ArrayList<>(wordTimings);
        for(Word word:sortedWords)
            if(word==null || word.startMs()<0 || word.endMs()<word.startMs() || word.endMs()>MAX_TIME_MS)
                throw new IllegalArgumentException("Invalid word timing");
        Collections.sort(sortedWords,WORD_ORDER); // stable for ties
        boolean overlap=false;
        long previousEnd=-1;
        for(Word word:sortedWords){
            if(word.startMs()<previousEnd)overlap=true;
            previousEnd=Math.max(previousEnd,word.endMs());
        }
        Status status=parsed.isEmpty() && sortedWords.isEmpty()
                ?(lrcText==null || lrcText.trim().isEmpty()?Status.EMPTY:Status.UNSUPPORTED):Status.PARSED;
        return new Lyrics(status,parsed,sortedWords,overlap,Collections.<Long>emptyList());
    }

    /**
     * Align a translation LRC to the original by EXACT timestamp. A time needs exactly one
     * original line and exactly one translation line; everything else stays untranslated and is
     * reported as ambiguous instead of being approximated.
     */
    public static Lyrics alignTranslations(Lyrics original,Lyrics translation){
        if(original==null || translation==null)throw new IllegalArgumentException("Missing lyric set");
        Map<Long,List<String>> originalByTime=new LinkedHashMap<>();
        for(Line line:original.lines())addAtTime(originalByTime,line);
        Map<Long,List<String>> translatedByTime=new LinkedHashMap<>();
        for(Line line:translation.lines())addAtTime(translatedByTime,line);
        List<Line> aligned=new ArrayList<>();
        List<Long> ambiguous=new ArrayList<>();
        for(Line line:original.lines()){
            List<String> originals=originalByTime.get(line.timeMs());
            List<String> translations=translatedByTime.get(line.timeMs());
            boolean unique=originals!=null && originals.size()==1 && translations!=null && translations.size()==1;
            if(unique)aligned.add(line.withTranslation(translations.get(0)));
            else{
                aligned.add(line.withTranslation(null));
                if((originals.size()>1 || translations!=null && translations.size()>1) && ambiguous.size()<MAX_AMBIGUOUS_TIMES && !ambiguous.contains(line.timeMs()))ambiguous.add(line.timeMs());
            }
        }
        return original.withLines(aligned,ambiguous);
    }

    private static void addAtTime(Map<Long,List<String>> map,Line line){List<String> values=map.get(line.timeMs());if(values==null){values=new ArrayList<>();map.put(line.timeMs(),values);}values.add(line.text());}
    /** Native-normalized TSV only, NOT raw official YRC. */
    public static List<Word> parseWords(String officialWordText){
        if(officialWordText==null)return Collections.emptyList();
        text(officialWordText,MAX_INPUT_CHARS);
        List<Word> words=new ArrayList<>();
        int rawLines=0;
        for(int offset=0;offset<=officialWordText.length();){
            if(++rawLines>MAX_WORDS*2)throw new IllegalArgumentException("Too many source word lines");
            int endOfLine=officialWordText.indexOf('\n',offset);if(endOfLine<0)endOfLine=officialWordText.length();String raw=officialWordText.substring(offset,endOfLine);offset=endOfLine+1;
            String line=raw.endsWith("\r")?raw.substring(0,raw.length()-1):raw;
            if(line.length()>MAX_LINE_CHARS)throw new IllegalArgumentException("Word timing line too long");
            if(line.trim().isEmpty())continue;
            String[] parts=line.split("\t",-1);
            if(parts.length!=3)throw new IllegalArgumentException("Malformed word timing");
            long start=Long.parseLong(parts[0].trim()),end=Long.parseLong(parts[1].trim());
            if(start<0 || end<start || end>MAX_TIME_MS)throw new IllegalArgumentException("Invalid word timing range");
            words.add(new Word(start,end,text(parts[2],MAX_LINE_CHARS)));
            if(words.size()>MAX_WORDS)throw new IllegalArgumentException("Too many word timings");
        }
        Collections.sort(words,WORD_ORDER); // stable chronological projection
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
