#!/usr/bin/env python3
"""Run four exact pinned Java method bodies with synthetic LocationManager classes.

NOT the Android class, JNI/native Gecko, OS permission, provider or site geolocation.
Only source method bodies are copied to an owned temporary harness; no live API calls.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile

EXPECTED = 'f17191faae943bdf970d4b3276c855bbbb38c0b50ae51cec7d47f9170181137e'
METHODS = [
    'private static float getLocationAccuracy(',
    'private static Location determineReliableLocation(',
    'private static @Nullable Location getLastKnownLocation(',
    'private static synchronized boolean updateLocationListeners('
]


def extract(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    depth = 1
    at = opening + 1
    while depth:
        depth += (source[at] == '{') - (source[at] == '}')
        at += 1
    return source[start:at].replace('@NotNull ', '').replace('@Nullable ', '')


HARNESS = r'''
import java.util.*;
public final class SyntheticLocationSourceProbe {
    static int checks;
    static void check(boolean b) { if(!b)throw new AssertionError("check "+checks);checks++; }
    static boolean locationListeningRequested, locationPaused, locationHighAccuracyEnabled;
    static LocationManager manager;
    static AndroidListeners sAndroidListeners;
    static Object getApplicationContext(){return null;}
    static LocationManager getLocationManager(Object unused){return manager;}
    static final class Location {
        final float accuracy;final long nanos;final boolean has;
        Location(float a,long t,boolean h){accuracy=a;nanos=t;has=h;}
        float getAccuracy(){return accuracy;}boolean hasAccuracy(){return has;}
        long getElapsedRealtimeNanos(){return nanos;}
    }
    static final class Criteria {
        static final int ACCURACY_FINE=1,ACCURACY_COARSE=2;int accuracy;
        void setSpeedRequired(boolean b){} void setBearingRequired(boolean b){} void setAltitudeRequired(boolean b){}
        void setAccuracy(int a){accuracy=a;}
    }
    static final class Looper {static Looper getMainLooper(){return new Looper();}}
    static final class AndroidListeners {
        final List<Location> published=new ArrayList<>();
        void onLocationChanged(Location l){published.add(l);}
    }
    static final class LocationManager {
        static final String GPS_PROVIDER="gps",NETWORK_PROVIDER="network";
        final Map<String,Location> cached=new LinkedHashMap<>();
        boolean listenerActive=true;int removes,requests,chosenAccuracy;String requestProvider;long minMs;float minMeters;
        List<String> getAllProviders(){return new ArrayList<>(cached.keySet());}
        Location getLastKnownLocation(String provider){return cached.get(provider);}
        boolean isProviderEnabled(String provider){return true;}
        void removeUpdates(AndroidListeners listener){removes++;listenerActive=false;}
        String getBestProvider(Criteria c,boolean enabled){chosenAccuracy=c.accuracy;return c.accuracy==Criteria.ACCURACY_COARSE?NETWORK_PROVIDER:GPS_PROVIDER;}
        void requestLocationUpdates(String provider,long ms,float meters,AndroidListeners listener,Looper loop){requests++;requestProvider=provider;minMs=ms;minMeters=meters;listenerActive=true;}
    }
    static void reset(){manager=new LocationManager();sAndroidListeners=new AndroidListeners();locationListeningRequested=true;locationPaused=false;locationHighAccuracyEnabled=false;}
    // PINNED SOURCE METHODS INSERTED BELOW: only nullability annotations removed.
    __METHODS__
    public static void main(String[] args) {
        reset();Location gps=new Location(3,1000000000L,true),network=new Location(1500,1000000000L,true);
        manager.cached.put(LocationManager.GPS_PROVIDER,gps);manager.cached.put(LocationManager.NETWORK_PROVIDER,network);
        check(updateLocationListeners());check(sAndroidListeners.published.size()==1);
        check(sAndroidListeners.published.get(0)==gps); // raw most-accurate cache object before coarse provider choice
        check(manager.chosenAccuracy==Criteria.ACCURACY_COARSE);
        check(manager.requestProvider.equals(LocationManager.NETWORK_PROVIDER));
        check(manager.minMs==100);check(manager.minMeters==0.5f);
        check(getLocationAccuracy(new Location(0,0,false))==1001);
        reset();locationPaused=true;
        check(updateLocationListeners());check(manager.removes==0);check(manager.requests==0);check(manager.listenerActive);
        locationListeningRequested=false;updateLocationListeners();check(manager.removes==1);check(!manager.listenerActive);
        reset();locationHighAccuracyEnabled=true;updateLocationListeners();check(manager.chosenAccuracy==Criteria.ACCURACY_FINE);check(manager.requestProvider.equals(LocationManager.GPS_PROVIDER));
        System.out.println(checks);
    }
}
'''


def run(args):
    raw = args.source.read_bytes()
    assert hashlib.sha256(raw).hexdigest() == EXPECTED
    work = args.work_dir.resolve()
    assert work.is_dir() and not work.is_relative_to(Path('/tmp'))
    assert shutil.disk_usage(work).free >= 16 * 1024**2 + 1024**3
    assert int(next(l.split()[1] for l in Path('/proc/meminfo').read_text().splitlines()
                    if l.startswith('MemAvailable:'))) * 1024 >= 3 * 1024**3
    bodies = [extract(raw.decode('utf-8'), method) for method in METHODS]
    with tempfile.TemporaryDirectory(prefix='geo-source-host-', dir=work) as target:
        java = Path(target)/'SyntheticLocationSourceProbe.java'
        java.write_text(HARNESS.replace('__METHODS__', '\n'.join(bodies)))
        opts = dict(check=True, capture_output=True, text=True, timeout=30)
        subprocess.run([str(args.java_home/'bin/javac'),'-J-Xmx256m','-J-Djava.io.tmpdir='+str(work),
                        '--release','8','-d',target,str(java)], **opts)
        output = subprocess.run([str(args.java_home/'bin/java'),'-Xmx96m','-Djava.io.tmpdir='+str(work),
                                 '-cp',target,'SyntheticLocationSourceProbe'], **opts)
    assert output.stdout.strip() == '16'
    return {
        'scope':'HOST source-method exercise with synthetic Location/LocationManager; NOT actual Android class/JNI/Gecko/OS provider/site behavior',
        'coordinate':'org.mozilla.geckoview:geckoview:158.0.20261007201254',
        'sourcePath':'org/mozilla/gecko/GeckoAppShell.java', 'sourceSha256':EXPECTED,
        'methods':METHODS,'checks':16,'fixtureSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'javaMethodBodySha256':[hashlib.sha256(s.encode()).hexdigest() for s in bodies],
        'coarseCriteriaDidNotPreventMostAccurateSyntheticCachedObjectPublication':True,
        'pausedStillRequestedBranchDidNotRemoveSyntheticExistingListener':True,
        'providerSelectionIsNotCoordinateRedaction':True,
        'actualSensitiveApiAccess':False,'actualNativeGeckoPrecisionObserved':False,
        'nativeDownstreamPerRequestRedactionInspected':False,'livePermissionGrantedOrRevoked':False,
        'temporaryHarnessRemoved':True,
        'consequence':'Do not equate enableHighAccuracy=false or pauseLocation with per-site approximate privacy or acknowledged active-use termination. Native/OS downstream still requires inspection and separately scoped validation.'
    }


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path,required=True)
    parser.add_argument('--java-home',type=Path,required=True)
    parser.add_argument('--work-dir',type=Path,required=True)
    print(json.dumps(run(parser.parse_args()),ensure_ascii=False,indent=2))
