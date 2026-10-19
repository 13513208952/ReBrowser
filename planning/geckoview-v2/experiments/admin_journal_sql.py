#!/usr/bin/env python3
"""Actual production-class literal DDL on owned HOST SQLite; no Android DB/runtime claim."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import shutil
import sqlite3
import subprocess
import tempfile

P=Path(__file__).resolve().parents[1]
ROOT=P.parents[1]


def run(args):
    work=args.work_dir.resolve()
    assert work.is_dir() and not work.is_relative_to(ROOT) and not work.is_relative_to(Path('/tmp'))
    assert shutil.disk_usage(work).free>=16*1024**2+1024**3
    assert int(next(l.split()[1] for l in Path('/proc/meminfo').read_text().splitlines() if l.startswith('MemAvailable:')))*1024>=3*1024**3
    classes=ROOT/'rebrowser/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
    assert (classes/'io/github/rebrowser/control/IdempotencyStore.class').is_file()
    fixture=P/'experiments/DumpAdminJournalSchema.java'
    checks=0
    def yes(value):
        nonlocal checks
        assert value
        checks+=1
    with tempfile.TemporaryDirectory(prefix='admin-ledger-host-',dir=work) as target:
        opts=dict(check=True,text=True,capture_output=True,timeout=30)
        classpath=str(classes)+':'+str(args.android_jar.resolve())
        subprocess.run([str(args.java_home/'bin/javac'),'-J-Xmx256m','-J-Djava.io.tmpdir='+str(work),'--release','8','-cp',classpath,'-d',target,str(fixture)],**opts)
        result=subprocess.run([str(args.java_home/'bin/java'),'-Xmx96m','-Djava.io.tmpdir='+str(work),'-cp',target+':'+classpath,'DumpAdminJournalSchema'],**opts)
        statements=[base64.urlsafe_b64decode(line+'='*((-len(line))%4)).decode() for line in result.stdout.splitlines()]
        yes(len(statements)==4)
        path=Path(target)/'synthetic-admin.db'
        db=sqlite3.connect(path,isolation_level=None)
        for s in statements:db.execute(s)
        for s in statements:yes(db.execute('SELECT 1 FROM sqlite_master WHERE sql=?',(s,)).fetchone()==(1,))
        columns={r[1] for r in db.execute('PRAGMA table_info(admin_requests_v1)')}
        yes(columns=={'principal_fp','target_fp','data_epoch','request_id','content_sha256','job_ref'})
        base=('0'*64,'1'*64,'2'*32,'3'*32,bytes([4])*32,'j_'+'5'*32)
        insert='INSERT INTO admin_requests_v1 VALUES(?,?,?,?,?,?)'
        db.execute(insert,base)
        lookup='SELECT job_ref,content_sha256 FROM admin_requests_v1 WHERE principal_fp=? AND target_fp=? AND data_epoch=? AND request_id=?'
        yes(db.execute(lookup,base[:4]).fetchone()==(base[5],base[4]))
        yes(db.execute(lookup,base[:4]).fetchone()[1]!=bytes([9])*32) # native layer must report content conflict
        def reject(sql,values=()):
            nonlocal checks
            db.execute('SAVEPOINT invalid_case')
            try:db.execute(sql,values)
            except sqlite3.IntegrityError:checks+=1
            else:raise AssertionError('unsafe ledger mutation accepted')
            finally:db.execute('ROLLBACK TO invalid_case');db.execute('RELEASE invalid_case')
        reject(insert,base)
        reject('UPDATE admin_requests_v1 SET content_sha256=?',(bytes([9])*32,))
        reject('DELETE FROM admin_requests_v1')
        changed=list(base);changed[4]=bytes([9])*32
        reject('INSERT OR REPLACE INTO admin_requests_v1 VALUES(?,?,?,?,?,?)',changed)
        changed=list(base);changed[3]='6'*32
        reject('INSERT OR REPLACE INTO admin_requests_v1 VALUES(?,?,?,?,?,?)',changed) # job_ref conflict cannot evict original
        yes(db.execute(lookup,base[:4]).fetchone()==(base[5],base[4]))
        for field in [0,1,2,3]:
            for bad in ['X'*len(base[field]),'short','../unowned']:
                row=list(base);row[3]='7'*32;row[5]='j_'+'8'*32;row[field]=bad
                reject(insert,row)
        for bad in ['d'*32,b'short',None]:
            row=list(base);row[3]='7'*32;row[5]='j_'+'8'*32;row[4]=bad;reject(insert,row)
        for bad in ['../escape','j_'+'G'*32,None]:
            row=list(base);row[3]='7'*32;row[5]=bad;reject(insert,row)
        for field,length in [(0,64),(1,64),(2,32),(3,32)]:
            row=list(base);row[field]='9'*length;row[5]='j_'+format(field+16,'032x');db.execute(insert,row)
            yes(db.execute(lookup,row[:4]).fetchone()==(row[5],row[4]))
        db.execute('BEGIN');row=list(base);row[3]='a'*32;row[5]='j_'+'b'*32;db.execute(insert,row);db.execute('ROLLBACK')
        yes(db.execute(lookup,row[:4]).fetchone() is None)
        yes(db.execute('PRAGMA integrity_check').fetchone()==('ok',))
        db.close();db=sqlite3.connect(path);yes(db.execute(lookup,base[:4]).fetchone()==(base[5],base[4]));db.close()
    sources=[ROOT/'app/src/main/java/io/github/rebrowser/control/IdempotencyStore.java',fixture,Path(__file__)]
    return {'scope':'actual production-class DDL on HOST SQLite with exact-owned synthetic disk DB/reopen; NOT Android SQLite method execution or policy/nonce atomicity',
            'hostSqlChecks':checks,'sqliteVersion':sqlite3.sqlite_version,'ddlStatements':len(statements),
            'sourceFiles':[{'path':str(f.relative_to(ROOT)),'sha256':hashlib.sha256(f.read_bytes()).hexdigest()} for f in sources],
            'ddlSha256':hashlib.sha256('\n'.join(statements).encode()).hexdigest(),'temporaryDbAndHarnessRemoved':True,
            'androidSQLiteExecuted':False,'actualPolicyAndSqliteAtomicityTested':False,'realDataOrKeysUsed':False}


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home',type=Path,required=True)
    parser.add_argument('--android-jar',type=Path,required=True)
    parser.add_argument('--work-dir',type=Path,required=True)
    print(json.dumps(run(parser.parse_args()),ensure_ascii=False,indent=2))
