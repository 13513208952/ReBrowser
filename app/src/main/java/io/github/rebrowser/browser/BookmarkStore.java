package io.github.rebrowser.browser;

import io.github.rebrowser.bootstrap.ComponentGate;
import io.github.rebrowser.data.NativeRecordCodec;
import io.github.rebrowser.data.OwnedDataCatalog;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.*;

/** Existing encrypted native bookmark/folder record. Explicit immutable edits + exact CAS;
 * no import, migration, automatic defaults, page navigation or UI/admin authority minting.
 * IDs below are metadata, NOT native tab/session/document/permission references. */
public final class BookmarkStore {
    public static final int MAX_NODES=500,MAX_DEPTH=16,MAX_BYTES=1024*1024;
    public static final class Node {
        private final String id,parent,title,url;private final boolean folder;
        public Node(String id,String parent,String title,String url,boolean folder){if(!BookmarkStore.id(id) || parent!=null && !BookmarkStore.id(parent))throw bad();this.id=id;this.parent=parent;this.title=NativeRecordCodec.validateText(title,640,false);this.url=folder?NativeRecordCodec.validateText(url,0,true):web(url);this.folder=folder;}
        public String id(){return id;}public String parentId(){return parent;}public String title(){return title;}public String url(){return url;}public boolean folder(){return folder;}
    }
    public static final class Snapshot {
        private final long revision;private final List<Node> nodes;private final Map<String,Node> index;
        public Snapshot(long metadataRevision,List<Node> nodes){if(metadataRevision<0 || nodes==null || nodes.size()>MAX_NODES)throw bad();revision=metadataRevision;List<Node> owned=new ArrayList<>(nodes);Map<String,Node> byId=new LinkedHashMap<>();for(Node n:owned)if(n==null || byId.put(n.id,n)!=null)throw bad();
            for(Node n:owned){Set<String> path=new HashSet<>();Node p=n;int depth=0;while(p!=null){if(++depth>MAX_DEPTH || !path.add(p.id))throw bad();if(p.parent==null)break;p=byId.get(p.parent);if(p==null || !p.folder)throw bad();}}
            this.nodes=Collections.unmodifiableList(owned);index=Collections.unmodifiableMap(byId);
            // Bound aggregate encoded size too; count and individual bounds alone are insufficient.
            long bytes=20;for(Node n:owned)bytes+=2+35+(n.parent==null?1:38)+1+2+utfBytes(n.title)+2+utfBytes(n.url);if(bytes>MAX_BYTES)throw bad();
        }
        public long metadataRevision(){return revision;}public List<Node> nodes(){return nodes;}
        public Node require(String id){Node n=index.get(id);if(n==null)throw bad();return n;}
        public Snapshot add(String parent,String title,String url,boolean folder){if(nodes.size()>=MAX_NODES)throw bad();String id=null;for(int attempt=0;attempt<16;attempt++){String candidate=randomId();if(!index.containsKey(candidate)){id=candidate;break;}}if(id==null)throw bad();List<Node> copy=new ArrayList<>(nodes);copy.add(new Node(id,parent,title,url,folder));return new Snapshot(next(revision),copy);}
        public Snapshot edit(String id,String parent,String title,String url){Node before=require(id);List<Node> copy=new ArrayList<>(nodes);copy.set(copy.indexOf(before),new Node(id,parent,title,url,before.folder));return new Snapshot(next(revision),copy);}
        /** Explicit native delete intent; descendants removed, never an automatic repair. */
        public Snapshot removeSubtree(String id){require(id);Set<String> remove=new HashSet<>();remove.add(id);for(int round=0;round<MAX_DEPTH;round++){boolean added=false;for(Node n:nodes)if(remove.contains(n.parent))added|=remove.add(n.id);if(!added)break;}List<Node> copy=new ArrayList<>();for(Node n:nodes)if(!remove.contains(n.id))copy.add(n);return new Snapshot(next(revision),copy);}
        /** Literal bounded search; no regex, lowercasing or URL resolution beyond this supplied tree. */
        public List<Node> search(String query,int offset,int limit){NativeRecordCodec.validateText(query,256,true);if(offset<0 || offset>MAX_NODES || limit<1 || limit>100)throw bad();List<Node> out=new ArrayList<>();int seen=0;for(Node n:nodes)if(n.title.contains(query) || n.url.contains(query)){if(seen++>=offset && out.size()<limit)out.add(n);}return Collections.unmodifiableList(out);}
    }
    public static final NativeRecordCodec.Codec<Snapshot> CODEC=new NativeRecordCodec.Codec<Snapshot>(){
        @Override public Snapshot decode(ByteBuffer input){NativeRecordCodec.Reader r=new NativeRecordCodec.Reader(input,"RBBOOK01",MAX_BYTES);long revision=r.nonnegative();int count=r.u16();if(count>MAX_NODES)throw bad();List<Node> nodes=new ArrayList<>();for(int n=0;n<count;n++){String id=r.text(35,false),parent=r.bool()?r.text(35,false):null;boolean folder=r.bool();String title=r.text(640,false),url=r.text(8192,true);Node node=new Node(id,parent,title,url,folder);if(!node.url.equals(url))throw bad();nodes.add(node);}r.end();return new Snapshot(revision,nodes);}
        @Override public byte[] encode(Snapshot s){if(s==null)throw bad();NativeRecordCodec.Writer w=new NativeRecordCodec.Writer("RBBOOK01",MAX_BYTES).nonnegative(s.revision).u16(s.nodes.size());for(Node n:s.nodes){w.text(n.id,35,false).bool(n.parent!=null);if(n.parent!=null)w.text(n.parent,35,false);w.bool(n.folder).text(n.title,640,false).text(n.url,8192,true);}return w.finish();}
    };
    private final OwnedDataCatalog records;
    public BookmarkStore(OwnedDataCatalog nativeRecords){if(nativeRecords==null)throw bad();records=nativeRecords;}
    public OwnedDataCatalog.View<Snapshot> read(ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{return records.read(OwnedDataCatalog.Family.BOOKMARKS,CODEC,permit);}
    public long compareAndWrite(OwnedDataCatalog.View<Snapshot> expected,Snapshot intended,ComponentGate.BusinessPermit permit)throws IOException,GeneralSecurityException{records.requireFamily(expected,OwnedDataCatalog.Family.BOOKMARKS,CODEC);if(intended==null || expected.kind()==io.github.rebrowser.data.PrivateRecordStore.Kind.CURRENT && intended.revision<=expected.value(permit).revision)throw bad();return records.compareAndWrite(expected,intended,permit);}
    private static String web(String value){NativeRecordCodec.validateText(value,8192,false);String normalized=SearchEngines.requireWebUrl(value).url();NativeRecordCodec.validateText(normalized,8192,false);return normalized;}
    private static boolean id(String s){return s!=null && s.matches("bm_[0-9a-f]{32}");}
    private static int utfBytes(String s){return s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;}
    private static long next(long n){if(n==Long.MAX_VALUE)throw bad();return n+1;}
    private static String randomId(){byte[] b=new byte[16];new SecureRandom().nextBytes(b);StringBuilder s=new StringBuilder("bm_");for(byte v:b)s.append(Character.forDigit((v&255)>>>4,16)).append(Character.forDigit(v&15,16));return s.toString();}
    private static IllegalArgumentException bad(){return NativeRecordCodec.bad();}
}
