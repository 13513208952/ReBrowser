package io.github.rebrowser.music;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import io.github.rebrowser.ui.BrowserToolbar;
import java.util.List;
import java.util.Set;
import java.util.EnumSet;
import java.util.Collections;

/**
 * Native music WINDOW SHELL over the OFFICIAL player projection. Queue rows, lyrics and transport
 * controls are READ-ONLY observations; every action is an INTENT carrying the original queue
 * token+revision. Seeks are bounded by an explicit offered action and the duration captured at
 * gesture start. Glyph highlighting requires BOTH a unique active [start,end) word AND explicit
 * exact native character mapping; unmapped timing is labelled separately, never guessed from
 * repeated text. A gap never keeps a prior word lit. No player/service/URL is created here.
 */
public final class MusicActivity extends Activity {
    public interface Actions {
        void requestTransport(MusicMediaSession.Action exactAction,long expectedIntentGeneration);
        void seekTo(long positionMs,long expectedIntentGeneration);
        void openQueue();
        void openLyrics();
        void playTrack(String trackToken,long expectedQueueRevision);
        void removeTrack(String trackToken,long expectedQueueRevision);
        void toggleLike(String trackToken,long expectedQueueRevision);
    }
    public static final int MAX_QUEUE_ROWS = 12;

    /** Pure display arithmetic used by the window; no player state is changed. */
    public static final class Model {
        public static int progressPermille(long positionMs,long durationMs){
            if(positionMs< -1 || positionMs>MusicState.MAX_POSITION_MS || durationMs< -1 || durationMs>MusicState.MAX_POSITION_MS)throw new IllegalArgumentException("Invalid music progress range");
            if(durationMs<=0 || positionMs<0)return 0;
            long value=positionMs*1000L/durationMs;
            return (int)Math.max(0,Math.min(1000,value));
        }
        public static long seekTarget(long durationMs,int permille){
            if(durationMs<=0 || durationMs>MusicState.MAX_POSITION_MS || permille<0 || permille>1000)throw new IllegalArgumentException("Invalid seek target");
            return durationMs*permille/1000L;
        }
        /** Old display-only entry cannot infer an offered SEEK from duration or playback. */
        public static boolean seekEnabled(MusicState observed){return false;}
        public static boolean seekEnabled(MusicState observed,boolean explicitlyOffered){
            return explicitlyOffered && observed!=null && observed.hasReportedDuration() && observed.durationMs()>0;
        }
        /** Strict [start,end) word label; a gap must not keep the previous word highlighted. */
        public static String lyricText(MusicLyrics.Lyrics lyrics,long positionMs){
            if(lyrics==null || !lyrics.available())return "歌词未读取";
            MusicLyrics.Line line=lyrics.lineAt(positionMs);
            if(line==null)return "";
            StringBuilder text=new StringBuilder(line.text());
            String translation=line.translation();
            int active=lyrics.activeWordAt(positionMs);
            if(active>=0 && lyrics.activeSpanAt(positionMs)==null){
                MusicLyrics.Word word=lyrics.words().get(active);
                text.append("\n当前时间词（未映射原行）：").append(word.text());
            }
            if(translation!=null && !translation.isEmpty())text.append("\n").append(translation);
            return text.toString();
        }
    }

    private TextView title,artist,state,lyrics;
    private Button previous,toggle,next;
    private TextView queueLabel;
    private SeekBar progress;
    private EditText queueFilter;
    private LinearLayout queue;
    private BrowserToolbar.Binding binding;
    private Actions actions;
    private MusicState observed;
    private Set<MusicMediaSession.Action> offered=Collections.emptySet();
    private Object interactionFrame;
    private Object seekFrameAtStart;
    private Object queueFrame;
    private MusicQueue queuePage=MusicQueue.unread();
    private MusicLyrics.Lyrics lyricsModel;
    private long durationMs=-1;
    private int seekStartPermille=-1;
    private long seekDurationAtStart=-1;
    private long seekGenerationAtStart=-1;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        queue=new LinearLayout(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        title=new TextView(this);title.setTextSize(20);
        artist=new TextView(this);artist.setTextSize(14);
        state=new TextView(this);
        root.addView(title);root.addView(artist);root.addView(state);
        progress=new SeekBar(this);
        progress.setMax(1000);
        progress.setEnabled(false);
        root.addView(progress,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        LinearLayout transport=new LinearLayout(this);
        transport.setOrientation(LinearLayout.HORIZONTAL);
        previous=new Button(this);previous.setText("上一首");previous.setMinimumHeight(dp(48));
        toggle=new Button(this);toggle.setText("播放/暂停");toggle.setMinimumHeight(dp(48));
        next=new Button(this);next.setText("下一首");next.setMinimumHeight(dp(48));
        transport.addView(previous,new LinearLayout.LayoutParams(0,dp(48),1));
        transport.addView(toggle,new LinearLayout.LayoutParams(0,dp(48),1));
        transport.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(transport);
        lyrics=new TextView(this);lyrics.setTextSize(15);
        root.addView(lyrics);
        queueLabel=new TextView(this);
        root.addView(queueLabel);
        queueFilter=new EditText(this);queueFilter.setSaveEnabled(false);queueFilter.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);queueFilter.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(MusicQueue.MAX_FILTER_CHARS)});
        queueFilter.setSingleLine(true);
        queueFilter.setHint("筛选本页队列（标题/歌手/专辑）");
        root.addView(queueFilter,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
        queue.setOrientation(LinearLayout.VERTICAL);
        root.addView(queue,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1));
        Button queueButton=new Button(this);queueButton.setText("队列");queueButton.setMinimumHeight(dp(48));
        Button lyricsButton=new Button(this);lyricsButton.setText("歌词");lyricsButton.setMinimumHeight(dp(48));
        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.addView(queueButton,new LinearLayout.LayoutParams(0,dp(48),1));
        bottom.addView(lyricsButton,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(bottom);
        setContentView(root);
        previous.setEnabled(false);next.setEnabled(false);toggle.setEnabled(false);
        queueButton.setOnClickListener(v->intent(Actions::openQueue));
        lyricsButton.setOnClickListener(v->intent(Actions::openLyrics));
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean fromUser){}
            public void onStartTrackingTouch(SeekBar bar){
                seekStartPermille=bar.getProgress();
                seekDurationAtStart=durationMs;
                seekGenerationAtStart=observed==null?-1:observed.intentGeneration();
                seekFrameAtStart=interactionFrame;
            }
            public void onStopTrackingTouch(SeekBar bar){
                Object expected=seekFrameAtStart;
                long startDuration=seekDurationAtStart,startGeneration=seekGenerationAtStart;
                boolean started=seekStartPermille>=0;
                seekStartPermille=-1;seekDurationAtStart=-1;seekGenerationAtStart=-1;seekFrameAtStart=null;
                if(!started || startDuration<=0 || binding==null || observed==null
                        || expected==null || expected!=interactionFrame || !offered.contains(MusicMediaSession.Action.SEEK)
                        || observed.intentGeneration()!=startGeneration)return;
                long target=Model.seekTarget(startDuration,bar.getProgress());
                intent(actions->actions.seekTo(target,startGeneration));
            }
        });
        queueFilter.setOnEditorActionListener((view,action,event)->{
            renderQueue();
            return true;
        });
        showState(null,MusicQueue.unread(),null);
    }

    /** Compatibility entry: no native offers, therefore NO transport or seek controls enabled. */
    public void bind(BrowserToolbar.Binding binding,MusicState observed,MusicQueue queuePage,MusicLyrics.Lyrics lyricsModel,Actions actions){
        bind(binding,observed,queuePage,lyricsModel,Collections.<MusicMediaSession.Action>emptySet(),actions);
    }
    public void bind(BrowserToolbar.Binding binding,MusicState observed,MusicQueue queuePage,
                     MusicLyrics.Lyrics lyricsModel,Set<MusicMediaSession.Action> nativeOffered,Actions actions){
        ownerThread();
        if(binding==null || observed==null || queuePage==null || nativeOffered==null || actions==null)
            throw new IllegalArgumentException("Missing native music binding or actions");
        binding.check();
        EnumSet<MusicMediaSession.Action> copy=EnumSet.noneOf(MusicMediaSession.Action.class);
        copy.addAll(nativeOffered);
        offered=Collections.unmodifiableSet(copy);queueFilter.setText("");
        this.binding=binding;this.actions=actions;interactionFrame=new Object();
        seekFrameAtStart=null;queueFrame=null;seekStartPermille=-1;
        showState(observed,queuePage,lyricsModel);
    }

    public void unbind(){
        ownerThread();
        binding=null;actions=null;observed=null;lyricsModel=null;interactionFrame=null;seekFrameAtStart=null;queueFrame=null;
        offered=Collections.emptySet();if(queueFilter!=null)queueFilter.setText("");seekStartPermille=-1;seekDurationAtStart=-1;seekGenerationAtStart=-1;
        showState(null,MusicQueue.unread(),null);
    }

    private void showState(MusicState next,MusicQueue page,MusicLyrics.Lyrics lyricSet){
        observed=next;queuePage=page;lyricsModel=lyricSet;
        title.setText(next==null?"音乐":(next.title().isEmpty()?"音乐":next.title()));
        artist.setText(next==null?"":next.artist());
        state.setText(next==null?"未连接官方播放器":next.playbackLabel()+" · "+next.accountLabel());
        durationMs=next!=null && next.hasReportedDuration()?next.durationMs():-1;
        progress.setEnabled(Model.seekEnabled(next,offered.contains(MusicMediaSession.Action.SEEK)));
        previous.setEnabled(next!=null && offered.contains(MusicMediaSession.Action.SKIP_PREVIOUS));
        nextButtonState(next);
        final Object expectedFrame=interactionFrame;final MusicState exactObservation=next;
        previous.setOnClickListener(next==null?null:v->transport(expectedFrame,exactObservation,MusicMediaSession.Action.SKIP_PREVIOUS));
        this.next.setOnClickListener(next==null?null:v->transport(expectedFrame,exactObservation,MusicMediaSession.Action.SKIP_NEXT));
        MusicMediaSession.Action exactToggle=offered.contains(MusicMediaSession.Action.PAUSE) && next!=null && next.playback()==MusicState.Playback.PLAYING?MusicMediaSession.Action.PAUSE:offered.contains(MusicMediaSession.Action.PLAY)?MusicMediaSession.Action.PLAY:MusicMediaSession.Action.PAUSE;
        toggle.setOnClickListener(next==null?null:v->transport(expectedFrame,exactObservation,exactToggle));
        long shown=0;
        if(next!=null){
            MusicState.Estimate estimate=next.estimatePosition(next.intentGeneration(),android.os.SystemClock.elapsedRealtime());
            shown=estimate.usable()?estimate.positionMs():next.positionMs();
        }
        progress.setProgress(Model.progressPermille(shown,durationMs));
        String lyricText=next==null?"歌词未读取":Model.lyricText(lyricSet,shown);
        MusicLyrics.WordSpan span=lyricSet==null || next==null?null:lyricSet.activeSpanAt(shown);
        if(span==null)lyrics.setText(lyricText);
        else{android.text.SpannableString highlighted=new android.text.SpannableString(lyricText);highlighted.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),span.start(),span.end(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);lyrics.setText(highlighted);}
        renderQueue();
    }

    private void nextButtonState(MusicState current){
        next.setEnabled(current!=null && offered.contains(MusicMediaSession.Action.SKIP_NEXT));
        toggle.setEnabled(current!=null && (offered.contains(MusicMediaSession.Action.PLAY)
                || offered.contains(MusicMediaSession.Action.PAUSE)));
    }
    private void renderQueue(){
        queueFrame=new Object();
        BrowserToolbar.clearOwnedViewTree(queue);queue.removeAllViews();
        if(queuePage.state()==MusicQueue.ViewState.UNREAD){
            queueLabel.setText("队列未读取");
            return;
        }
        String filter=queueFilter==null || queueFilter.getText()==null?"":queueFilter.getText().toString();
        MusicQueue.Window window;
        try{window=queuePage.window(0,MAX_QUEUE_ROWS,filter);}
        catch(IllegalArgumentException|IllegalStateException invalid){queueLabel.setText("筛选条件无效或队列不可用");return;}
        queueLabel.setText("队列 "+window.tracks().size()+" 项"+(window.hasMore()?"（有更多）":"")+(filter.trim().isEmpty()?"":" · 本页筛选"));
        List<MusicQueue.Track> tracks=window.tracks();
        final Object expectedQueueFrame=queueFrame;
        for(int i=0;i<tracks.size();i++){
            MusicQueue.Track track=tracks.get(i);
            final int position=i;
            LinearLayout line=new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            Button play=new Button(this);
            play.setText((window.originalStart()+i+1)+". "+track.title()+" — "+track.artist());
            play.setMinimumHeight(dp(48));
            play.setContentDescription("播放队列项：" + track.title());
            play.setEnabled(track.offers(MusicQueue.Action.PLAY_ENTRY));play.setOnClickListener(track.offers(MusicQueue.Action.PLAY_ENTRY)?v->queueIntent(expectedQueueFrame,intent->intent.playTrack(track.token(),window.revision())):null);
            line.addView(play,new LinearLayout.LayoutParams(0,dp(48),1));
            Button like=new Button(this);
            like.setText(track.liked()==MusicQueue.Like.REPORTED_LIKED?"取消喜欢":"喜欢");
            like.setMinimumHeight(dp(48));
            like.setContentDescription((track.liked()==MusicQueue.Like.REPORTED_LIKED?"请求取消喜欢：":"请求喜欢：")+track.title());
            like.setEnabled(track.offers(MusicQueue.Action.TOGGLE_LIKE));like.setOnClickListener(track.offers(MusicQueue.Action.TOGGLE_LIKE)?v->queueIntent(expectedQueueFrame,intent->intent.toggleLike(track.token(),window.revision())):null);
            line.addView(like,new LinearLayout.LayoutParams(dp(88),dp(48)));
            Button remove=new Button(this);
            remove.setText("移除");
            remove.setMinimumHeight(dp(48));
            remove.setContentDescription("请求从队列移除：" + track.title());
            remove.setEnabled(track.offers(MusicQueue.Action.REMOVE_FROM_QUEUE));remove.setOnClickListener(track.offers(MusicQueue.Action.REMOVE_FROM_QUEUE)?v->queueIntent(expectedQueueFrame,intent->intent.removeTrack(track.token(),window.revision())):null);
            line.addView(remove,new LinearLayout.LayoutParams(dp(72),dp(48)));
            queue.addView(line);
        }
        if(tracks.isEmpty()){
            TextView empty=new TextView(this);
            empty.setText("当前筛选没有匹配的队列项");
            queue.addView(empty);
        }
    }

    private interface QueueIntent { void run(Actions actions); }
    private void queueIntent(Object expectedFrame,QueueIntent intent){
        BrowserToolbar.Binding captured=binding;Actions callback=actions;
        if(captured==null || callback==null || expectedFrame!=queueFrame)return;
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{
            captured.check();
            if(captured!=binding || callback!=actions || expectedFrame!=queueFrame)return;
            intent.run(callback);
        });
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && expectedFrame==queueFrame && binding==captured && actions==callback)state.setText("操作结果未知，请核对官方播放器状态");
    }

    @Override protected void onDestroy(){
        unbind();BrowserToolbar.clearOwnedViewTree(findViewById(android.R.id.content));
        super.onDestroy();
    }

    private void transport(Object expectedFrame,MusicState exactObservation,MusicMediaSession.Action action){
        if(expectedFrame!=interactionFrame || exactObservation==null || exactObservation!=observed || !offered.contains(action))return;
        intent(callback->{if(expectedFrame==interactionFrame && exactObservation==observed && offered.contains(action))callback.requestTransport(action,exactObservation.intentGeneration());});
    }
    private void intent(java.util.function.Consumer<Actions> action){
        BrowserToolbar.Binding captured=binding;Actions callback=actions;Object expectedFrame=interactionFrame;
        if(captured==null || callback==null || expectedFrame==null)return;
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{captured.check();if(captured!=binding || callback!=actions || expectedFrame!=interactionFrame)return;action.accept(callback);});
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && binding==captured && actions==callback && expectedFrame==interactionFrame)state.setText("操作结果未知，请核对官方播放器状态");
    }

    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static void ownerThread(){if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new IllegalStateException("Native UI owner thread required");}
}
