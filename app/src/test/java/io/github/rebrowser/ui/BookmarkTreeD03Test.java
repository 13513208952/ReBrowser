package io.github.rebrowser.ui;

import static org.junit.Assert.*;
import org.junit.Test;

/** Pure bounded bookmark tree; no store or navigation is executed. */
public final class BookmarkTreeD03Test {
    private static BookmarkView.Node folder(Object token,Object parent,String title){return new BookmarkView.Node(token,parent,title,"",BookmarkView.Kind.FOLDER);}
    private static BookmarkView.Node bookmark(Object token,Object parent,String title){return new BookmarkView.Node(token,parent,title,"https://example/",BookmarkView.Kind.BOOKMARK);}
    @Test public void navigationKeepsTokensAndPaths(){
        BookmarkView.Tree tree=new BookmarkView.Tree(java.util.Arrays.asList(
                folder("root",null,"根"),folder("sub","root","子"),bookmark("link","sub","站点A")),5);
        assertEquals(1,tree.children(null).size());
        assertEquals("root",tree.children(null).get(0).token());
        java.util.List<BookmarkView.Row> rows=tree.rowsIn("sub");
        assertEquals(1,rows.size());
        assertEquals("link",rows.get(0).itemRef());
        assertTrue(rows.get(0).pathLabel().contains("子"));
        assertEquals(1,tree.search("站点").size());
    }
    @Test public void duplicateCycleDepthAndUnknownParentAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new BookmarkView.Tree(java.util.Arrays.asList(folder("a",null,"A"),folder("a",null,"B")),1));
        assertThrows(IllegalArgumentException.class,()->new BookmarkView.Tree(java.util.Arrays.asList(folder("a","b","A"),folder("b","a","B")),1));
        assertThrows(IllegalArgumentException.class,()->new BookmarkView.Tree(java.util.Arrays.asList(folder("a","missing","A")),1));
        assertThrows(IllegalArgumentException.class,()->new BookmarkView.Tree(java.util.Arrays.asList(folder("a","a","A")),1));
        Object parent=null;java.util.List<BookmarkView.Node> deep=new java.util.ArrayList<>();
        for(int i=0;i<BookmarkView.MAX_DEPTH+2;i++){Object token="n"+i;deep.add(folder(token,parent,"N"+i));parent=token;}
        assertThrows(IllegalArgumentException.class,()->new BookmarkView.Tree(deep,1));
    }
    @Test public void invalidNodesAndFilterBoundsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new BookmarkView.Node(null,null,"t","",BookmarkView.Kind.FOLDER));
        assertThrows(IllegalArgumentException.class,()->new BookmarkView.Node("t",null,"","",BookmarkView.Kind.FOLDER));
        BookmarkView.Tree tree=new BookmarkView.Tree(java.util.Arrays.asList(bookmark("b",null,"标题")),1);
        assertThrows(IllegalArgumentException.class,()->tree.search(new String(new char[65]).replace('\0','x')));
        assertEquals(0,tree.search("   ").size());
    }

    @Test public void bookmarkCannotParentNodesAndVisibleFolderBudgetIsExact(){
        Object notFolder=new Object(),child=new Object();
        assertThrows(IllegalArgumentException.class,()->new BookmarkView.Tree(java.util.Arrays.asList(
                bookmark(notFolder,null,"普通书签"),bookmark(child,notFolder,"非法子节点")),1));
        Object folder=new Object();
        java.util.List<BookmarkView.Node> accepted=new java.util.ArrayList<>();
        accepted.add(folder(folder,null,"根目录"));
        for(int i=0;i<BookmarkView.MAX_ROWS;i++)accepted.add(bookmark(new Object(),folder,"记录"+i));
        BookmarkView.Tree good=new BookmarkView.Tree(accepted,1);
        assertEquals(BookmarkView.MAX_ROWS,good.rowsIn(folder).size());
        accepted.add(bookmark(new Object(),folder,"超出容量"));
        assertThrows(IllegalArgumentException.class,()->new BookmarkView.Tree(accepted,1));
    }
    @Test public void aggregateSearchDoesNotOverrunSinglePageBudget(){
        Object folderA=new Object(),folderB=new Object();
        java.util.List<BookmarkView.Node> nodes=new java.util.ArrayList<>();
        nodes.add(folder(folderA,null,"A"));nodes.add(folder(folderB,null,"B"));
        for(int i=0;i<60;i++)nodes.add(bookmark(new Object(),folderA,"matchA"+i));
        for(int i=0;i<60;i++)nodes.add(bookmark(new Object(),folderB,"matchB"+i));
        BookmarkView.Tree tree=new BookmarkView.Tree(nodes,1);
        assertThrows(IllegalArgumentException.class,()->tree.search("match"));
    }
}
