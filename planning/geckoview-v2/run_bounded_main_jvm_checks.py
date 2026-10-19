#!/usr/bin/env python3
"""Bounded standalone main-source SDK TYPE compile/HOST tests, NOT AGP/Android runtime.

Fallback for a known small source increment when the >=3GiB Android-build RAM gate
is unavailable. JDK17 java.base + pinned SDK classpath is NOT Android bootclasspath.
No download/Gradle/JNI/dex/R8/APK/device/key provisioning. Owned temporary outputs only.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time

P = Path(__file__).resolve().parent
ROOT = P.parents[1]
HARNESS = r'''import org.junit.runner.*;
import org.junit.runner.notification.*;
import java.util.*;
public final class BoundedMainHostRunner {
 public static void main(String[] names)throws Exception {
  final Map<String,int[]> stats=new TreeMap<>();
  JUnitCore junit=new JUnitCore();
  junit.addListener(new RunListener(){
   private int[] row(Description d){return stats.computeIfAbsent(d.getClassName(),k->new int[2]);}
   public void testFinished(Description d){row(d)[0]++;}
   public void testFailure(Failure f){row(f.getDescription())[1]++;}
  });
  Class<?>[] classes=new Class<?>[names.length];for(int i=0;i<names.length;i++)classes[i]=Class.forName(names[i]);
  Result result=junit.run(classes);StringBuilder out=new StringBuilder("{\"tests\":").append(result.getRunCount()).append(",\"failures\":").append(result.getFailureCount()).append(",\"ignored\":").append(result.getIgnoreCount()).append(",\"classes\":[");
  boolean first=true;for(Map.Entry<String,int[]> e:stats.entrySet()){if(!first)out.append(',');first=false;out.append("{\"class\":\"").append(e.getKey()).append("\",\"tests\":").append(e.getValue()[0]).append(",\"failures\":").append(e.getValue()[1]).append(",\"errors\":0}");}
  out.append("]}");System.out.println(out);for(Failure failure:result.getFailures())System.err.println(failure.getTrace());if(!result.wasSuccessful() || result.getIgnoreCount()!=0)System.exit(1);
 }
}
'''


def sha(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def resources(work, minimum_ram_mib=2304):
    free = shutil.disk_usage(work).free
    memory = int(next(line.split()[1] for line in Path('/proc/meminfo').read_text().splitlines()
                      if line.startswith('MemAvailable:'))) * 1024
    assert free >= 16 * 1024**2 + 1024**3, 'standalone small-check disk gate'
    assert memory >= minimum_ram_mib * 1024**2, 'standalone small-check RAM gate; do not stop other processes'
    return {'freeBytes': free, 'memAvailableBytes': memory}


def next_sequential_admission(work, minimum_ram):
    # Prior owned JVM has exited; allow bounded kernel accounting/other-workload settling.
    # NEVER overlap JVMs, lower the selected gate or stop unrelated processes.
    deadline = time.monotonic()+20
    while True:
        try:
            return resources(work, minimum_ram)
        except AssertionError:
            if time.monotonic() >= deadline:
                raise
            time.sleep(0.5)


def run(args):
    work = args.work_dir.resolve()
    assert work.is_dir() and not work.is_relative_to(ROOT) and not work.is_relative_to(Path('/tmp'))
    minimum_ram = 2048 if args.compact else 2304
    before = resources(work, minimum_ram)
    dependencies = [args.android_jar, args.gecko_jar, args.annotation_jar, args.junit, args.hamcrest]
    assert all(p.is_file() for p in dependencies)
    baseline = json.loads((P/'evidence/production-android-api-compile.json').read_text())
    assert sha(args.android_jar) == baseline['androidJarSha256'], 'pinned SDK only'
    assert sha(args.gecko_jar) == 'f42db6ed0c09a2aa091d663ebae13df4ed3e0a1e8ad98555781754bb657ef049', 'exact classes.jar independently matched retained official Gecko AAR'
    main = sorted((ROOT/'app/src/main/java/io/github/rebrowser').rglob('*.java'))
    tests = sorted((ROOT/'app/src/test/java/io/github/rebrowser').rglob('*.java'))
    test_classes = ['.'.join(p.relative_to(ROOT/'app/src/test/java').with_suffix('').parts)
                    for p in tests if p.name.endswith('Test.java')]
    assert main and tests and all(re.fullmatch(r'[A-Za-z0-9_.]+', name) for name in test_classes)
    limits = (['-Xmx192m', '-XX:MaxMetaspaceSize=96m', '-XX:ReservedCodeCacheSize=48m'] if args.compact else ['-Xmx256m', '-XX:MaxMetaspaceSize=128m', '-XX:ReservedCodeCacheSize=64m']) + ['-XX:ActiveProcessorCount=2', '-Xss512k', '-Djava.io.tmpdir='+str(work)]
    java, javac = (args.java_home/'bin'/name for name in ['java', 'javac'])
    classpath = os.pathsep.join(str(p.resolve()) for p in dependencies)
    with tempfile.TemporaryDirectory(prefix='main-small-jvm-', dir=work) as temporary:
        temporary = Path(temporary)
        classes = temporary/'classes'
        classes.mkdir()
        harness = temporary/'BoundedMainHostRunner.java'
        harness.write_text(HARNESS)
        compiler = subprocess.run([str(javac), *['-J'+flag for flag in limits], '--release', '17', '-proc:none', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(classes), *map(str, main+tests+[harness,P/'experiments/DumpTaskJournalSchema.java'])], cwd=ROOT, text=True, capture_output=True, timeout=75)
        if compiler.returncode:
            raise RuntimeError('Standalone compile failed:\n'+compiler.stdout+compiler.stderr)
        middle = next_sequential_admission(work, minimum_ram)
        started = time.monotonic()
        result = subprocess.run([str(java), *limits, '-cp', str(classes)+os.pathsep+classpath,
                                 'BoundedMainHostRunner', *test_classes], cwd=ROOT, text=True,
                                capture_output=True, timeout=75)
        if result.returncode:
            raise RuntimeError('HOST tests failed:\n'+result.stdout+result.stderr)
        unit = json.loads(result.stdout)
        assert unit['failures'] == unit['ignored'] == 0
        assert sum(row['tests'] for row in unit['classes']) == unit['tests']
        all_classes = list((classes/'io/github/rebrowser').rglob('*.class'))
        main_names = {str(p.relative_to(ROOT/'app/src/main/java').with_suffix('')) for p in main}
        main_classes = sum(str(p.relative_to(classes)).removesuffix('.class').split('$')[0] in main_names for p in all_classes)
        # Additional real SDK BOOTCLASSPATH compile for the current changed native files.
        # The full main compile above intentionally uses JDK java.base, not Android boot.
        api_output = temporary/'changed-android-api'
        api_output.mkdir()
        changed = [ROOT/'app/src/main/java/io/github/rebrowser'/name for name in ['engine/NavigationDelegate.java','engine/GeckoSessionOwner.java','core/TaskCoordinator.java','core/ResourceBudgets.java','core/TaskJournal.java']]
        next_sequential_admission(work, minimum_ram)
        actual_api = subprocess.run([str(javac), *['-J'+flag for flag in limits], '-source', '8', '-target', '8', '-proc:none', '-encoding', 'UTF-8', '-bootclasspath', str(args.android_jar.resolve()), '-cp', str(classes)+os.pathsep+classpath, '-d', str(api_output), *map(str, changed)], cwd=ROOT, text=True, capture_output=True, timeout=45)
        if actual_api.returncode:
            raise RuntimeError('Changed SDK bootclasspath compile failed:\n'+actual_api.stdout+actual_api.stderr)
        changed_api = {'scope':'five changed native sources ACTUAL Android37.2 BOOTCLASSPATH compile; existing main ABI dependencies compiled standalone, NOT Android runtime/API30 qualification', 'sourceFiles':[{'path':str(p.relative_to(ROOT)),'sha256':sha(p)} for p in changed], 'classFiles':[{'path':str(p.relative_to(api_output)),'sha256':sha(p),'classMajor':int.from_bytes(p.read_bytes()[6:8],'big')} for p in sorted(api_output.rglob('*.class'))], 'diagnostics':actual_api.stderr, 'androidRuntimeExecuted':False}
        next_sequential_admission(work, minimum_ram)
        schema_result = subprocess.run([str(java),*limits,'-cp',str(classes)+os.pathsep+classpath,'DumpTaskJournalSchema'],cwd=ROOT,text=True,capture_output=True,timeout=30,check=True)
        assert len(schema_result.stdout) < 32768
        sql = [base64.urlsafe_b64decode(s+'='*((-len(s))%4)).decode() for s in schema_result.stdout.splitlines()]
        assert len(sql) == 9
        schema_sha = hashlib.sha256('\n'.join(sql).encode()).hexdigest()
        assert schema_sha == json.loads((P/'evidence/task-journal-host-sql.json').read_text())['ddlAndRecoverySha256'], 'no schema drift under historical SQL evidence'
        generated_bytes = sum(p.stat().st_size for p in temporary.rglob('*') if p.is_file())
        assert generated_bytes < 16 * 1024**2
    return {
        'scope': 'ALL current main Java + HOST tests compiled standalone --release17 against existing Android37.2/Gecko TYPE classpath; JDK java.base, NOT Android bootclasspath/AGP/Android runtime',
        'validationExecutor': 'bounded-standalone-javac-junit',
        'sourceFiles': [{'path':str(p.relative_to(ROOT)), 'sha256':sha(p)} for p in main+tests],
        'productionJavaSourceFiles':len(main), 'compiledMainClassFiles':main_classes, 'mainAndTestClassFiles':len(all_classes),
        'changedSourcesAndroidBootclasspathCompile':changed_api,
        'taskJournalCurrentCompiledSchemaSha256':schema_sha,
        'taskJournalSchemaExtractor':{'path':str((P/'experiments/DumpTaskJournalSchema.java').relative_to(ROOT)),'sha256':sha(P/'experiments/DumpTaskJournalSchema.java')},
        'historical235SqlChecksRerun':False,
        'unitTestsPassed':unit['tests'], 'unitTests':unit['classes'],
        'localDependencies':[{'name':p.name, 'sha256':sha(p)} for p in dependencies],
        'validationResourceProfile':'compact-192m' if args.compact else 'standard-256m', 'minimumMemAvailableMiB':minimum_ram,
        'jvmLimits':limits, 'resourceBeforeCompile':before, 'resourceBeforeTests':middle,
        'generatedBytesBeforeRemoval':generated_bytes, 'temporaryClassDirectoryRemoved':True,
        'hostAndAdditionalApiElapsedSeconds':round(time.monotonic()-started,3), 'compilerDiagnostics':compiler.stderr,
        'runnerSha256':sha(Path(__file__)), 'harnessSha256':hashlib.sha256(HARNESS.encode()).hexdigest(),
        'jdkJavaBaseNotAndroidBootclasspath':True, 'currentIncrementAgpTasksExecuted':False,
        'androidRuntimeExecuted':False, 'nativeMergeDexR8OrApkExecuted':False,
        'realDeviceKeysOrCredentialsUsed':False
    }


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ['java-home','android-jar','gecko-jar','annotation-jar','junit','hamcrest','work-dir']:
        parser.add_argument('--'+option, type=Path, required=True)
    parser.add_argument('--compact', action='store_true', help='smaller sequential JVM192m heap/96m meta/48m code, fresh RAM>=2048MiB; NOT Android-build gate')
    print(json.dumps(run(parser.parse_args()), ensure_ascii=False, indent=2))
