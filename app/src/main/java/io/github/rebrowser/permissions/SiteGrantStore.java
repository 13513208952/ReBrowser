package io.github.rebrowser.permissions;

import io.github.rebrowser.bootstrap.ComponentGate;
import java.net.URI;
import java.util.*;
import java.util.function.LongSupplier;

/** Process-local native origin/partition decision HINTS with checked expiry. NOT persisted Gecko
 * permissions, active camera/microphone/location grants or a guarantee that expiry stopped work.
 * No method automatically grants a request; PermissionCoordinator must repeat actual native
 * source/document/user/OS checks. Origin objects come only from the native permission adapter. */
public final class SiteGrantStore {
    public enum Capability { CAMERA, MICROPHONE, LOCATION, NOTIFICATIONS, STORAGE }
    public enum Choice { ASK, DENY, ALLOW_ONCE_INTENT }
    public static final class NativeOrigin {
        private final Object partition;private final String origin;
        private NativeOrigin(Object partition,String origin){this.partition=partition;this.origin=origin;}
        public String displayOrigin(){return origin;}
    }
    public static final class Decision {
        private final Choice choice;private final long expires,revision;
        private Decision(Choice choice,long expires,long revision){this.choice=choice;this.expires=expires;this.revision=revision;}
        public Choice rememberedChoice(){return choice;}public long expiresElapsedMs(){return expires;}public long revision(){return revision;}
    }
    private static final class Key {
        final NativeOrigin origin;final Capability capability;
        Key(NativeOrigin origin,Capability capability){this.origin=origin;this.capability=capability;}
        @Override public boolean equals(Object value){if(!(value instanceof Key))return false;Key k=(Key)value;return origin.partition==k.origin.partition && origin.origin.equals(k.origin.origin) && capability==k.capability;}
        @Override public int hashCode(){return 31*(31*System.identityHashCode(origin.partition)+origin.origin.hashCode())+capability.hashCode();}
    }
    private final ComponentGate.DataEpochPin source;private final LongSupplier clock;private final Map<Key,Decision> decisions=new LinkedHashMap<>();private long last=-1,revision;private boolean closed;
    public SiteGrantStore(ComponentGate.DataEpochPin nativeSource,LongSupplier elapsedRealtime){if(nativeSource==null || elapsedRealtime==null)throw new IllegalArgumentException("Native site-decision context required");source=nativeSource;clock=elapsedRealtime;source.requireCurrent();}
    static NativeOrigin fromNativeRequest(Object exactPartition,String nativeUri){if(exactPartition==null || nativeUri==null || nativeUri.length()>8192)throw denied();try{URI u=new URI(nativeUri);String scheme=u.getScheme(),host=u.getHost();if((!"https".equals(scheme) && !"http".equals(scheme)) || host==null || u.getRawUserInfo()!=null || u.getPort()< -1 || u.getPort()>65535)throw denied();String renderedHost=host.indexOf(':')>=0 && !host.startsWith("[")?"["+host+"]":host;int p=u.getPort();boolean standard=p<0 || scheme.equals("https")&&p==443 || scheme.equals("http")&&p==80;return new NativeOrigin(exactPartition,scheme+"://"+renderedHost.toLowerCase(Locale.ROOT)+(standard?"":":"+p));}catch(java.net.URISyntaxException invalid){throw denied();}}
    public synchronized Decision rememberNativeChoice(NativeOrigin origin,Capability capability,Choice choice,long lifetimeMs){long now=current();if(origin==null || capability==null || choice==null || lifetimeMs<1 || lifetimeMs>300_000 || now>Long.MAX_VALUE-lifetimeMs || revision==Long.MAX_VALUE)throw denied();prune(now);Key key=new Key(origin,capability);if(!decisions.containsKey(key) && decisions.size()>=512)throw new IllegalStateException("Native site-decision limit reached");Decision d=new Decision(choice,now+lifetimeMs,++revision);decisions.put(key,d);return d;}
    public synchronized Decision lookupHint(NativeOrigin origin,Capability capability){long now=current();if(origin==null || capability==null)throw denied();prune(now);return decisions.get(new Key(origin,capability));}
    public synchronized void forget(NativeOrigin origin,Capability capability){current();if(origin==null || capability==null)throw denied();decisions.remove(new Key(origin,capability));} // Not Gecko cache/media revocation.
    public synchronized void retirePartition(Object exactPartition){if(exactPartition==null)throw denied();Iterator<Key> keys=decisions.keySet().iterator();while(keys.hasNext())if(keys.next().origin.partition==exactPartition)keys.remove();}
    public synchronized void close(){closed=true;decisions.clear();}
    private void prune(long now){Iterator<Decision> entries=decisions.values().iterator();while(entries.hasNext())if(now>=entries.next().expires)entries.remove();}
    private long current(){source.requireCurrent();long now;try{now=clock.getAsLong();}catch(RuntimeException unknown){close();throw denied();}if(closed || now<0 || now<last){close();throw denied();}last=now;return now;}
    private static SecurityException denied(){return new SecurityException("Native site-decision hint unavailable");}
}
