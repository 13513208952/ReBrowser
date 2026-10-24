package io.github.rebrowser.continuity;

import android.app.Application;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import io.github.rebrowser.bootstrap.StartupCoordinator;
import io.github.rebrowser.crypto.DataKeyVault;
import io.github.rebrowser.crypto.InstallKeyStore;
import io.github.rebrowser.crypto.PrivateRecordSealer;
import io.github.rebrowser.browser.HistoryStore;
import io.github.rebrowser.browser.BookmarkStore;
import io.github.rebrowser.browser.SearchEngines;
import io.github.rebrowser.settings.SettingsRepository;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.lang.ref.WeakReference;
import android.os.SystemClock;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exact native source resume plus explicit cold first setup. Existing inspection NEVER seeds,
 * repairs or erases. New setup requires positive empty inventory AND native user consent;
 * initial keys/records finish before the STABLE ledger, then independent normal inspection. */
public final class SourceBootstrap {
    public enum Status { UNCONFIGURED, MISSING_BASELINE, UNCERTAIN_METADATA, RECOVERY_REQUIRED, SOURCE_MISMATCH, ACCEPTED_STABLE, UNAVAILABLE }
    public static final long MAX_TRANSFER_AGE_MS=30_000;
    public static final class AcceptedContext {
        private final byte[] epoch;private final String signer,root;
        private AcceptedContext(LastGoodSourceStore.SourceEpoch baseline){epoch=baseline.dataEpoch();signer=baseline.signingSha256();root=baseline.rootSha256();}
        public byte[] dataEpoch(){return epoch.clone();}
        public String signingSha256(){return signer;}
        public String rootSha256(){return root;}
    }
    public static final class Inspection {
        private final Object application;private final Status status;private final long started;
        private final AcceptedContext accepted;private final AtomicBoolean transferred=new AtomicBoolean();
        private Inspection(Object application,Status status,long started,AcceptedContext accepted){this.application=application;this.status=status;this.started=started;this.accepted=accepted;}
        public Status status(){return status;}
        /** Exact own native app, monotonic <=30s, one attempt spent BEFORE handoff; not IPC input. */
        public AcceptedContext takeAccepted(Application nativeApplication){return transfer(nativeApplication,SystemClock.elapsedRealtime());}
        private AcceptedContext transfer(Object nativeApplication,long now){
            if(!transferred.compareAndSet(false,true))throw new SecurityException("Native source inspection already transferred");
            if(application!=nativeApplication || accepted==null || status!=Status.ACCEPTED_STABLE || now<started || now-started>MAX_TRANSFER_AGE_MS)throw new SecurityException("Native source inspection expired/unavailable");
            return accepted;
        }
    }
    /** No PM/filesystem IO when compiled Root missing. Directory/store always native-owned, not IPC. */
    public static Inspection inspectOwn(Application nativeApplication,LastGoodSourceStore nativeStore){
        CompiledSourceIdentity root=CompiledSourceIdentity.current();
        if(!root.configured())return new Inspection(nativeApplication,Status.UNCONFIGURED,0,null);
        // Configuring a public Root is not source acceptance. Missing native context must fail
        // without touching Android PM/clock/filesystem, not crash or infer first installation.
        if(nativeApplication==null || nativeStore==null)return new Inspection(nativeApplication,Status.UNAVAILABLE,0,null);
        long start=SystemClock.elapsedRealtime();
        try{
            nativeStore.requireNativeBootstrapPath(nativeApplication.getDataDir().toPath().resolve("no_backup").resolve(LastGoodSourceStore.BOOTSTRAP_DIRECTORY));
            SigningIdentity.OwnInspection before=SigningIdentity.inspectOwnForContinuity(nativeApplication);
            LastGoodSourceStore.View view=nativeStore.inspect();
            SigningIdentity.OwnInspection after=SigningIdentity.inspectOwnForContinuity(nativeApplication);
            LastGoodSourceStore.ReadReceipt exact=nativeStore.confirmUnchanged(view);
            return evaluateNative(nativeApplication,root,before,after,exact,start,SystemClock.elapsedRealtime());
        }catch(SigningIdentity.Unavailable|IOException|RuntimeException unavailable){return new Inspection(nativeApplication,Status.UNAVAILABLE,start,null);}
    }
    /** PRIVATE native provenance composition; reflected SYNTHETIC fixtures are NOT OS evidence. */
    private static Inspection evaluateNative(Object app,CompiledSourceIdentity root,SigningIdentity.OwnInspection before,SigningIdentity.OwnInspection after,LastGoodSourceStore.ReadReceipt exact,long start,long now){
        if(app==null || root==null || !root.configured())return new Inspection(app,Status.UNCONFIGURED,start,null);
        if(start<0 || now<start || now-start>MAX_TRANSFER_AGE_MS || before==null || after==null || !before.belongsTo(app) || !after.belongsTo(app) || !before.sameInstallation(after) || exact==null)return new Inspection(app,Status.UNAVAILABLE,start,null);
        LastGoodSourceStore.View view=exact.snapshot();
        if(view.kind()==LastGoodSourceStore.Kind.MISSING)return new Inspection(app,Status.MISSING_BASELINE,start,null);
        if(view.kind()!=LastGoodSourceStore.Kind.CURRENT || !view.hasBaselineMetadata())return new Inspection(app,Status.UNCERTAIN_METADATA,start,null);
        LastGoodSourceStore.Record record=view.record();
        if(record.phase()!=LastGoodSourceStore.Phase.STABLE)return new Inspection(app,Status.RECOVERY_REQUIRED,start,null);
        LastGoodSourceStore.SourceEpoch baseline=record.lastGood();
        // Actual current signer only. Android lineage membership alone needs explicit rescue path.
        if(root.compareBaselineRoot(baseline)!=CompiledSourceIdentity.RootRelation.MATCH || !after.identity().currentCertificateSha256().equals(baseline.signingSha256()))return new Inspection(app,Status.SOURCE_MISMATCH,start,null);
        return new Inspection(app,Status.ACCEPTED_STABLE,start,new AcceptedContext(baseline));
    }
    public interface FirstSetupCallback {void finished(Inspection inspection,String explanation);}
    /** Private actual native positive-button provenance, not a caller boolean or owner-auth grant.
     * Its lifetime ends at source publication; it never authorizes existing-data reads/enrollment. */
    public static final class FreshConsent {
        private final StartupCoordinator.FirstSetupFence fence;private final Application app;private final SigningIdentity.OwnInspection signer;
        private final String root;private final byte[] epoch=new byte[16];private final long started;
        private final Path directory;private final Map<Path,Object> directories=new LinkedHashMap<>();private final Map<Path,byte[]> records=new LinkedHashMap<>();
        private final AtomicBoolean installationAttempt=new AtomicBoolean(),wrappingAttempt=new AtomicBoolean();
        private FreshConsent(Proposal proposal)throws Exception{fence=proposal.fence;app=fence.nativeApplication();signer=proposal.signer;root=proposal.root;directory=proposal.directory;started=SystemClock.elapsedRealtime();new SecureRandom().nextBytes(epoch);proposal.requireUnchanged();directories.putAll(proposal.empty);}
        public byte[] dataEpoch(){requireCurrent();return epoch.clone();}
        public void beginInstallationKeyAttempt(){requireCurrent();if(!installationAttempt.compareAndSet(false,true))throw new SecurityException("Installation generation already attempted");}
        public void beginWrappingKeyAttempt(){requireCurrent();if(!wrappingAttempt.compareAndSet(false,true))throw new SecurityException("Wrapping generation already attempted");}
        public void requireCurrent(){
            try{fence.requireStillCold(app);long now=SystemClock.elapsedRealtime();if(now<started || now-started>120_000 || !root.equals(CompiledSourceIdentity.current().requireRootSpkiSha256()) || !signer.sameInstallation(SigningIdentity.inspectOwnForContinuity(app)))throw new SecurityException("Initial source changed/expired");for(Map.Entry<Path,Object> d:directories.entrySet())requireDirectory(d.getKey(),d.getValue());}
            catch(IOException|SigningIdentity.Unavailable failure){throw new SecurityException("Initial native context unavailable",failure);}
        }
        private Path addDirectory(Path path)throws IOException{requireCurrent();Files.createDirectory(path,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));directories.put(path,directoryKey(path));forceDirectory(path.getParent());requireCurrent();return path;}
        private void requirePrepared()throws IOException{
            requireCurrent();Map<Path,Object> top=inspectEmptyRoot(directory,true,signer.nativePackageLastUpdateTime());for(Map.Entry<Path,Object> before:directories.entrySet())if(before.getKey().getParent()!=null && before.getKey().getParent().equals(directory) && !Objects.equals(top.get(before.getKey()),before.getValue()))throw new IOException("Initial root namespace changed");
            Path noBackup=directory.resolve("no_backup"),bootstrap=noBackup.resolve(LastGoodSourceStore.BOOTSTRAP_DIRECTORY);
            for(Path parent:directories.keySet())if(parent.startsWith(noBackup))try(DirectoryStream<Path> children=Files.newDirectoryStream(parent)){int count=0;for(Path child:children){if(++count>16)throw new IOException("Initial namespace exceeds bound");if(directories.containsKey(child)){requireDirectory(child,directories.get(child));continue;}if(records.containsKey(child)){byte[] actual=readExactSmall(child,64*1024+DataKeyVault.ENVELOPE_OVERHEAD);if(!MessageDigest.isEqual(records.get(child),digest(actual)))throw new IOException("Initial record changed");continue;}String name=child.getFileName().toString();if(parent.equals(bootstrap) && (name.equals("source-continuity.lock") || name.equals("source-continuity.pending") || name.equals("source-continuity.v1"))){BasicFileAttributes a=attributes(child);if(!a.isRegularFile() || a.fileKey()==null || a.size()>(name.endsWith(".lock")?0:LastGoodSourceStore.FRAME_BYTES))throw new IOException("Unsafe initial source metadata");continue;}throw new IOException("Unrecognized initial namespace entry");}}
            if(records.size()!=3)throw new IOException("Initial browser records incomplete");for(Map.Entry<Path,byte[]> file:records.entrySet())if(!MessageDigest.isEqual(file.getValue(),digest(readExactSmall(file.getKey(),64*1024+DataKeyVault.ENVELOPE_OVERHEAD))))throw new IOException("Initial readback changed");
        }
    }
    private static final class Proposal {
        final StartupCoordinator.FirstSetupFence fence;final Path directory;final Map<Path,Object> empty;final SigningIdentity.OwnInspection signer;final String root;final long started;
        Proposal(StartupCoordinator.FirstSetupFence fence)throws Exception{this.fence=fence;Application app=fence.nativeApplication();fence.requireStillCold(app);started=SystemClock.elapsedRealtime();root=CompiledSourceIdentity.current().requireRootSpkiSha256();signer=SigningIdentity.inspectOwnForContinuity(app);directory=app.getDataDir().toPath().toAbsolutePath().normalize();empty=inspectEmptyRoot(directory,false,signer.nativePackageLastUpdateTime());InstallKeyStore.requireAbsentForFirstSetup(fence);requireUnchanged();}
        void requireUnchanged()throws Exception{Application app=fence.nativeApplication();fence.requireStillCold(app);long now=SystemClock.elapsedRealtime();if(now<started || now-started>300_000 || !root.equals(CompiledSourceIdentity.current().requireRootSpkiSha256()) || !signer.sameInstallation(SigningIdentity.inspectOwnForContinuity(app)) || !empty.equals(inspectEmptyRoot(directory,false,signer.nativePackageLastUpdateTime())))throw new SecurityException("First-setup proposal changed/expired");InstallKeyStore.requireAbsentForFirstSetup(fence);}
    }
    /** Actual native first-setup entry. Read-only inventory precedes the fixed dialog; cancellation
     * does not create dirs/keys/records. The private consent is made ONLY by its positive button.
     * No URI/extras/caller epoch/Root/default record can enter this operation. */
    public static void requestFirstSetup(Activity activity,StartupCoordinator.FirstSetupFence fence,FirstSetupCallback callback){
        if(activity==null || fence==null || callback==null || Looper.myLooper()!=Looper.getMainLooper() || activity.getApplication()!=fence.nativeApplication() || activity.isFinishing() || activity.isDestroyed() || !activity.hasWindowFocus())throw new SecurityException("Foreground native first-setup window required");
        fence.requireStillCold(activity.getApplication());Handler main=new Handler(Looper.getMainLooper());WeakReference<Activity> window=new WeakReference<>(activity);ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,15,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(1),r->{Thread t=new Thread(r,"ReBrowser-first-setup");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());io.allowCoreThreadTimeOut(true);
        try{io.execute(()->{
            try{Proposal proposal=new Proposal(fence);postVerified(main,()->showFirstSetupDialog(window,proposal,io,main,callback));}
            catch(Exception unavailable){io.shutdown();android.util.Log.w("ReBrowserFirstSetup","inventory unavailable: "+unavailable.getClass().getSimpleName());postVerified(main,()->callback.finished(null,"不是可确认的空白安装，或原生检查不可用；未创建或覆盖数据"));}
        });}catch(RejectedExecutionException knownUnsubmitted){io.shutdown();callback.finished(null,"检查任务未提交");}
    }
    private static void showFirstSetupDialog(WeakReference<Activity> window,Proposal proposal,ThreadPoolExecutor io,Handler main,FirstSetupCallback callback){
        Activity owner=window.get();
        if(owner==null || owner.isFinishing() || owner.isDestroyed() || !owner.hasWindowFocus()){io.shutdown();callback.finished(null,"初始化确认窗口已离开；未创建数据");return;}
        AtomicBoolean decided=new AtomicBoolean();
        Runnable cancel=()->{if(decided.compareAndSet(false,true)){io.shutdown();callback.finished(null,"已取消；未创建数据");}};
        try{
            AlertDialog dialog=new AlertDialog.Builder(owner).setTitle("首次初始化 ReBrowser")
                .setMessage("已核对未发现本应用业务数据且没有安装密钥（允许已核验的依赖编译标记）。\n\n将创建新的安装密钥、加密数据及初始浏览器配置，不导入或覆盖旧数据，也不登记管理员设备名单。\n\n应用签名 SHA-256：\n"+proposal.signer.identity().currentCertificateSha256()+"\n\nRoot 公钥 SHA-256：\n"+proposal.root)
                .setNegativeButton("取消",(d,w)->cancel.run())
                .setPositiveButton("确认初始化",(d,w)->{
                    if(!decided.compareAndSet(false,true))return;
                    try{io.execute(()->{
                        Inspection accepted=null;
                        try{accepted=initializeOnIo(proposal);}
                        catch(Exception failure){android.util.Log.w("ReBrowserFirstSetup","creation unknown/locked: "+failure.getClass().getSimpleName());}
                        final Inspection result=accepted;io.shutdown();
                        postVerified(main,()->callback.finished(result,result==null?"初始化未确认；保留现状，未重试、重置或解锁":"初始化写入及独立来源检查完成"));
                    });}catch(RejectedExecutionException knownUnsubmitted){io.shutdown();callback.finished(null,"初始化任务未提交；未创建数据");}
                    catch(RuntimeException unknown){io.shutdown();/* unknown submission stays locked/pending, no second job */}
                }).create();
            dialog.setOnCancelListener(d->cancel.run());dialog.show();
        }catch(RuntimeException windowGone){if(decided.compareAndSet(false,true)){io.shutdown();callback.finished(null,"初始化确认窗口已失效；未创建数据");}}
    }
    /** Synchronize ONLY the Handler enqueue/return, never crypto/filesystem IO. An actually
     * delivered callback must also observe positive post return; unknown enqueue cannot accept. */
    private static void postVerified(Handler main,Runnable callback){Object admission=new Object();boolean[] confirmed={false};synchronized(admission){try{confirmed[0]=main.post(()->{synchronized(admission){if(!confirmed[0])return;}callback.run();});}catch(RuntimeException unknown){/* no handoff retry or source publication */}}}
    private static Inspection initializeOnIo(Proposal proposal)throws Exception{
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Initial data IO lane required");FreshConsent consent=new FreshConsent(proposal);Path root=consent.directory,noBackup=root.resolve("no_backup");if(!consent.directories.containsKey(noBackup))consent.addDirectory(noBackup);
        Path epochs=consent.addDirectory(noBackup.resolve("owned-epochs-v1")),epoch=consent.addDirectory(epochs.resolve(hex(consent.epoch))),records=consent.addDirectory(epoch.resolve("private-records-v1"));consent.addDirectory(epoch.resolve("mail-objects-v1"));consent.addDirectory(epoch.resolve("apk-staging-v1"));Path bootstrap=consent.addDirectory(noBackup.resolve(LastGoodSourceStore.BOOTSTRAP_DIRECTORY));
        InstallKeyStore.createForFirstSetup(consent);DataKeyVault.InitialKey key=DataKeyVault.createForFirstSetup(consent);
        writeInitial(consent,key,records,PrivateRecordSealer.Purpose.SETTINGS,"RB-SETTINGS-0001",SettingsRepository.CODEC.encode(new SettingsRepository.Snapshot(0,SearchEngines.Provider.BING,false,false)));
        writeInitial(consent,key,records,PrivateRecordSealer.Purpose.HISTORY,"RB-HISTORY-00001",HistoryStore.CODEC.encode(new HistoryStore.Snapshot(0,false,Collections.emptyList())));
        writeInitial(consent,key,records,PrivateRecordSealer.Purpose.BOOKMARKS,"RB-BOOKMARKS-001",BookmarkStore.CODEC.encode(new BookmarkStore.Snapshot(0,Collections.emptyList())));
        consent.requirePrepared();LastGoodSourceStore store=new LastGoodSourceStore(bootstrap);LastGoodSourceStore.View missing=store.inspect();LastGoodSourceStore.SourceEpoch source=new LastGoodSourceStore.SourceEpoch(proposal.signer.identity().currentCertificateSha256(),proposal.root,consent.epoch);
        LastGoodSourceStore.View committed=store.seedVerifiedFresh(missing,source,(expected,replacement)->{consent.requirePrepared();if(expected.kind()!=LastGoodSourceStore.Kind.MISSING || replacement==null || replacement.phase()!=LastGoodSourceStore.Phase.STABLE || replacement.revision()!=1 || !source.signingSha256().equals(replacement.lastGood().signingSha256()) || !source.rootSha256().equals(replacement.lastGood().rootSha256()) || !Arrays.equals(source.dataEpoch(),replacement.lastGood().dataEpoch()))throw new IOException("Unexpected initial source replacement");});
        consent.requirePrepared();store.confirmUnchanged(committed);Inspection inspection=inspectOwn(proposal.fence.nativeApplication(),store);if(inspection.status()!=Status.ACCEPTED_STABLE)throw new IOException("Independent initial source admission unavailable");return inspection;
    }
    private static void writeInitial(FreshConsent consent,DataKeyVault.InitialKey key,Path directory,PrivateRecordSealer.Purpose purpose,String scopeText,byte[] plaintext)throws Exception{
        byte[] sealed=null;try{consent.requireCurrent();byte[] scope=scopeText.getBytes(StandardCharsets.US_ASCII);sealed=key.sealInitial(new PrivateRecordSealer.Binding(purpose,consent.epoch,scope,1),plaintext);Path file=directory.resolve("p"+purpose.code()+"_"+hex(scope)+".v1");
            try(FileChannel out=FileChannel.open(file,new HashSet<OpenOption>(Arrays.asList(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))){ByteBuffer bytes=ByteBuffer.wrap(sealed);while(bytes.hasRemaining())if(out.write(bytes)<=0)throw new IOException("No initial record write progress");out.force(true);}forceDirectory(directory);byte[] readback=readExactSmall(file,64*1024+DataKeyVault.ENVELOPE_OVERHEAD);if(!MessageDigest.isEqual(sealed,readback))throw new IOException("Initial record readback mismatch");consent.records.put(file,digest(readback));consent.requireCurrent();
        }finally{Arrays.fill(plaintext,(byte)0);if(sealed!=null)Arrays.fill(sealed,(byte)0);}
    }
    /** Full bounded inventory, not just missing owned marker/key. Unknown, nonempty or linked
     * directories block first setup; ART/cache exceptions require actual platform qualification. */
    static Map<Path,Object> inspectEmptyRoot(Path root,boolean prepared)throws IOException{return inspectEmptyRoot(root,prepared,0);}
    static Map<Path,Object> inspectEmptyRoot(Path root,boolean prepared,long nativePackageUpdated)throws IOException{
        Map<Path,Object> result=new LinkedHashMap<>();result.put(root,directoryKey(root));Set<String> allowed=new HashSet<>(Arrays.asList("cache","code_cache","files","no_backup","shared_prefs","databases"));
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(root)){int count=0;for(Path child:entries){if(++count>16 || !allowed.contains(child.getFileName().toString()))throw new IOException("Unknown initial application entry");result.put(child,directoryKey(child));if(prepared && child.getFileName().toString().equals("no_backup"))continue;try(DirectoryStream<Path> contents=Files.newDirectoryStream(child)){int n=0;for(Path item:contents){if(++n>1 || !child.getFileName().toString().equals("files") || !item.getFileName().toString().equals("profileInstalled") || nativePackageUpdated<1 || !validProfileInstallerMarker(readExactSmall(item,24),nativePackageUpdated))throw new IOException("Existing application contents require recovery, not initialization");}}}}return result;
    }
    /** AndroidX ProfileInstaller1.4.0's exact non-business verification cache: schema/result/
     * actual native package lastUpdateTime/current ART profile size, DataOutputStream big-endian.
     * Verified against the pinned dependency bytecode + actual fresh Android15 installation.
     * No generic files/cache exemption and no name-only/size-only fresh-install proof. */
    static boolean validProfileInstallerMarker(byte[] bytes,long nativeUpdated){if(bytes==null || bytes.length!=24 || nativeUpdated<1)return false;ByteBuffer in=ByteBuffer.wrap(bytes);int schema=in.getInt(),result=in.getInt();long updated=in.getLong(),size=in.getLong();return schema==1 && (result>=0 && result<=3 || result>=65536 && result<=327680 && result%65536==0) && updated==nativeUpdated && size>=0 && size<=64L*1024*1024;}
    private static BasicFileAttributes attributes(Path path)throws IOException{return Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
    private static Object directoryKey(Path path)throws IOException{BasicFileAttributes a=attributes(path);if(!a.isDirectory() || a.fileKey()==null)throw new IOException("Identifiable native directory required");return a.fileKey();}
    private static void requireDirectory(Path path,Object key)throws IOException{if(!Objects.equals(key,directoryKey(path)))throw new IOException("Native initial directory changed");}
    private static void forceDirectory(Path directory)throws IOException{try(FileChannel channel=FileChannel.open(directory,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){channel.force(true);}}
    static byte[] readExactSmall(Path file,int maximum)throws IOException{
        BasicFileAttributes before=attributes(file);if(!before.isRegularFile() || before.fileKey()==null || before.size()>maximum)throw new IOException("Unsafe initial record");ByteBuffer bytes=ByteBuffer.allocate((int)before.size()+1);try(FileChannel input=FileChannel.open(file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){while(bytes.hasRemaining()){int n=input.read(bytes);if(n<0)break;if(n==0)throw new IOException("No initial record read progress");}}BasicFileAttributes after=attributes(file);if(bytes.position()!=before.size() || after.size()!=before.size() || !before.fileKey().equals(after.fileKey()))throw new IOException("Initial record changed while reading");return Arrays.copyOf(bytes.array(),bytes.position());
    }
    private static byte[] digest(byte[] bytes){try{return MessageDigest.getInstance("SHA-256").digest(bytes);}catch(java.security.NoSuchAlgorithmException unavailable){throw new IllegalStateException(unavailable);}}
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append("0123456789abcdef".charAt((b&255)>>>4)).append("0123456789abcdef".charAt(b&15));return out.toString();}
    private SourceBootstrap(){}
}
