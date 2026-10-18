#!/usr/bin/env python3
"""Source-backed contract checks plus explicit SYMBOLIC permission-expiry counterexample.
Never reads clipboard/location, creates grants, or invokes Android/Gecko.
"""
import json
from pathlib import Path
import hashlib

P = Path(__file__).resolve().parent


def review():
    design = json.loads((P/'records/gecko-permission-design.json').read_text())
    evidence = json.loads((P/'evidence/gecko-permission-api-and-lifetime.json').read_text())
    assert evidence['compile']['exitCode'] == 0
    source = P/evidence['compile']['source']
    assert hashlib.sha256(source.read_bytes()).hexdigest() == evidence['compile']['sourceSha256']
    assert not evidence['AndroidRuntimeTested'] and not evidence['realLocationOrClipboardRead']
    assert not design['actualDevicePermissionOrClipboardTestsPerformed']
    assert {'PF01','PF02','PF03','PF04','PF05','PF06','PF07','PF08'} <= {f['id'] for f in design['criticalFindings']}
    geo_probe = json.loads((P/'evidence/geolocation-source-host-probe.json').read_text())
    geo_fixture = P/'experiments/geolocation_source_host_probe.py'
    assert hashlib.sha256(geo_fixture.read_bytes()).hexdigest() == geo_probe['fixtureSha256']
    assert geo_probe['checks'] == 16 and geo_probe['temporaryHarnessRemoved']
    assert geo_probe['coarseCriteriaDidNotPreventMostAccurateSyntheticCachedObjectPublication']
    assert geo_probe['pausedStillRequestedBranchDidNotRemoveSyntheticExistingListener']
    assert not geo_probe['actualSensitiveApiAccess'] and not geo_probe['actualNativeGeckoPrecisionObserved']
    assert not geo_probe['nativeDownstreamPerRequestRedactionInspected']
    module_probe = json.loads((P/'evidence/permission-module-host-probe.json').read_text())
    assert hashlib.sha256((P/module_probe['fixtureSource']).read_bytes()).hexdigest() == module_probe['fixtureSha256']
    assert module_probe['checks'] == 28 and module_probe['ordinaryPermissionWritesExpireNeverObserved']
    assert not module_probe['setPermissionDispatchCompletionCallbackObserved']
    assert not module_probe['realPermissionOrClipboardLocationAccess']
    # Symbolic source-backed counterexample: metadata/timer expiry is not engine revocation.
    n = 0; expires = 300000
    engine_state = 'allow-never'; own_grant_active = lambda now: now < expires
    assert own_grant_active(0);n += 1
    assert not own_grant_active(expires);n += 1
    assert engine_state == 'allow-never';n += 1  # stale engine cache survives own metadata expiry
    revoke_submitted = True; n += 1
    assert revoke_submitted and engine_state == 'allow-never';n += 1
    fence_open = False
    assert not fence_open;n += 1
    # A readback from another context is not evidence for this context.
    expected_context = 'synthetic-a'; observed_context = 'synthetic-b'
    if observed_context == expected_context: fence_open = True
    assert not fence_open;n += 1
    # Model a later, scoped confirmed state, NOT an actual Gecko callback.
    observed_context = expected_context; engine_state = 'prompt'
    fence_open = observed_context == expected_context and engine_state != 'allow-never'
    assert fence_open;n += 1
    active_capture_stop_observed = False
    assert not active_capture_stop_observed;n += 1  # permission-map state != capture stopped
    notified = False; shown = False
    assert not shown and not notified;n += 1
    shown = True
    if shown and not notified: notified = True
    assert notified;n += 1
    # Exactly-once request settlement must not be renewed by a second reply.
    settled = False; replies = 0
    for _ in range(2):
        if not settled: settled = True;replies += 1
    assert replies == 1;n += 1
    return {'permissionNativeApiCompileEvidenceChecked':True,
            'pinnedPermissionModuleHostChecks':module_probe['checks'],
            'geolocationSourceMethodHostChecks':geo_probe['checks'],
            'permissionLifetimeSymbolicBoundaryChecks':n,
            'finitePermissionLeaseEngineExpiryImplemented':False,
            'actualOwnerClipboardLocationOrMediaAccess':False}


if __name__ == '__main__':
    print(json.dumps(review(),indent=2))
