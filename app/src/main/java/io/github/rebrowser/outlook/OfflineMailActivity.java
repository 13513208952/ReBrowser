package io.github.rebrowser.outlook;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import io.github.rebrowser.ui.BrowserToolbar;
import java.util.*;

/** Native one-page presentation only: no DB, account authentication, HTML, attachment IO/export.
 * Exact private-issued presentation/request identity, NOT query-string equality, binds async UI
 * responses. Retiring that recipient does NOT stop the native reader/writer or release its credit.
 */
public final class OfflineMailActivity extends Activity {
 public static final int MAX_ROWS=100,MAX_TEXT_CHARS=4096,MAX_SEARCH_CHARS=64,MAX_PAGE_NUMBER=10000;
 public enum PageDirection {PREVIOUS,NEXT}
 public static final class Query {
  private final String literal;private final int pageNumber;
  public Query(String literal,int pageNumber){String raw=literal==null?"":text(literal,MAX_SEARCH_CHARS);if(pageNumber<0 || pageNumber>MAX_PAGE_NUMBER)throw new IllegalArgumentException("Invalid page number");this.literal=raw.trim();this.pageNumber=pageNumber;}
  public String literal(){return literal;}public int pageNumber(){return pageNumber;}
  @Override public String toString(){return "query-page-"+pageNumber;} // no private literal in diagnostics
 }
 public static final class PageNav {
  private final Boolean previous,next;
  public PageNav(Boolean previous,Boolean next){this.previous=previous;this.next=next;}
  public Boolean previousAvailable(){return previous;}public Boolean nextAvailable(){return next;}
 }
 public static final class MailRow {
  private final Object token;private final String subject,sender,dateText,preview;private final boolean attachment;
  public MailRow(Object token,String subject,String sender,String dateText,String preview,boolean attachment){if(token==null)throw new IllegalArgumentException("Missing mail token");this.token=token;this.subject=text(subject,512);this.sender=text(sender,256);this.dateText=text(dateText,64);this.preview=text(preview,512);this.attachment=attachment;}
  public Object token(){return token;}public String subject(){return subject;}public String sender(){return sender;}public String dateText(){return dateText;}public String preview(){return preview;}public boolean attachmentPresent(){return attachment;}
 }
 public static final class Page {
  private final List<MailRow> rows;private final long revision;
  public Page(List<MailRow> rows,long revision){if(rows==null || rows.size()>MAX_ROWS || revision<1)throw new IllegalArgumentException("Invalid mail page");Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());for(MailRow row:rows)if(row==null || !seen.add(row.token()))throw new IllegalArgumentException("Invalid/duplicate mail row");this.rows=Collections.unmodifiableList(new ArrayList<>(rows));this.revision=revision;}
  public List<MailRow> rows(){return rows;}public long revision(){return revision;}
 }
 /** Private-issued UI identity ONLY, not actor/account/permission authority. */
 public static final class Presentation {
  private final Page page;private final Query query;private final BrowserToolbar.Binding binding;
  private Presentation(Page page,Query query,BrowserToolbar.Binding binding){this.page=page;this.query=query;this.binding=binding;}
  public Page page(){return page;}public Query query(){return query;}
 }
 public static final class BodyRequest {
  private final Presentation presentation;private final MailRow row;private final boolean attachment;private boolean delivered;
  private BodyRequest(Presentation p,MailRow row,boolean attachment){presentation=p;this.row=row;this.attachment=attachment;}
  public Presentation presentation(){return presentation;}public MailRow row(){return row;}public boolean attachment(){return attachment;}
 }
 public interface Actions {
  void open(BodyRequest request);
  void attachment(BodyRequest request);
  void search(Presentation expectedPresentation,String literal,int maxResults);
  void requestPage(Presentation expectedPresentation,PageDirection direction);
 }
 private TextView status;private EditText search;private ListView list;private Button previous,next,submit;
 private final Adapter adapter=new Adapter();private Presentation current;private Actions actions;
 private PageNav nav=new PageNav(null,null);private boolean requestAttempted;private BodyRequest pendingBody;
 private final Map<View,Boolean> ownedRows=new WeakHashMap<>();private final Object holderIssuer=new Object();
 @Override protected void onCreate(Bundle saved){super.onCreate(saved);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);status=new TextView(this);root.addView(status);search=new EditText(this);search.setSingleLine(true);search.setHint("搜索本机离线邮件（字面匹配）");search.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(MAX_SEARCH_CHARS)});search.setSaveEnabled(false);search.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);root.addView(search);submit=new Button(this);submit.setText("搜索");submit.setMinimumHeight(dp(48));submit.setOnClickListener(v->submitSearch());root.addView(submit);
  search.setOnEditorActionListener((view,action,event)->{if(action!=EditorInfo.IME_ACTION_SEARCH && !(event!=null && event.getKeyCode()==KeyEvent.KEYCODE_ENTER && event.getAction()==KeyEvent.ACTION_UP))return false;submitSearch();return true;});search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
  LinearLayout paging=new LinearLayout(this);previous=new Button(this);next=new Button(this);previous.setText("上一页");next.setText("下一页");previous.setMinimumHeight(dp(48));next.setMinimumHeight(dp(48));previous.setOnClickListener(v->requestPage(PageDirection.PREVIOUS));next.setOnClickListener(v->requestPage(PageDirection.NEXT));paging.addView(previous);paging.addView(next);root.addView(paging);list=new ListView(this);list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);showUnavailable(null);
 }
 public void showPage(BrowserToolbar.Binding binding,Page page,Actions actions){showPage(binding,page,new Query("",0),new PageNav(null,null),actions);}
 public Presentation showPage(BrowserToolbar.Binding binding,Page page,Query query,PageNav nav,Actions actions){
  owner();if(binding==null || page==null || query==null || nav==null || actions==null)throw new IllegalArgumentException("Missing native page binding");binding.check();scrubRows();current=new Presentation(page,query,binding);this.actions=actions;this.nav=nav;requestAttempted=false;pendingBody=null;adapter.notifyDataSetChanged();search.setText(query.literal());status.setText(page.rows().isEmpty()?"本页未提供离线邮件":"离线只读列表；正文仅纯文本");updateButtons();return current;
 }
 /** Native owner uses its original request's receipt, never reacquires the latest frame. */
 public Presentation showPageResponse(Presentation expected,Page page,Query query,PageNav nav,Actions actions){owner();if(!eligible(expected))return null;return showPage(expected.binding,page,query,nav,actions);}
 private boolean eligible(Presentation p){if(p==null || current!=p || actions==null)return false;try{p.binding.check();}catch(RuntimeException stale){return false;}return current==p && actions!=null;}
 private void submitSearch(){owner();Presentation p=current;Actions callback=actions;if(!eligible(p) || requestAttempted)return;String literal;try{literal=text(search.getText().toString(),MAX_SEARCH_CHARS).trim();if(literal.isEmpty())throw new IllegalArgumentException("Empty literal");}catch(RuntimeException invalid){search.setError("搜索内容为空或无效");return;}if(current!=p || actions!=callback)return;requestAttempted=true;updateButtons();status.setText("检索已请求，等待原生结果");String exact=literal;BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{if(eligible(p) && actions==callback)callback.search(p,exact,50);});if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && current==p)status.setText("检索结果未知，等待原生重新读取");}
 private void requestPage(PageDirection direction){owner();Presentation p=current;Actions callback=actions;if(direction==null || !eligible(p) || requestAttempted)return;Boolean offered=direction==PageDirection.PREVIOUS?nav.previousAvailable():nav.nextAvailable();if(!Boolean.TRUE.equals(offered))return;if(current!=p || actions!=callback)return;requestAttempted=true;updateButtons();status.setText("翻页已请求，等待原生结果");BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{if(eligible(p) && actions==callback)callback.requestPage(p,direction);});if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && current==p)status.setText("翻页结果未知，不自动重试");}
 private void updateButtons(){boolean connected=current!=null && actions!=null;previous.setEnabled(connected && !requestAttempted && Boolean.TRUE.equals(nav.previousAvailable()));next.setEnabled(connected && !requestAttempted && Boolean.TRUE.equals(nav.nextAvailable()));submit.setEnabled(connected && !requestAttempted);search.setEnabled(connected && !requestAttempted);}
 public void showUnavailable(String reason){owner();current=null;actions=null;pendingBody=null;requestAttempted=false;nav=new PageNav(null,null);scrubRows();adapter.notifyDataSetChanged();search.setText("");search.setError(null);status.setText(reason==null?"未连接原生离线窗口":BrowserToolbar.boundedStatusReason(reason));updateButtons();}
 public boolean showBodyText(BodyRequest request,String subject,String plainText){owner();if(request==null || request!=pendingBody || request.attachment || request.delivered || !eligible(request.presentation))return false;String rendered=text(subject,512)+"\n"+text(plainText,64*1024);if(request!=pendingBody || !eligible(request.presentation))return false;request.delivered=true;pendingBody=null;status.setText(rendered);return true;}
 /** Legacy caller lacks private presentation/request correlation; never reacquire a fresh one. */
 @Deprecated public boolean showBodyText(BrowserToolbar.Binding binding,Page page,Object token,String subject,String text){return false;}
 @Deprecated public boolean showBodyText(BrowserToolbar.Binding binding,Page page,Query query,Object token,String subject,String text){return false;}
 private void dispatch(Presentation p,MailRow row,boolean attachment){owner();Actions callback=actions;if(!eligible(p) || callback==null || attachment && !row.attachmentPresent())return;boolean found=false;for(MailRow candidate:p.page.rows())if(candidate==row){found=true;break;}if(!found || current!=p || actions!=callback)return;BodyRequest request=new BodyRequest(p,row,attachment);pendingBody=attachment?null:request;BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->{if(!eligible(p) || actions!=callback)return;if(attachment)callback.attachment(request);else callback.open(request);});if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && current==p && actions==callback)status.setText("邮件操作结果未知，等待原生重新读取");}
 private void scrubRows(){for(View root:new ArrayList<>(ownedRows.keySet()))if(root!=null)BrowserToolbar.clearOwnedViewTree(root);ownedRows.clear();}
 @Override protected void onDestroy(){current=null;actions=null;pendingBody=null;scrubRows();if(list!=null)list.setAdapter(null);BrowserToolbar.clearOwnedViewTree(findViewById(android.R.id.content));super.onDestroy();}
 private final class Adapter extends BaseAdapter {
  public int getCount(){return current==null?0:current.page.rows().size();}public MailRow getItem(int position){return current.page.rows().get(position);}public long getItemId(int position){return position;}
  public View getView(int position,View recycled,ViewGroup parent){owner();Holder holder;if(recycled!=null && ownedRows.containsKey(recycled) && recycled.getTag() instanceof Holder && ((Holder)recycled.getTag()).issuer==holderIssuer)holder=(Holder)recycled.getTag();else{if(ownedRows.size()>=MAX_ROWS*2)throw new IllegalStateException("Mail row view budget");holder=new Holder();holder.root.setTag(holder);ownedRows.put(holder.root,Boolean.TRUE);}Presentation p=current;if(p==null || position<0 || position>=p.page.rows().size()){BrowserToolbar.clearOwnedViewTree(holder.root);return holder.root;}MailRow row=p.page.rows().get(position);holder.title.setText(row.subject());holder.detail.setText(row.sender()+" · "+row.dateText()+" · "+row.preview());holder.open.setText("打开");holder.attach.setText("附件");holder.open.setEnabled(true);holder.attach.setEnabled(row.attachmentPresent());holder.open.setOnClickListener(v->dispatch(p,row,false));holder.attach.setOnClickListener(row.attachmentPresent()?v->dispatch(p,row,true):null);holder.open.setContentDescription("打开离线邮件："+row.subject());holder.attach.setContentDescription("附件："+row.subject());return holder.root;}
 }
 private final class Holder {final Object issuer=holderIssuer;final LinearLayout root=new LinearLayout(OfflineMailActivity.this);final TextView title=new TextView(OfflineMailActivity.this),detail=new TextView(OfflineMailActivity.this);final Button open=new Button(OfflineMailActivity.this),attach=new Button(OfflineMailActivity.this);Holder(){root.setOrientation(LinearLayout.VERTICAL);open.setMinimumHeight(dp(48));attach.setMinimumHeight(dp(48));root.addView(title);root.addView(detail);LinearLayout buttons=new LinearLayout(OfflineMailActivity.this);buttons.addView(open);buttons.addView(attach);root.addView(buttons);}}
 private static String text(String value,int max){if(value==null || value.length()>max)throw new IllegalArgumentException("Unbounded offline text");for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Broken UTF16");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Broken UTF16");}return value;}
 private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 private static void owner(){if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new IllegalStateException("Native window owner thread required");}
}
