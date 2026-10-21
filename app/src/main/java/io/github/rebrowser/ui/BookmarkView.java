package io.github.rebrowser.ui;

import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bounded native bookmark/folder presentation with LOCAL tree navigation and literal search over
 * the currently supplied tree only. It owns no store, saves/deletes nothing, shows no dialog and
 * performs no navigation: open/add/edit/remove are user INTENTS with the exact captured token.
 * Cycles, duplicate tokens, excessive depth/size and unknown parents are rejected, never guessed.
 */
public final class BookmarkView extends ScrollView {
    public static final int MAX_ROWS = 100;
    public static final int MAX_NODES = 500;
    public static final int MAX_DEPTH = 16;
    public static final int MAX_TITLE_CHARS = 4096;
    public static final int MAX_URL_CHARS = 8192;
    public static final int MAX_SEARCH_CHARS = 64;

    public enum Kind { FOLDER, BOOKMARK }

    public interface Actions {
        void open(Row row, long expectedRevision);
        void add(boolean folder, long expectedRevision);
        void edit(Row row, long expectedRevision);
        void remove(Row row, long expectedRevision);
    }

    /** Immutable row. FOLDER keeps an empty displayUrl; BOOKMARK requires display text. */
    public static final class Row {
        private final Object itemRef;
        private final String title, displayUrl;
        private final Kind kind;
        private final String pathLabel;
        public Row(Object itemRef, String title, String displayUrl, Kind kind) { this(itemRef,title,displayUrl,kind,""); }
        public Row(Object itemRef, String title, String displayUrl, Kind kind, String pathLabel) {
            if (itemRef == null || kind == null) throw invalid();
            this.itemRef = itemRef;
            this.title = text(title, MAX_TITLE_CHARS, false);
            this.displayUrl = text(displayUrl == null ? "" : displayUrl, MAX_URL_CHARS, true);
            if (kind == Kind.FOLDER && !this.displayUrl.isEmpty()) throw invalid();
            if (kind == Kind.BOOKMARK && this.displayUrl.isEmpty()) throw invalid();
            this.kind = kind;
            this.pathLabel = text(pathLabel == null ? "" : pathLabel, 512, true);
        }
        public Object itemRef() { return itemRef; }
        public String title() { return title; }
        public String displayUrl() { return displayUrl; }
        public Kind kind() { return kind; }
        public String pathLabel(){return pathLabel;}
    }

    public static final class Rows {
        public static void validate(List<Row> rows, long pageRevision) {
            if (pageRevision < 1) throw new IllegalArgumentException("Invalid native bookmark page revision");
            if (rows == null || rows.size() > MAX_ROWS) throw new IllegalArgumentException("Invalid native bookmark page size");
            Map<Object, Row> seen = new IdentityHashMap<>();
            for (Row row : rows) {
                if (row == null) throw new IllegalArgumentException("Missing native bookmark row");
                if (seen.put(row.itemRef(), row) != null) throw new IllegalArgumentException("Duplicate native bookmark row token");
            }
        }
        public static String label(Row row) {
            if (row == null) throw invalid();
            return (row.kind == Kind.FOLDER ? "文件夹 · " : "书签 · ") + row.title;
        }
        public static String description(Row row) {
            if (row == null) throw invalid();
            return row.kind == Kind.FOLDER ? "文件夹，可展开" : row.displayUrl;
        }
    }

    /** One supplied tree node: parent token is native data; it is not a load/authority reference. */
    public static final class Node {
        private final Object token;private final Object parentToken;private final String title,displayUrl;private final Kind kind;
        public Node(Object token,Object parentToken,String title,String displayUrl,Kind kind){
            if(token==null || kind==null)throw invalid();
            this.token=token;this.parentToken=parentToken;
            this.title=text(title,MAX_TITLE_CHARS,false);
            this.displayUrl=text(displayUrl==null?"":displayUrl,MAX_URL_CHARS,true);
            if(kind==Kind.FOLDER && !this.displayUrl.isEmpty())throw invalid();
            if(kind==Kind.BOOKMARK && this.displayUrl.isEmpty())throw invalid();
            this.kind=kind;
        }
        public Object token(){return token;}
        public Object parentToken(){return parentToken;}
        public String title(){return title;}
        public String displayUrl(){return displayUrl;}
        public Kind kind(){return kind;}
        public Row row(String pathLabel){return new Row(token,title,displayUrl,kind,pathLabel);}
    }

    /** Bounded, cycle-checked tree. Rejects rather than expanding or fabricating missing parents. */
    public static final class Tree {
        private final List<Node> nodes;private final long revision;
        private final Map<Object,List<Node>> childrenByParent;
        public Tree(List<Node> nodes,long revision){
            if(nodes==null || nodes.size()>MAX_NODES || revision<1)throw new IllegalArgumentException("Invalid native bookmark tree");
            Map<Object,Node> byToken=new IdentityHashMap<>();
            List<Node> copy=new ArrayList<>();
            for(Node node:nodes){
                if(node==null)throw new IllegalArgumentException("Missing native bookmark node");
                if(byToken.put(node.token(),node)!=null)throw new IllegalArgumentException("Duplicate native bookmark token");
                copy.add(node);
            }
            Map<Object,List<Node>> children=new IdentityHashMap<>();
            for(Node node:copy){
                if(node.parentToken()!=null){
                    Node parent=byToken.get(node.parentToken());
                    if(parent==null)throw new IllegalArgumentException("Unknown native bookmark parent");
                    if(parent.kind()!=Kind.FOLDER)throw new IllegalArgumentException("A bookmark cannot own children");
                }
                if(node.parentToken()==node.token())throw new IllegalArgumentException("Native bookmark self-parent");
                List<Node> siblings=children.computeIfAbsent(node.parentToken(),key->new ArrayList<>());
                if(siblings.size()>=MAX_ROWS)throw new IllegalArgumentException("Native bookmark folder exceeds page budget");
                siblings.add(node);
            }
            for(Node node:copy){
                int depth=0;Object cursor=node.parentToken();Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
                while(cursor!=null){
                    if(!seen.add(cursor))throw new IllegalArgumentException("Native bookmark cycle");
                    if(++depth>MAX_DEPTH)throw new IllegalArgumentException("Native bookmark depth exceeded");
                    cursor=byToken.get(cursor).parentToken();
                }
            }
            this.nodes=Collections.unmodifiableList(copy);
            this.childrenByParent=children;
            this.revision=revision;
        }
        public long revision(){return revision;}
        public int size(){return nodes.size();}
        public List<Node> children(Object folderToken){
            if(folderToken!=null && (byToken(folderToken)==null || byToken(folderToken).kind()!=Kind.FOLDER))throw new IllegalArgumentException("Unknown bookmark folder");
            List<Node> children=childrenByParent.get(folderToken);
            return children==null?Collections.<Node>emptyList():Collections.unmodifiableList(children);
        }
        /** Path labels from root to this folder (bounded), used for breadcrumbs and row paths. */
        public String pathLabel(Object folderToken){
            if(folderToken==null)return "收藏";
            List<String> labels=new ArrayList<>();
            Object cursor=folderToken;int guard=0;
            while(cursor!=null && guard++<=MAX_DEPTH){
                Node node=byToken(cursor);
                if(node==null || node.kind()!=Kind.FOLDER)throw new IllegalArgumentException("Unknown bookmark folder");
                labels.add(node.title());
                cursor=node.parentToken();
            }
            Collections.reverse(labels);
            String full="收藏 / " + String.join(" / ", labels);if(full.length()<=512)return full;int end=511;if(Character.isHighSurrogate(full.charAt(end-1)))end--;return full.substring(0,end)+"…";
        }
        /** Literal, case-folded title search over the supplied tree only. */
        public List<Row> search(String literal){
            if(literal==null)return Collections.emptyList();text(literal,MAX_SEARCH_CHARS,true);
            if(literal.trim().isEmpty())return Collections.emptyList();
            String needle=literal.trim().toLowerCase(Locale.ROOT);
            List<Row> matched=new ArrayList<>();
            for(Node node:nodes){
                if(node.title().toLowerCase(Locale.ROOT).contains(needle)){
                    if(matched.size()>=MAX_ROWS)throw new IllegalArgumentException("Bookmark search result exceeds page budget");
                    matched.add(node.row(pathLabel(node.parentToken())));
                }
            }
            return Collections.unmodifiableList(matched);
        }
        public List<Row> rowsIn(Object folderToken){
            List<Row> rows=new ArrayList<>();
            String path=pathLabel(folderToken);
            for(Node node:children(folderToken))rows.add(node.row(path));
            return Collections.unmodifiableList(rows);
        }
        private Node byToken(Object token){for(Node node:nodes)if(node.token()==token)return node;return null;}
    }

    /** Owner-thread page/frame reducer; captured frames never retarget a newer page. */
    public static final class State {
        private final Thread owner = Thread.currentThread();
        private long revision;
        private List<Row> rows = Collections.emptyList();
        private Object frame;
        private boolean retired;
        public Object show(long pageRevision, List<Row> next) {
            check();
            if (retired) throw new IllegalStateException("Retired native list cannot show a new page");
            Rows.validate(next, pageRevision);
            revision = pageRevision;
            rows = Collections.unmodifiableList(new ArrayList<>(next));
            frame = new Object();
            return frame;
        }
        public void hide() { check(); rows = Collections.emptyList(); frame = null; }
        public void retire() { check(); if (retired) return; retired = true; rows = Collections.emptyList(); frame = null; }
        public Object frame() { check(); return frame; }
        public long revision() { check(); return revision; }
        public int rowCount() { check(); return retired ? 0 : rows.size(); }
        public boolean current(Object expectedFrame, long expectedRevision) {
            check();
            return !retired && expectedFrame != null && expectedFrame == frame && expectedRevision == revision;
        }
        public List<Row> rows() { check(); return rows; }
        public Row rowAt(int position) {
            check();
            if (retired || position < 0 || position >= rows.size()) return null;
            return rows.get(position);
        }
        public Row require(Object expectedFrame, long expectedRevision, Object itemRef) {
            check();
            if (!current(expectedFrame, expectedRevision)) throw new IllegalStateException("Stale native bookmark frame");
            for (Row row : rows) if (row.itemRef() == itemRef) return row;
            throw new IllegalArgumentException("Unknown native bookmark row token");
        }
        private void check() { if (Thread.currentThread() != owner) throw new IllegalStateException("Bookmark view owner thread required"); }
    }

    private final LinearLayout rowsContainer;
    private final LinearLayout pathBar;
    private final TextView pathLabel;
    private final EditText searchInput;
    private final State state = new State();
    private BrowserToolbar.Binding binding;
    private Actions actions;
    private Object frame;
    private Tree tree;
    private Object currentFolder;
    private String searchText="";

    public BookmarkView(Context context) {
        super(context);
        BrowserToolbar.main();
        LinearLayout root=new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        pathBar=new LinearLayout(context);
        pathBar.setOrientation(LinearLayout.HORIZONTAL);
        Button up=new Button(context);
        up.setText("返回上一级");
        up.setMinimumHeight(dp(48));
        up.setOnClickListener(v->navigateUp());
        pathBar.addView(up,new LinearLayout.LayoutParams(dp(112),dp(48)));
        pathLabel=new TextView(context);
        pathLabel.setText("收藏");
        pathLabel.setMinimumHeight(dp(48));
        pathBar.addView(pathLabel,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(pathBar);
        searchInput=new EditText(context);searchInput.setSaveEnabled(false);searchInput.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        searchInput.setSingleLine(true);
        searchInput.setHint("检索当前提供的收藏标题");
        searchInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(MAX_SEARCH_CHARS)});
        root.addView(searchInput,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
        Button search=new Button(context);
        search.setText("检索");
        search.setMinimumHeight(dp(48));
        search.setOnClickListener(v->applySearch());
        root.addView(search,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
        rowsContainer=new LinearLayout(context);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(rowsContainer,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
        addView(root,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
    }

    /** Flat list binding (kept for callers without a tree); still searchable locally. */
    public void bind(BrowserToolbar.Binding binding, long pageRevision, List<Row> page, Actions actions) {
        BrowserToolbar.main();
        if (binding == null || actions == null) throw invalid();
        binding.check();
        state.show(pageRevision, page);
        tree=null;currentFolder=null;searchText="";searchInput.setText("");
        this.binding = binding;this.actions = actions;frame = state.frame();pathLabel.setText("收藏");
        renderRows(page);
    }

    /** Tree binding: folder navigation and title search over the same supplied nodes. */
    public void bindTree(BrowserToolbar.Binding binding,Tree tree,Actions actions){
        BrowserToolbar.main();
        if(binding==null || tree==null || actions==null)throw invalid();
        binding.check();
        this.tree=tree;this.currentFolder=null;
        state.show(tree.revision(),tree.rowsIn(null));
        this.binding=binding;this.actions=actions;frame=state.frame();
        searchText="";searchInput.setText("");
        renderTreeRows();
    }

    private void navigateUp(){
        if(tree==null || frame==null)return;
        Object parent=null;
        for(Node node:tree.nodes)if(node.token()==currentFolder){parent=node.parentToken();break;}
        currentFolder=parent;
        searchText="";searchInput.setText("");
        renderTreeRows();
    }

    private void applySearch(){
        BrowserToolbar.main();
        if(tree==null)return;
        searchText=searchInput.getText()==null?"":searchInput.getText().toString();
        renderTreeRows();
    }

    private void renderTreeRows(){
        if(tree==null || binding==null)return;
        try{binding.check();}catch(RuntimeException stale){unbind();return;}
        List<Row> rows;
        if(searchText.trim().isEmpty()){
            rows=tree.rowsIn(currentFolder);
        }else{
            try{rows=tree.search(searchText);}catch(IllegalArgumentException invalid){pathLabel.setText("检索条件无效");return;}
        }
        state.show(tree.revision(),rows);
        frame=state.frame();
        pathLabel.setText(searchText.trim().isEmpty()?tree.pathLabel(currentFolder):"检索：" + searchText.trim() + "（" + rows.size() + "）");
        renderRows(rows);
    }

    private void renderRows(List<Row> page){
        unbindRows();
        final Object current=frame;
        final long revision=state.revision();
        Button newBookmark = button("新建书签");
        newBookmark.setOnClickListener(v -> runAdd(current, revision, false));
        Button newFolder = button("新建文件夹");
        newFolder.setOnClickListener(v -> runAdd(current, revision, true));
        rowsContainer.addView(newBookmark);
        rowsContainer.addView(newFolder);
        if(page.isEmpty()){
            TextView empty=new TextView(getContext());
            empty.setText("当前路径没有可显示的收藏（未读取其他来源）");
            rowsContainer.addView(empty);
            return;
        }
        for (Row row : page) {
            LinearLayout line = new LinearLayout(getContext());
            line.setOrientation(LinearLayout.HORIZONTAL);
            Button open = button(Rows.label(row));
            open.setMinimumWidth(dp(160));
            open.setContentDescription(row.kind() == Kind.FOLDER ? "展开文件夹：" + row.title() : "打开书签：" + row.title());
            open.setOnClickListener(v -> {
                if(row.kind()==Kind.FOLDER && tree!=null){if(frame!=current || !state.current(current,revision) || binding==null)return;try{binding.check();state.require(current,revision,row.itemRef());}catch(RuntimeException stale){return;}if(frame!=current)return;currentFolder=row.itemRef();searchText="";searchInput.setText("");renderTreeRows();}
                else run(current, revision, row.itemRef(), (callback, exact) -> callback.open(exact, revision));
            });
            line.addView(open, new LinearLayout.LayoutParams(0, dp(48), 1));
            Button edit = button("编辑");
            edit.setContentDescription("编辑：" + row.title());
            edit.setOnClickListener(v -> run(current, revision, row.itemRef(), (callback, exact) -> callback.edit(exact, revision)));
            line.addView(edit, new LinearLayout.LayoutParams(dp(72), dp(48)));
            Button remove = button("移除");
            remove.setContentDescription("移除：" + row.title());
            remove.setOnClickListener(v -> run(current, revision, row.itemRef(), (callback, exact) -> callback.remove(exact, revision)));
            line.addView(remove, new LinearLayout.LayoutParams(dp(72), dp(48)));
            rowsContainer.addView(line);
        }
    }

    /** Drops every private text/row/listener; the page must be read again before reuse. */
    public void unbind() {
        BrowserToolbar.main();
        binding = null;
        actions = null;
        tree=null;currentFolder=null;searchText="";
        state.hide();frame=null;searchInput.setText("");
        unbindRows();
    }

    public void retire() {
        BrowserToolbar.main();
        binding = null;
        actions = null;
        tree=null;currentFolder=null;
        state.retire();frame=null;searchInput.setText("");
        unbindRows();
    }

    private void unbindRows() {
        BrowserToolbar.clearOwnedViewTree(rowsContainer);rowsContainer.removeAllViews();
        setContentDescription(null);
        if(binding==null)pathLabel.setText("收藏");
    }

    private void runAdd(Object expected, long expectedRevision, boolean folder) {
        BrowserToolbar.Binding captured = binding;
        Actions callback = actions;
        if (captured == null || callback == null || frame != expected) return;
        try{captured.check();if(frame!=expected || captured!=binding || callback!=actions || !state.current(expected,expectedRevision))return;}
        catch(RuntimeException stale){return;}
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->callback.add(folder,expectedRevision));
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && frame==expected && binding==captured && actions==callback)setContentDescription("操作结果未知，请重新读取收藏状态");
    }

    private void run(Object expected, long expectedRevision, Object itemRef, java.util.function.BiConsumer<Actions, Row> action) {
        BrowserToolbar.Binding captured = binding;
        Actions callback = actions;
        if (captured == null || callback == null || frame != expected) return;
        final Row row;
        try{captured.check();if(frame!=expected || captured!=binding || callback!=actions)return;row=state.require(expected,expectedRevision,itemRef);}
        catch(RuntimeException stale){return;}
        BrowserToolbar.IntentOutcome outcome=BrowserToolbar.invokeNativeIntent(()->action.accept(callback,row));
        if(outcome==BrowserToolbar.IntentOutcome.CALLBACK_UNKNOWN && frame==expected && binding==captured && actions==callback)setContentDescription("操作结果未知，请重新读取收藏状态");
    }

    @Override protected void onDetachedFromWindow() {
        unbind();
        super.onDetachedFromWindow();
    }

    private Button button(String label) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setMinimumHeight(dp(48));
        return button;
    }

    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }

    private static String text(String value, int maximum, boolean allowEmpty) {
        if (value == null || value.length() > maximum || (!allowEmpty && value.isEmpty())) throw invalid();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c)) throw invalid();
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw invalid();
            } else if (Character.isLowSurrogate(c)) throw invalid();
        }
        return value;
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid native bookmark row/page/tree"); }
}
