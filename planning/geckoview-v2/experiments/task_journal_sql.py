#!/usr/bin/env python3
"""Actual production-class task DDL/recovery SQL on owned HOST SQLite, not Android DAO."""
import argparse
import base64
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import shutil
import sqlite3
import subprocess
import tempfile

P=Path(__file__).resolve().parents[1]
ROOT=P.parents[1]
STATES=[(0,0,0),(1,1,0),(2,1,1),(3,1,2),(3,2,2),(3,3,2),(3,1,3),(3,2,3),(3,3,3),(4,2,0),(5,1,1),(5,2,1),(5,3,1),(6,3,0)]
TARGET='1'*64
EPOCH='2'*32
RUN='3'*32
TOKEN='4'*32
JOB='j_'+'5'*32
INSERT='INSERT INTO task_journal_v1 VALUES(?,?,?,?,?,?,?,?,?,?)'


def transition_allowed(a,b):
    p,w,e=a;q,x,f=b
    destinations={0:{1,4},1:{2,4,6},2:{3,5},3:{3},4:set(),5:{3,5},6:{6}}
    if q not in destinations[p]:return False
    if w==2 and x!=2:return False
    if w==3 and x not in (2,3):return False
    if p==3 and f!=e:return False
    return True


def update(db,state):
    p,w,e=state
    old=db.execute('SELECT phase FROM task_journal_v1 WHERE job_ref=?',(JOB,)).fetchone()[0]
    if old==0 and p==1:
        db.execute('UPDATE task_journal_v1 SET phase=?,work=?,effect=?,owner_run=?,execution_token=?,revision=revision+1 WHERE job_ref=?',(p,w,e,RUN,TOKEN,JOB))
    else:db.execute('UPDATE task_journal_v1 SET phase=?,work=?,effect=?,revision=revision+1 WHERE job_ref=?',(p,w,e,JOB))


def seed(db,state):
    db.execute(INSERT,(JOB,TARGET,EPOCH,b'b'*32,0,0,0,'','',0))
    p,w,e=state
    if p==0:return
    if p==4:update(db,state);return
    update(db,(1,1,0))
    if p==1:return
    if p==6:update(db,state);return
    update(db,(2,1,1))
    if p!=2:update(db,state)


def run(args):
    work=args.work_dir.resolve()
    assert work.is_dir() and not work.is_relative_to(ROOT) and not work.is_relative_to(Path('/tmp'))
    assert shutil.disk_usage(work).free>=16*1024**2+1024**3
    assert int(next(l.split()[1] for l in Path('/proc/meminfo').read_text().splitlines() if l.startswith('MemAvailable:')))*1024>=3*1024**3
    classes=ROOT/'rebrowser/app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
    fixture=P/'experiments/DumpTaskJournalSchema.java'
    checks=0
    def yes(condition):
        nonlocal checks
        assert condition
        checks+=1
    with tempfile.TemporaryDirectory(prefix='task-journal-host-',dir=work) as target:
        opts=dict(check=True,text=True,capture_output=True,timeout=30)
        classpath=str(classes)+':'+str(args.android_jar.resolve())
        subprocess.run([str(args.java_home/'bin/javac'),'-J-Xmx256m','-J-Djava.io.tmpdir='+str(work),'--release','8','-cp',classpath,'-d',target,str(fixture)],**opts)
        result=subprocess.run([str(args.java_home/'bin/java'),'-Xmx96m','-Djava.io.tmpdir='+str(work),'-cp',target+':'+classpath,'DumpTaskJournalSchema'],**opts)
        sql=[base64.urlsafe_b64decode(s+'='*((-len(s))%4)).decode() for s in result.stdout.splitlines()]
        yes(len(sql)==9);admin=sql[:4];ddl=sql[4:8];recovery=sql[8]
        def connect(path=':memory:'):
            db=sqlite3.connect(path,isolation_level=None,timeout=5)
            db.execute('PRAGMA foreign_keys=ON');db.execute('PRAGMA recursive_triggers=OFF')
            return db
        def schema(db):
            for statement in admin+ddl:db.execute(statement)
        db=connect();schema(db)
        for statement in admin+ddl:yes(db.execute('SELECT 1 FROM sqlite_master WHERE sql=?',(statement,)).fetchone()==(1,))
        # Every valid phase/work/effect destination tested against every valid origin.
        accepted=0
        for a in STATES:
            db.execute('SAVEPOINT origin');seed(db,a)
            for b in STATES:
                db.execute('SAVEPOINT destination')
                try:update(db,b)
                except sqlite3.IntegrityError:yes(not transition_allowed(a,b))
                else:yes(transition_allowed(a,b));accepted+=1
                finally:db.execute('ROLLBACK TO destination');db.execute('RELEASE destination')
            db.execute('ROLLBACK TO origin');db.execute('RELEASE origin')
        def reject(statement,values=()):
            nonlocal checks
            db.execute('SAVEPOINT invalid')
            try:db.execute(statement,values)
            except sqlite3.IntegrityError:checks+=1
            else:raise AssertionError('unsafe task mutation accepted')
            finally:db.execute('ROLLBACK TO invalid');db.execute('RELEASE invalid')
        seed(db,(2,1,1))
        for column,value in [('job_ref','j_'+'a'*32),('target_fp','a'*64),('data_epoch','a'*32),('binding_sha256',b'x'*32),('owner_run','a'*32),('execution_token','a'*32)]:
            reject('UPDATE task_journal_v1 SET '+column+'=?,revision=revision+1',(value,))
        reject('DELETE FROM task_journal_v1')
        reject('INSERT OR REPLACE INTO task_journal_v1 VALUES(?,?,?,?,?,?,?,?,?,?)',(JOB,TARGET,EPOCH,b'b'*32,0,0,0,'','',0))
        reject('UPDATE task_journal_v1 SET revision=revision+2')
        reject('UPDATE task_journal_v1 SET revision=1.5')
        reject('UPDATE task_journal_v1 SET phase=2,work=2,revision=revision+1') # ended+unknown cannot masquerade as pending intent
        reject(INSERT,('j_'+'c'*32,TARGET,EPOCH,b'b'*32,2,1,1,RUN,TOKEN,0)) # cannot insert directly as dispatched
        for index,bad in [(0,'../path'),(1,'G'*64),(2,'short'),(3,'b'*32)]:
            row=['j_'+'d'*32,TARGET,EPOCH,b'b'*32,0,0,0,'','',0];row[index]=bad;reject(INSERT,row)
        # Exact compiled recovery SQL: unknown whole-chain work, never terminal or replay.
        db.execute(recovery,(TARGET,EPOCH,'9'*32))
        yes(db.execute('SELECT phase,work,effect FROM task_journal_v1 WHERE job_ref=?',(JOB,)).fetchone()==(5,3,1))
        before=db.total_changes;db.execute(recovery,(TARGET,EPOCH,'9'*32));yes(db.total_changes==before)
        db.close()
        for state in [(1,1,0),(3,1,2),(3,2,3)]:
            db=connect();schema(db);seed(db,state)
            db.execute(recovery,(TARGET,EPOCH,RUN));yes(db.execute('SELECT phase,work,effect FROM task_journal_v1').fetchone()==state) # live process/instance is NOT lost
            db.execute(recovery,(TARGET,EPOCH,'9'*32));expected=(6,3,0) if state[0]==1 else (3,3,state[2]) if state[1]==1 else state
            yes(db.execute('SELECT phase,work,effect FROM task_journal_v1').fetchone()==expected);db.close()
        # Both request and task rows roll back/commit/reopen as ONE SQLite transaction.
        path=Path(target)/'paired-synthetic.db';db=connect(path);schema(db)
        request=('0'*64,TARGET,EPOCH,'a'*32,b'c'*32,JOB)
        db.execute('BEGIN IMMEDIATE');db.execute('INSERT INTO admin_requests_v1 VALUES(?,?,?,?,?,?)',request);seed(db,(0,0,0));db.execute('ROLLBACK')
        yes(db.execute('SELECT COUNT(*) FROM admin_requests_v1').fetchone()==(0,));yes(db.execute('SELECT COUNT(*) FROM task_journal_v1').fetchone()==(0,))
        db.execute('BEGIN IMMEDIATE');db.execute('INSERT INTO admin_requests_v1 VALUES(?,?,?,?,?,?)',request);seed(db,(0,0,0));db.execute('COMMIT');db.close()
        db=connect(path);yes(db.execute('SELECT job_ref FROM admin_requests_v1').fetchone()==(JOB,));yes(db.execute('SELECT phase,work,effect FROM task_journal_v1').fetchone()==(0,0,0));db.close()
        def compete(number):
            c=connect(path)
            try:
                c.execute('BEGIN IMMEDIATE');cursor=c.execute('UPDATE task_journal_v1 SET phase=1,work=1,owner_run=?,execution_token=?,revision=revision+1 WHERE job_ref=? AND phase=0',(RUN,format(number,'032x'),JOB));won=cursor.rowcount;c.execute('COMMIT');return won
            finally:c.close()
        with ThreadPoolExecutor(max_workers=2) as pool:yes(sorted(pool.map(compete,[1,2]))==[0,1])
        db=connect(path);yes(db.execute('PRAGMA integrity_check').fetchone()==('ok',));db.close()
    sources=[ROOT/'app/src/main/java/io/github/rebrowser/core/TaskJournal.java',ROOT/'app/src/main/java/io/github/rebrowser/core/TaskSnapshot.java',ROOT/'app/src/main/java/io/github/rebrowser/control/IdempotencyStore.java',fixture,Path(__file__)]
    return {'scope':'actual compiled literal DDL/recovery on owned HOST SQLite, exhaustive transition graph and paired SQL transaction; NOT Android DAO execution/complete policy admission',
        'hostSqlChecks':checks,'validStateTriples':len(STATES),'transitionPairsTested':len(STATES)**2,'acceptedTransitionPairs':accepted,'concurrentHostSqlWriters':2,'sqliteVersion':sqlite3.sqlite_version,
        'sourceFiles':[{'path':str(f.relative_to(ROOT)),'sha256':hashlib.sha256(f.read_bytes()).hexdigest()} for f in sources],
        'ddlAndRecoverySha256':hashlib.sha256('\n'.join(sql).encode()).hexdigest(),'temporaryDbAndHarnessRemoved':True,
        'androidSQLiteMethodsExecuted':False,'actualNativeCoordinatorAndPolicyAtomicityTested':False,'realDataOrKeysUsed':False}


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home',type=Path,required=True);parser.add_argument('--android-jar',type=Path,required=True);parser.add_argument('--work-dir',type=Path,required=True)
    print(json.dumps(run(parser.parse_args()),ensure_ascii=False,indent=2))
