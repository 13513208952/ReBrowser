package io.github.rebrowser.browser;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.data.NativeRecordCodec;
import io.github.rebrowser.data.OwnedDataCatalog;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.*;

/** Browser-global URL FAVORITES, not secondary-workspace environment bookmarks.
 * Explicit immutable edits + source-bound exact CAS; no cookie/profile import or URL authority.
 * Historical RBBOOK01 empty/flat records remain exact-compatible. Parent/folder fields are
 * reserved FALSE; wrong unshipped hierarchy is rejected, never silently flattened or repaired.
 * Native metadata IDs are not session/document/administrator/permission references. */
public final class BookmarkStore {
    public static final int MAX_NODES=500,MAX_BYTES=1024*1024;
    public static final class Node {
        private final String id,title,url;
        public Node(String id,String title,String url){if(!BookmarkStore.id(id))throw bad();this.id=id;this.url=web(url);this.title=cleanTitle(title,this.url);}
        public String id(){return id;}public String title(){return title;}public String url(){return url;}
    }
    public static final class Snapshot {
        private final long revision;private final List<Node> nodes;private final Map<String,Node> index;
        public Snapshot(long metadataRevision,List<Node> nodes){
            if(metadataRevision<0 || nodes==null || nodes.size()>MAX_NODES)throw bad();revision=metadataRevision;List<Node> copy=new ArrayList<>(nodes);Map<String,Node> byId=new LinkedHashMap<>();Set<String> urls=new HashSet<>();long bytes=20;
            for(Node node:copy){if(node==null || byId.put(node.id,node)!=null || !urls.add(favoriteKey(node.url)))throw bad();bytes+=2+35+2+2+utfBytes(node.title)+2+utfBytes(node.url);if(bytes>MAX_BYTES)throw bad();}
            this.nodes=Collections.unmodifiableList(copy);index=Collections.unmodifiableMap(byId);
        }
        public long metadataRevision(){return revision;}public List<Node> nodes(){return nodes;}
        public Node require(String id){Node node=index.get(id);if(node==null)throw bad();return node;}
        public Node findByUrl(String url){String key=favoriteKey(url);for(Node node:nodes)if(favoriteKey(node.url).equals(key))return node;return null;}
        public Snapshot add(String title,String url){String canonical=web(url);if(findByUrl(canonical)!=null)return this;if(nodes.size()>=MAX_NODES)throw new IllegalStateException("Favorites count limit reached");String id=null;for(int n=0;n<16;n++){String candidate=randomId();if(!index.containsKey(candidate)){id=candidate;break;}}if(id==null)throw bad();List<Node> copy=new ArrayList<>(nodes);copy.add(new Node(id,title,canonical));return new Snapshot(next(revision),copy);}
        public Snapshot editExact(String id,String expectedUrl,String title,String url){Node before=require(id);if(!before.url.equals(web(expectedUrl)))throw new IllegalStateException("Original favorite changed");Node other=findByUrl(url);if(other!=null && other!=before)throw new IllegalArgumentException("URL already favorited");List<Node> copy=new ArrayList<>(nodes);copy.set(copy.indexOf(before),new Node(id,title,url));return new Snapshot(next(revision),copy);}
        public Snapshot removeExact(String id,String expectedUrl){Node before=require(id);if(!before.url.equals(web(expectedUrl)))throw new IllegalStateException("Original favorite changed");List<Node> copy=new ArrayList<>(nodes);copy.remove(before);return new Snapshot(next(revision),copy);}
    }
    public static final NativeRecordCodec.Codec<Snapshot> CODEC=new NativeRecordCodec.Codec<Snapshot>(){
        public Snapshot decode(ByteBuffer input){NativeRecordCodec.Reader r=new NativeRecordCodec.Reader(input,"RBBOOK01",MAX_BYTES);long revision=r.nonnegative();int count=r.u16();if(count>MAX_NODES)throw bad();List<Node> nodes=new ArrayList<>();for(int i=0;i<count;i++){String id=r.text(35,false);if(r.bool() || r.bool())throw new IllegalArgumentException("Unshipped favorite hierarchy is unsupported; not converted");String title=r.text(640,false),url=r.text(8192,true);Node node=new Node(id,title,url);if(!node.url.equals(url) || !node.title.equals(title))throw bad();nodes.add(node);}r.end();return new Snapshot(revision,nodes);}
        public byte[] encode(Snapshot snapshot){if(snapshot==null)throw bad();NativeRecordCodec.Writer w=new NativeRecordCodec.Writer("RBBOOK01",MAX_BYTES).nonnegative(snapshot.revision).u16(snapshot.nodes.size());for(Node node:snapshot.nodes)w.text(node.id,35,false).bool(false).bool(false).text(node.title,640,false).text(node.url,8192,true);return w.finish();}
    };
    private final OwnedDataCatalog records;
    public BookmarkStore(OwnedDataCatalog nativeRecords){if(nativeRecords==null)throw bad();records=nativeRecords;}
    public OwnedDataCatalog.View<Snapshot> read(ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{return records.read(OwnedDataCatalog.Family.BOOKMARKS,CODEC,permit);}
    public long compareAndWrite(OwnedDataCatalog.View<Snapshot> expected,Snapshot intended,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{records.requireFamily(expected,OwnedDataCatalog.Family.BOOKMARKS,CODEC);if(intended==null || expected.kind()==io.github.rebrowser.data.PrivateRecordStore.Kind.CURRENT && intended.revision!=next(expected.value(permit).revision))throw bad();return records.compareAndWrite(expected,intended,permit);}
    /** Match inherited favorites (ignore fragments/trailing path slash), NOT recent-full-URL
     * identity. Queries stay distinct; stored/opened full URL is never replaced by this key. */
    public static String favoriteKey(String url){String key=web(url);int fragment=key.indexOf('#');if(fragment>=0)key=key.substring(0,fragment);if(key.indexOf('?')<0)while(key.endsWith("/"))key=key.substring(0,key.length()-1);return key;}
    private static String cleanTitle(String title,String url){String value=title==null?"":title.trim();if(value.isEmpty())value=url;return NativeRecordCodec.validateText(value,640,false);}
    private static String web(String value){NativeRecordCodec.validateText(value,8192,false);return NativeRecordCodec.validateText(SearchEngines.requireWebUrl(value).url(),8192,false);}
    private static boolean id(String value){return value!=null && value.matches("bm_[0-9a-f]{32}");}
    private static int utfBytes(String value){return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;}
    private static long next(long value){if(value==Long.MAX_VALUE)throw bad();return value+1;}
    private static String randomId(){byte[] bytes=new byte[16];new SecureRandom().nextBytes(bytes);StringBuilder out=new StringBuilder("bm_");for(byte b:bytes)out.append(Character.forDigit((b&255)>>>4,16)).append(Character.forDigit(b&15,16));return out.toString();}
    private static IllegalArgumentException bad(){return NativeRecordCodec.bad();}
}
