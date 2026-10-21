#!/usr/bin/env python3
"""Independent R53 actual HOST SQLite candidate audit. No Android DB, keys or user data."""
from pathlib import Path
import sqlite3,json,hashlib
P=Path(__file__).resolve().parents[1]
def run():
    source=P/'experiments/runtime-mail-schema-candidate.sql';raw=source.read_bytes();text=raw.decode();blocks=[b.strip() for b in text.split('-- rebrowser:statement')[1:]]
    db=sqlite3.connect(':memory:',isolation_level=None);checks=0
    for b in blocks:db.execute(b)
    assert db.execute('PRAGMA foreign_keys').fetchone()==(1,);checks+=1
    account=(bytes(16),bytes([1])*16,'ctx_native')
    db.execute('INSERT INTO account_meta VALUES(1,1,?,?,?,1)',account)
    def rejected(sql,args=()):
        nonlocal checks
        db.execute('SAVEPOINT bad')
        try:db.execute(sql,args)
        except sqlite3.DatabaseError:checks+=1
        else:raise AssertionError('invalid mail state accepted')
        finally:db.execute('ROLLBACK TO bad');db.execute('RELEASE bad')
    rejected('DELETE FROM account_meta');rejected('UPDATE account_meta SET native_account_id=?',(bytes([9])*16,));rejected('INSERT OR REPLACE INTO account_meta VALUES(1,1,?,?,?,2)',account)
    oid=bytes([2])*16;db.execute('INSERT INTO object_reservations(object_id) VALUES(?)',(oid,))
    rejected('INSERT OR REPLACE INTO object_reservations(object_id) VALUES(?)',(oid,));rejected('DELETE FROM object_reservations');rejected('UPDATE object_reservations SET object_id=?',(bytes([3])*16,))
    columns='object_id,original_digest,purpose,codec,account_key_version,original_bytes,compressed_bytes,stored_bytes,storage_kind,inline_payload,owned_file_key,object_state'
    good=[oid,bytes([4])*32,1,0,1,1,1,145,0,bytes(145),None,1]
    insert='INSERT INTO content_objects('+columns+') VALUES('+','.join('?'*12)+')'
    for field,bad in [(0,'2'*16),(1,'4'*32),(4,1.5),(4,0),(4,4294967296),(5,2),(7,144),(9,bytes(144)),(10,'unowned')]:
        row=good.copy();row[field]=bad;rejected(insert,row)
    large=good.copy();large[5]=large[6]=65536;large[7]=124+65536+40;large[8]=1;large[9]=None;large[10]=None;rejected(insert,large)
    db.execute(insert,good);checks+=1
    assert db.execute('SELECT use_state FROM object_reservations WHERE object_id=?',(oid,)).fetchone()==(1,);checks+=1
    rejected('UPDATE object_reservations SET use_state=0');rejected('UPDATE content_objects SET object_state=0');rejected('UPDATE content_objects SET original_digest=?',(bytes([5])*32,))
    db.execute('INSERT INTO mail_items VALUES(?,?,?,?,?,?,?,?,?,?,?)',('m1','s1',None,1,'subject','sender',0,oid,None,1,1));checks+=1
    assert db.execute('SELECT refcount FROM content_objects').fetchone()==(1,);checks+=1
    assert db.execute('SELECT actual_refs FROM actual_object_references').fetchone()==(1,);checks+=1
    rejected('UPDATE mail_items SET local_revision=1');rejected('DELETE FROM content_objects WHERE object_id=?',(oid,))
    db.execute('INSERT INTO sync_state VALUES(?,?,?,?,?)',('main','c1',1,1,bytes(16)));checks+=1
    rejected('UPDATE sync_state SET data_epoch=?',(bytes([8])*16,));rejected('UPDATE sync_state SET account_binding_revision=2');rejected('UPDATE sync_state SET committed_revision=0')
    db.execute('DELETE FROM mail_items WHERE official_item_key=?',('m1',));assert db.execute('SELECT refcount FROM content_objects').fetchone()==(0,);checks+=1
    db.execute('DELETE FROM content_objects WHERE object_id=?',(oid,));rejected(insert,good)
    schema=[{'type':t,'name':n,'sql':s} for t,n,s in db.execute("SELECT type,name,sql FROM sqlite_master WHERE sql IS NOT NULL ORDER BY type,name")]
    db.close();return {'scope':'R53 independent actual HOST SQLite candidate audit; no runtime schema promotion/Android DB execution','candidateSourceSha256':hashlib.sha256(raw).hexdigest(),'statementsExecuted':len(blocks),'checksPassed':checks,'schema':schema,'androidSqliteMethodsExecuted':False,'realDataOrKeysUsed':False,'sourcePath':'planning/geckoview-v2/experiments/runtime-mail-schema-candidate.sql'}
if __name__=='__main__':print(json.dumps(run(),ensure_ascii=False,indent=2))
