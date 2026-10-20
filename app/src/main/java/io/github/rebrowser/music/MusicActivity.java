package io.github.rebrowser.music;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import io.github.rebrowser.ui.BrowserToolbar;
import java.util.List;

/**
 * Native music WINDOW SHELL over the OFFICIAL player projection: title/artist/state, queue rows
 * and lyric lines are READ-ONLY observations. Transport/seek/queue buttons emit INTENT only; this
 * window never creates a second player, extracts a URL, restores playback, registers a service or
 * derives playback authorization from a page stop/URL.
 */
public final class MusicActivity extends Activity {
    public interface Actions {
        void playPause();
        void next();
        void previous();
        void seekTo(long positionMs);
        void openQueue();
        void openLyrics();
    }
    public static final int MAX_QUEUE_ROWS = 12;

    /** Pure display arithmetic used by the window; no player state is changed. */
    public static final class Model {
        public static int progressPermille(long positionMs,long durationMs){
            if(positionMs< -1 || positionMs>MusicState.MAX_POSITION_MS || durationMs< -1 || durationMs>MusicState.MAX_POSITION_MS)throw new IllegalArgumentException("Invalid progress range");
            if(positionMs<0 || durationMs<=0)return 0;
            long value=positionMs*1000L/durationMs;
            return (int)Math.max(0,Math.min(1000,value));
        }
        public static long seekTarget(long durationMs,int permille){
            if(durationMs<=0 || durationMs>MusicState.MAX_POSITION_MS || permille<0 || permille>1000)throw new IllegalArgumentException("Invalid seek target");
            return durationMs*permille/1000L;
        }
        public static boolean seekEnabled(MusicState observed){return observed!=null && observed.hasReportedDuration() && observed.playback()==MusicState.Playback.PLAYING;}
    }

    private TextView title,artist,state,lyrics;
    private SeekBar progress;
    private LinearLayout queue;
    private BrowserToolbar.Binding binding;
    private long durationMs=-1;private Object frame,seekFrame;private BrowserToolbar.Binding seekBinding;private Actions seekActions;private long seekDuration;
    private boolean nativeSeekOffered;

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
        Button previous=new Button(this);previous.setText("上一首");previous.setMinimumHeight(dp(48));
        Button toggle=new Button(this);toggle.setText("播放/暂停");toggle.setMinimumHeight(dp(48));
        Button next=new Button(this);next.setText("下一首");next.setMinimumHeight(dp(48));
        transport.addView(previous,new LinearLayout.LayoutParams(0,dp(48),1));
        transport.addView(toggle,new LinearLayout.LayoutParams(0,dp(48),1));
        transport.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(transport);
        lyrics=new TextView(this);lyrics.setTextSize(15);
        root.addView(lyrics);
        queue.setOrientation(LinearLayout.VERTICAL);
        root.addView(queue,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1));
        Button queueButton=new Button(this);queueButton.setText("队列");queueButton.setMinimumHeight(dp(48));
        Button lyricsButton=new Button(this);lyricsButton.setText("歌词");lyricsButton.setMinimumHeight(dp(48));
        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.addView(queueButton,new LinearLayout.LayoutParams(0,dp(48),1));
        bottom.addView(lyricsButton,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(bottom);
        setContentView(root);
        previous.setOnClickListener(v->intent(Actions::previous));
        next.setOnClickListener(v->intent(Actions::next));
        toggle.setOnClickListener(v->intent(Actions::playPause));
        queueButton.setOnClickListener(v->intent(Actions::openQueue));
        lyricsButton.setOnClickListener(v->intent(Actions::openLyrics));
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean fromUser){}
            public void onStartTrackingTouch(SeekBar bar){seekFrame=nativeSeekOffered?frame:null;seekBinding=binding;seekActions=actions;seekDuration=durationMs;}
            public void onStopTrackingTouch(SeekBar bar){
                Object expected=seekFrame;BrowserToolbar.Binding exact=seekBinding;Actions callback=seekActions;long duration=seekDuration;seekFrame=null;seekBinding=null;seekActions=null;
                if(expected==null || expected!=frame || exact!=binding || callback!=actions || !nativeSeekOffered || duration<=0)return;
                long target=Model.seekTarget(duration,bar.getProgress());intent(nativeActions->nativeActions.seekTo(target));
            }
        });
        showState(null,MusicQueue.empty());
    }

    public void bind(BrowserToolbar.Binding binding,MusicState observed,MusicQueue queuePage,Actions actions){
        bind(binding,observed,queuePage,actions,false);
    }
    public void bind(BrowserToolbar.Binding binding,MusicState observed,MusicQueue queuePage,Actions actions,boolean nativeSeekOffered){
        ownerThread();
        if(binding==null || observed==null || queuePage==null || actions==null)throw new IllegalArgumentException("Missing native music binding");
        binding.check();
        this.binding=binding;this.frame=new Object();this.seekFrame=null;this.seekBinding=null;this.seekActions=null;this.nativeSeekOffered=nativeSeekOffered;
        this.actions=actions;
        showState(observed,queuePage);
    }
    private Actions actions;

    public void unbind(){
        ownerThread();
        binding=null;actions=null;frame=null;seekFrame=null;seekBinding=null;seekActions=null;nativeSeekOffered=false;
        showState(null,MusicQueue.empty());
    }

    private void showState(MusicState observed,MusicQueue page){
        title.setText(observed==null?"音乐":(observed.title().isEmpty()?"音乐":observed.title()));
        artist.setText(observed==null?"":observed.artist());
        state.setText(observed==null?"未连接官方播放器":observed.playbackLabel()+" · "+MusicState.text(observed.accountLabel(),64));
        lyrics.setText("歌词未读取");
        durationMs=observed!=null && observed.hasReportedDuration()?observed.durationMs():-1;
        progress.setEnabled(nativeSeekOffered && Model.seekEnabled(observed));
        progress.setProgress(observed==null?0:Model.progressPermille(observed.positionMs(),durationMs));
        BrowserToolbar.clearOwnedViewTree(queue);queue.removeAllViews();
        List<MusicQueue.Track> tracks=page.tracks();
        for(int i=0;i<Math.min(MAX_QUEUE_ROWS,tracks.size());i++){
            MusicQueue.Track track=tracks.get(i);
            TextView row=new TextView(this);
            row.setText((i+1)+". "+track.title()+" — "+track.artist());
            row.setMinimumHeight(dp(48));
            queue.addView(row,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
        }
        if(tracks.isEmpty()){
            TextView empty=new TextView(this);
            empty.setText("队列未读取");
            queue.addView(empty);
        }
    }

    @Override protected void onDestroy(){
        unbind();BrowserToolbar.clearOwnedViewTree(findViewById(android.R.id.content));
        super.onDestroy();
    }

    private void intent(java.util.function.Consumer<Actions> action){
        BrowserToolbar.Binding captured=binding;Actions callback=actions;
        if(captured==null || callback==null)return;
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{captured.check();if(captured!=binding || callback!=actions)return;action.accept(callback);});
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && captured==binding && callback==actions)state.setText("操作结果未知，请核对官方播放器状态");
    }

    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static void ownerThread(){if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new IllegalStateException("Native UI owner thread required");}
}
