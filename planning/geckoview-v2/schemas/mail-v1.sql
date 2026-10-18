-- DESIGN FIXTURE ONLY: new v2 account database, not an app migration or deletion implementation.
-- Android SQLite API30 compatibility still needs actual platform validation.
PRAGMA foreign_keys = ON;

CREATE TABLE account_meta (
  singleton INTEGER PRIMARY KEY CHECK(singleton=1),
  format_version INTEGER NOT NULL CHECK(format_version=1),
  data_epoch BLOB NOT NULL CHECK(length(data_epoch)=16),
  native_account_id BLOB NOT NULL CHECK(length(native_account_id)=16),
  partition_ref TEXT NOT NULL CHECK(length(partition_ref) BETWEEN 1 AND 80),
  binding_revision INTEGER NOT NULL CHECK(typeof(binding_revision)='integer' AND binding_revision>=1)
);

CREATE TABLE content_objects (
  object_id BLOB PRIMARY KEY NOT NULL CHECK(length(object_id)=16),
  original_digest BLOB NOT NULL CHECK(length(original_digest)=32),
  purpose INTEGER NOT NULL CHECK(purpose IN (1,2,3)),
  codec INTEGER NOT NULL CHECK(codec IN (0,1)),
  account_key_version INTEGER NOT NULL CHECK(account_key_version>=1),
  original_bytes INTEGER NOT NULL CHECK(typeof(original_bytes)='integer' AND original_bytes>=0),
  compressed_bytes INTEGER NOT NULL CHECK(typeof(compressed_bytes)='integer' AND compressed_bytes>=0),
  stored_bytes INTEGER NOT NULL CHECK(typeof(stored_bytes)='integer' AND stored_bytes>=144),
  storage_kind INTEGER NOT NULL CHECK(storage_kind IN (0,1)),
  inline_payload BLOB,
  owned_file_key TEXT,
  object_state INTEGER NOT NULL CHECK(object_state IN (0,1,2)),
  refcount INTEGER NOT NULL DEFAULT 0 CHECK(typeof(refcount)='integer' AND refcount>=0),
  CHECK((storage_kind=0 AND inline_payload IS NOT NULL AND length(inline_payload)=stored_bytes
         AND stored_bytes<=65536 AND owned_file_key IS NULL)
     OR (storage_kind=1 AND inline_payload IS NULL AND stored_bytes>65536
         AND owned_file_key=lower(hex(object_id))))
);
CREATE UNIQUE INDEX unique_ready_digest ON content_objects(original_digest,purpose) WHERE object_state=1;

CREATE TABLE mail_items (
  official_item_key TEXT PRIMARY KEY NOT NULL CHECK(length(official_item_key) BETWEEN 1 AND 1024),
  server_revision TEXT CHECK(server_revision IS NULL OR length(server_revision)<=4096),
  conversation_key TEXT,
  received_millis INTEGER NOT NULL CHECK(typeof(received_millis)='integer'),
  subject TEXT NOT NULL,
  sender_summary TEXT NOT NULL,
  flags INTEGER NOT NULL CHECK(typeof(flags)='integer' AND flags>=0),
  body_object_ref BLOB REFERENCES content_objects(object_id),
  search_object_ref BLOB REFERENCES content_objects(object_id),
  local_revision INTEGER NOT NULL CHECK(typeof(local_revision)='integer' AND local_revision>=1),
  sync_generation INTEGER NOT NULL CHECK(typeof(sync_generation)='integer' AND sync_generation>=1)
);
CREATE INDEX items_received ON mail_items(received_millis DESC,official_item_key);
CREATE INDEX items_conversation ON mail_items(conversation_key,received_millis DESC);
CREATE INDEX items_body_ref ON mail_items(body_object_ref);
CREATE INDEX items_search_ref ON mail_items(search_object_ref);

CREATE TABLE attachments (
  official_item_key TEXT NOT NULL REFERENCES mail_items(official_item_key),
  official_attachment_key TEXT NOT NULL CHECK(length(official_attachment_key) BETWEEN 1 AND 1024),
  object_ref BLOB REFERENCES content_objects(object_id),
  display_name TEXT NOT NULL,
  mime_hint TEXT NOT NULL,
  declared_bytes INTEGER CHECK(declared_bytes IS NULL OR (typeof(declared_bytes)='integer' AND declared_bytes>=0)),
  observed_bytes INTEGER NOT NULL CHECK(typeof(observed_bytes)='integer' AND observed_bytes>=0),
  download_state INTEGER NOT NULL CHECK(download_state IN (0,1,2,3)),
  PRIMARY KEY(official_item_key,official_attachment_key),
  CHECK((download_state=2 AND object_ref IS NOT NULL) OR (download_state<>2 AND object_ref IS NULL))
);
CREATE INDEX attachments_object ON attachments(object_ref);

CREATE TABLE sync_state (
  stream_key TEXT PRIMARY KEY NOT NULL,
  opaque_cursor TEXT,
  committed_revision INTEGER NOT NULL CHECK(typeof(committed_revision)='integer' AND committed_revision>=0),
  account_binding_revision INTEGER NOT NULL CHECK(account_binding_revision>=1),
  data_epoch BLOB NOT NULL CHECK(length(data_epoch)=16)
);
CREATE TABLE maintenance_jobs (
  job_id BLOB PRIMARY KEY NOT NULL CHECK(length(job_id)=16),
  kind INTEGER NOT NULL CHECK(kind BETWEEN 1 AND 8),
  phase INTEGER NOT NULL CHECK(phase BETWEEN 0 AND 8),
  target_manifest_digest BLOB NOT NULL CHECK(length(target_manifest_digest)=32),
  data_epoch BLOB NOT NULL CHECK(length(data_epoch)=16),
  generation INTEGER NOT NULL CHECK(generation>=1),
  last_committed_step INTEGER NOT NULL CHECK(last_committed_step>=0)
);
CREATE INDEX jobs_phase ON maintenance_jobs(phase);
CREATE TABLE search_policy (
  singleton INTEGER PRIMARY KEY CHECK(singleton=1),
  index_format INTEGER NOT NULL CHECK(index_format=1),
  indexed_revision INTEGER NOT NULL CHECK(indexed_revision>=0),
  retention_policy INTEGER NOT NULL CHECK(retention_policy BETWEEN 0 AND 3)
);

-- Counts are transactional caches. The derived view below is authoritative for conservative GC.
CREATE TRIGGER item_add_refs AFTER INSERT ON mail_items BEGIN
  UPDATE content_objects SET refcount=refcount+1 WHERE object_id=NEW.body_object_ref;
  UPDATE content_objects SET refcount=refcount+1 WHERE object_id=NEW.search_object_ref;
END;
CREATE TRIGGER item_change_refs AFTER UPDATE OF body_object_ref,search_object_ref ON mail_items BEGIN
  UPDATE content_objects SET refcount=refcount-1 WHERE object_id=OLD.body_object_ref;
  UPDATE content_objects SET refcount=refcount-1 WHERE object_id=OLD.search_object_ref;
  UPDATE content_objects SET refcount=refcount+1 WHERE object_id=NEW.body_object_ref;
  UPDATE content_objects SET refcount=refcount+1 WHERE object_id=NEW.search_object_ref;
END;
CREATE TRIGGER item_remove_refs AFTER DELETE ON mail_items BEGIN
  UPDATE content_objects SET refcount=refcount-1 WHERE object_id=OLD.body_object_ref;
  UPDATE content_objects SET refcount=refcount-1 WHERE object_id=OLD.search_object_ref;
END;
CREATE TRIGGER attachment_add_ref AFTER INSERT ON attachments BEGIN
  UPDATE content_objects SET refcount=refcount+1 WHERE object_id=NEW.object_ref;
END;
CREATE TRIGGER attachment_change_ref AFTER UPDATE OF object_ref ON attachments BEGIN
  UPDATE content_objects SET refcount=refcount-1 WHERE object_id=OLD.object_ref;
  UPDATE content_objects SET refcount=refcount+1 WHERE object_id=NEW.object_ref;
END;
CREATE TRIGGER attachment_remove_ref AFTER DELETE ON attachments BEGIN
  UPDATE content_objects SET refcount=refcount-1 WHERE object_id=OLD.object_ref;
END;
CREATE TRIGGER item_revision_guard BEFORE UPDATE ON mail_items
WHEN NEW.local_revision<=OLD.local_revision OR NEW.sync_generation<OLD.sync_generation
BEGIN SELECT RAISE(ABORT,'STALE_ITEM_REVISION'); END;
CREATE TRIGGER sync_epoch_guard BEFORE INSERT ON sync_state
WHEN NOT EXISTS(SELECT 1 FROM account_meta WHERE singleton=1 AND data_epoch=NEW.data_epoch
  AND binding_revision=NEW.account_binding_revision)
BEGIN SELECT RAISE(ABORT,'STALE_ACCOUNT_BINDING'); END;
CREATE TRIGGER sync_update_guard BEFORE UPDATE ON sync_state
WHEN NEW.committed_revision<OLD.committed_revision
  OR NOT EXISTS(SELECT 1 FROM account_meta WHERE singleton=1 AND data_epoch=NEW.data_epoch
  AND binding_revision=NEW.account_binding_revision)
BEGIN SELECT RAISE(ABORT,'STALE_SYNC_CURSOR'); END;
CREATE TRIGGER immutable_account_scope BEFORE UPDATE OF data_epoch,native_account_id,partition_ref ON account_meta
BEGIN SELECT RAISE(ABORT,'IMMUTABLE_ACCOUNT_SCOPE'); END;
CREATE TRIGGER account_binding_monotonic BEFORE UPDATE OF binding_revision ON account_meta
WHEN NEW.binding_revision<=OLD.binding_revision
BEGIN SELECT RAISE(ABORT,'STALE_ACCOUNT_BINDING'); END;
CREATE TRIGGER object_requires_account BEFORE INSERT ON content_objects
WHEN NOT EXISTS(SELECT 1 FROM account_meta WHERE singleton=1)
BEGIN SELECT RAISE(ABORT,'ACCOUNT_NOT_INITIALIZED'); END;
CREATE TRIGGER object_representation_immutable BEFORE UPDATE OF object_id,original_digest,purpose,codec,account_key_version,original_bytes,compressed_bytes,stored_bytes,storage_kind,inline_payload,owned_file_key ON content_objects
BEGIN SELECT RAISE(ABORT,'IMMUTABLE_OBJECT_REPRESENTATION'); END;
CREATE TRIGGER item_body_guard BEFORE INSERT ON mail_items
WHEN (NEW.body_object_ref IS NOT NULL AND NOT EXISTS(SELECT 1 FROM content_objects WHERE object_id=NEW.body_object_ref AND purpose=1 AND object_state=1))
  OR (NEW.search_object_ref IS NOT NULL AND NOT EXISTS(SELECT 1 FROM content_objects WHERE object_id=NEW.search_object_ref AND purpose=3 AND object_state=1))
BEGIN SELECT RAISE(ABORT,'INVALID_ITEM_OBJECT'); END;
CREATE TRIGGER item_body_update_guard BEFORE UPDATE OF body_object_ref,search_object_ref ON mail_items
WHEN (NEW.body_object_ref IS NOT NULL AND NOT EXISTS(SELECT 1 FROM content_objects WHERE object_id=NEW.body_object_ref AND purpose=1 AND object_state=1))
  OR (NEW.search_object_ref IS NOT NULL AND NOT EXISTS(SELECT 1 FROM content_objects WHERE object_id=NEW.search_object_ref AND purpose=3 AND object_state=1))
BEGIN SELECT RAISE(ABORT,'INVALID_ITEM_OBJECT'); END;
CREATE TRIGGER attachment_object_guard BEFORE INSERT ON attachments
WHEN NEW.object_ref IS NOT NULL AND NOT EXISTS(SELECT 1 FROM content_objects WHERE object_id=NEW.object_ref AND purpose=2 AND object_state=1)
BEGIN SELECT RAISE(ABORT,'INVALID_ATTACHMENT_OBJECT'); END;
CREATE TRIGGER attachment_object_update_guard BEFORE UPDATE OF object_ref ON attachments
WHEN NEW.object_ref IS NOT NULL AND NOT EXISTS(SELECT 1 FROM content_objects WHERE object_id=NEW.object_ref AND purpose=2 AND object_state=1)
BEGIN SELECT RAISE(ABORT,'INVALID_ATTACHMENT_OBJECT'); END;
CREATE VIEW actual_object_references AS
SELECT object_id,COUNT(*) AS actual_refs FROM (
  SELECT body_object_ref AS object_id FROM mail_items WHERE body_object_ref IS NOT NULL
  UNION ALL SELECT search_object_ref FROM mail_items WHERE search_object_ref IS NOT NULL
  UNION ALL SELECT object_ref FROM attachments WHERE object_ref IS NOT NULL
) GROUP BY object_id;
