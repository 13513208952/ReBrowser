#!/usr/bin/env python3
"""HOST in-memory SQLite constraint/rollback DESIGN fixtures; no Android or user data.

The DDL is a planning artifact. This never opens an existing database or deletes
files, and does not implement the application's cleanup/old-epoch erasure.
"""
import hashlib
import json
from pathlib import Path
import sqlite3

P = Path(__file__).resolve().parent


def review():
    ddl = (P / 'schemas/mail-v1.sql').read_text()
    checks = 0
    db = sqlite3.connect(':memory:', isolation_level=None)
    db.executescript(ddl)
    epoch, account = bytes(16), bytes([1]) * 16

    def yes(condition):
        nonlocal checks
        assert condition
        checks += 1

    def fail(fn):
        nonlocal checks
        db.execute('SAVEPOINT invalid_case')
        try:
            fn()
        except sqlite3.IntegrityError:
            checks += 1
        else:
            raise AssertionError('invalid schema operation unexpectedly accepted')
        finally:
            db.execute('ROLLBACK TO invalid_case')
            db.execute('RELEASE invalid_case')

    # Explicit columns keep synthetic fixtures coupled to the designed schema.
    def put(i, **kw):
        obj = i.to_bytes(16, 'big')
        purpose, codec, state = kw.get('purpose', 1), kw.get('codec', 1), kw.get('state', 1)
        size, kind = kw.get('size', 160), kw.get('kind', 0)
        payload = kw.get('payload', bytes(size) if kind == 0 else None)
        db.execute('INSERT INTO content_objects(object_id,original_digest,purpose,codec,account_key_version,original_bytes,compressed_bytes,stored_bytes,storage_kind,inline_payload,owned_file_key,object_state,refcount) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)',
                   (obj, kw.get('digest', hashlib.sha256(str(i).encode()).digest()), purpose, codec,
                    1, kw.get('original', 16), kw.get('compressed', 16), size, kind,
                    payload, kw.get('key'), state, 0))
        return obj

    def item(key, body=None, search=None, revision=1, generation=1):
        db.execute('INSERT INTO mail_items VALUES(?,?,?,?,?,?,?,?,?,?,?)',
                   (key, 'server-v1', 'thread-a', 0, 'fictional subject', 'example.invalid',
                    0, body, search, revision, generation))

    def refs(obj):
        cached = db.execute('SELECT refcount FROM content_objects WHERE object_id=?', (obj,)).fetchone()[0]
        actual = db.execute('SELECT actual_refs FROM actual_object_references WHERE object_id=?', (obj,)).fetchone()
        return cached, actual[0] if actual else 0

    fail(lambda: put(1))
    fail(lambda: db.execute('INSERT INTO sync_state VALUES(?,?,?,?,?)', ('main', 'opaque', 0, 1, epoch)))
    db.execute('INSERT INTO account_meta VALUES(1,1,?,?,?,1)', (epoch, account, 'p_fixture'))
    body = put(1)
    search = put(2, purpose=3)
    attachment = put(3, purpose=2)
    pending = put(4, state=0)
    item('one', body, search)
    yes(refs(body) == (1, 1) and refs(search) == (1, 1))
    item('two', body)
    yes(refs(body) == (2, 2))
    fail(lambda: put(5, codec=99))
    fail(lambda: put(5, original=-1))
    fail(lambda: put(5, digest=b'short'))
    fail(lambda: put(5, size=170, payload=bytes(160)))
    fail(lambda: put(5, size=65537))
    fail(lambda: put(5, kind=1, size=65537, key='../escape'))
    large = put(6, kind=1, size=65537, key=(6).to_bytes(16, 'big').hex())
    yes(refs(large) == (0, 0))
    fail(lambda: item('bad-purpose', attachment))
    fail(lambda: item('pending', pending))
    fail(lambda: item('missing', bytes([9]) * 16))
    fail(lambda: item('wrong-search', body, attachment))
    fail(lambda: item('one', body))
    fail(lambda: put(7, digest=hashlib.sha256(b'1').digest()))
    fail(lambda: db.execute('UPDATE content_objects SET codec=0 WHERE object_id=?', (body,)))
    fail(lambda: db.execute('UPDATE account_meta SET data_epoch=?', (bytes([2])*16,)))
    fail(lambda: db.execute('UPDATE account_meta SET binding_revision=1'))
    db.execute('INSERT INTO attachments VALUES(?,?,?,?,?,?,?,?)',
               ('one', 'attachment-1', attachment, 'fictional.txt', 'text/plain', None, 16, 2))
    yes(refs(attachment) == (1, 1))
    fail(lambda: db.execute('INSERT INTO attachments VALUES(?,?,?,?,?,?,?,?)',
               ('one', 'bad-body', body, 'x', 'text/plain', 16, 16, 2)))
    fail(lambda: db.execute('INSERT INTO attachments VALUES(?,?,?,?,?,?,?,?)',
               ('missing-item', 'x', attachment, 'x', 'text/plain', 16, 16, 2)))
    fail(lambda: db.execute('INSERT INTO attachments VALUES(?,?,?,?,?,?,?,?)',
               ('one', 'no-object', None, 'x', 'text/plain', 16, 16, 2)))
    fail(lambda: db.execute('UPDATE mail_items SET flags=1 WHERE official_item_key=?', ('one',)))
    replacement = put(8)
    db.execute('UPDATE mail_items SET body_object_ref=?,local_revision=2 WHERE official_item_key=?', (replacement, 'one'))
    yes(refs(body) == (1, 1) and refs(replacement) == (1, 1))
    db.execute('INSERT INTO sync_state VALUES(?,?,?,?,?)', ('main', 'cursor-1', 1, 1, epoch))
    fail(lambda: db.execute('UPDATE sync_state SET data_epoch=?', (bytes([2])*16,)))
    fail(lambda: db.execute('UPDATE sync_state SET account_binding_revision=2'))
    fail(lambda: db.execute('UPDATE sync_state SET committed_revision=0'))
    db.execute('BEGIN')
    staged = put(9)
    item('transaction-item', staged)
    db.execute('UPDATE sync_state SET opaque_cursor=?,committed_revision=2', ('uncommitted',))
    db.execute('ROLLBACK')
    yes(db.execute('SELECT COUNT(*) FROM content_objects WHERE object_id=?', (staged,)).fetchone()[0] == 0)
    yes(db.execute('SELECT COUNT(*) FROM mail_items WHERE official_item_key=?', ('transaction-item',)).fetchone()[0] == 0)
    yes(db.execute('SELECT opaque_cursor,committed_revision FROM sync_state').fetchone() == ('cursor-1', 1))
    # A corrupt cached count must not authorize GC: actual references stay visible.
    db.execute('UPDATE content_objects SET refcount=0 WHERE object_id=?', (body,))
    yes(refs(body) == (0, 1))
    yes(db.execute('SELECT COUNT(*) FROM actual_object_references WHERE object_id=?', (body,)).fetchone()[0] == 1)
    db.execute('UPDATE content_objects SET refcount=1,object_state=2 WHERE object_id=?', (body,))
    repaired = put(10, digest=hashlib.sha256(b'1').digest())
    yes(repaired != body)
    fail(lambda: item('new-ref-corrupt', body))
    db.execute('UPDATE account_meta SET binding_revision=2')
    fail(lambda: db.execute('UPDATE sync_state SET committed_revision=2'))
    db.execute('UPDATE sync_state SET committed_revision=2,account_binding_revision=2,opaque_cursor=?', ('cursor-2',))
    yes(db.execute('PRAGMA foreign_key_check').fetchall() == [])
    db.close()
    return {'hostSyntheticSqlChecks': checks, 'mailSchemaSha256': hashlib.sha256(ddl.encode()).hexdigest(),
            'hostSQLiteVersion': sqlite3.sqlite_version, 'androidSQLiteValidated': False,
            'realDatabaseOpenedOrFilesDeleted': False}


if __name__ == '__main__':
    print(json.dumps(review(), ensure_ascii=False, indent=2))
