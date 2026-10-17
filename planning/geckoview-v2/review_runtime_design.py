#!/usr/bin/env python3
"""Finite planning traces only; no Gecko/Android, networking, crypto keys or data deletion.

This model checks proposed admission/settlement/publication invariants. It is NOT
an implementation, a mock proving Gecko behavior, or a device acceptance test.
Queue scheduling is outside this model; submit means attempt actual dispatch.
"""
import json
from pathlib import Path

P = Path(__file__).resolve().parent


def run_trace(trace):
    actions = {a['id']: a for a in json.loads((P / 'records/action-semantics.json').read_text())['actions']}
    state = {'generation': 1, 'auth': True, 'authorized': True,
             'sourceAllowed': True, 'ownedSession': True, 'ownedNativeTask': True, 'fenced': False,
             'sessionsClosed': False, 'writers': 0, 'tasks': {}}
    results = []

    def settle(task, result, publish):
        task['running'] = False
        task['client'] = 'RESULT_AVAILABLE'
        task['result'] = result
        task['published'] = publish
        task['waitingPersistence'] = False

    for event in trace['events']:
        op = event['op']
        if op == 'set':
            assert set(event['values']) <= {'auth', 'authorized', 'sourceAllowed', 'ownedSession', 'ownedNativeTask', 'writers'}
            state.update(event['values'])
        elif op == 'submit':
            action = actions[event['action']]
            lane = action['lane']
            safe = lane == 'safety'
            owned_target = state['ownedNativeTask'] if event['action'] == 'download.cancel' else state['ownedSession']
            if not state['authorized']:
                results.append('DENY_AUTHORITY')
            elif safe and not owned_target:
                results.append('NO_OWNED_WORK')
            elif not safe and not state['sourceAllowed']:
                results.append('SOURCE_LOCKED')
            elif not safe and state['fenced']:
                results.append('FENCED')
            elif action['siteAuthRequirement'] == 'fresh-current-business-lease' and not state['auth']:
                results.append('WAIT_AUTH')
            elif any(t['running'] and t['lane'] == lane for t in state['tasks'].values()):
                results.append('ACTUAL_LANE_BUSY')
            else:
                assert event['task'] not in state['tasks']
                state['tasks'][event['task']] = {
                    'generation': state['generation'], 'owner': event.get('owner', 'document'),
                    'lane': lane, 'running': True, 'client': 'WAITING', 'result': 'PENDING',
                    'published': False, 'cancelRequested': False,
                    'requiresPersistence': event.get('requiresPersistence', False),
                    'persistence': 'PENDING' if event.get('requiresPersistence', False) else 'NOT_REQUIRED',
                    'waitingPersistence': False,
                    'effect': event.get('effect', 'POSSIBLE' if lane == 'effect' else 'NONE_READ_ONLY')}
                results.append('DISPATCHED')
        elif op == 'ui_timeout':
            task = state['tasks'][event['task']]
            if task['running']:
                task['client'] = 'DEADLINE_EXCEEDED'
        elif op == 'rotate_document_lease':
            state['generation'] += 1
        elif op == 'port_disconnect':
            # No proof of actual task death; leave all slots occupied.
            pass
        elif op == 'cancel_request':
            state['tasks'][event['task']]['cancelRequested'] = True
        elif op == 'cancel_result':
            task = state['tasks'][event['task']]
            if event['confirmed'] and event.get('ownerMatches', True) and task['running']:
                settle(task, 'CANCELLED_LOCAL', False)
                if task['effect'] == 'POSSIBLE':
                    task['effect'] = 'UNKNOWN'
        elif op == 'actual_result':
            task = state['tasks'][event['task']]
            # Proven original execution owner can settle; current lease is a
            # separate requirement for publishing business content.
            if event.get('ownerMatches', True) and task['running']:
                current = task['generation'] == state['generation']
                publish = current and not state['fenced'] and state['sourceAllowed']
                task['effect'] = event.get('effect', task['effect'])
                if task['requiresPersistence']:
                    # A JS result hands work to native persistence, not a
                    # successful domain result. Session death cannot kill IO.
                    task['owner'] = 'native_worker'
                    task['waitingPersistence'] = True
                else:
                    settle(task, event.get('result', 'OBSERVED_DESIRED_STATE'), publish)
        elif op == 'commit_storage':
            task = state['tasks'][event['task']]
            if not task['waitingPersistence'] or not task['running']:
                continue  # Duplicate/late commit cannot rewrite a terminal result.
            current = task['generation'] == state['generation']
            allowed = current and not state['fenced'] and state['sourceAllowed']
            committed = allowed and event.get('success', True)
            task['persistence'] = 'COMMITTED' if committed else ('STALE_DISCARDED' if not allowed else 'FAILED')
            settle(task, task['persistence'], committed)
        elif op == 'fence':
            state['fenced'] = True
        elif op == 'close_session_owner':
            state['sessionsClosed'] = True
            state['ownedSession'] = False
            # Session closure does not terminate helper/native-worker work.
            for task in state['tasks'].values():
                if task['running'] and task['owner'] == 'document':
                    settle(task, 'LOCAL_OWNER_DESTROYED', False)
                    if task['effect'] == 'POSSIBLE':
                        task['effect'] = 'UNKNOWN'
        elif op == 'quiescence':
            ok = (state['fenced'] and state['sessionsClosed'] and state['writers'] == 0
                  and not any(t['running'] for t in state['tasks'].values()))
            results.append('TICKET' if ok else 'WAIT_ACTUAL_DRAIN')
        else:
            raise AssertionError(('unknown model operation', op))
    return state, results


def review():
    record = json.loads((P / 'records/runtime-traces.json').read_text())
    assert record['modelOnly'] and not record['runtimeTested']
    assert len({c['id'] for c in record['cases']}) == len(record['cases'])
    for case in record['cases']:
        state, results = run_trace(case)
        assert results == case['expectedResults'], (case['id'], results)
        for task_id, expected in case.get('expectedTasks', {}).items():
            for key, value in expected.items():
                assert state['tasks'][task_id][key] == value, (case['id'], task_id, key)
    protocol = json.loads((P / 'records/connector-protocol.json').read_text())
    budgets = protocol['budgets']
    encoded_chunk = ((budgets['streamChunkRawMaxBytes'] + 2) // 3) * 4
    assert encoded_chunk + 4096 <= budgets['maxEnvelopeUtf8Bytes']
    assert budgets['streamCreditWindowBytes'] >= budgets['streamChunkRawMaxBytes']
    assert budgets['initialHelloCountBeforeReady'] == 1
    assert len({m['kind'] for m in protocol['envelopes']}) == len(protocol['envelopes'])
    assert all(m['required'] and m['semantics'] and m['boundary'] for m in protocol['envelopes'])
    assert {'auth', 'effect', 'read', 'safety'} == {l['id'] for l in protocol['lanes']}
    return {'runtimeTimelineExamplesSymbolicallyChecked': len(record['cases']),
            'connectorEnvelopeSchemasReviewed': len(protocol['envelopes']),
            'streamChunkBase64BytesAtMax': encoded_chunk,
            'actualGeckoExecutionOrCancellationTested': False}


if __name__ == '__main__':
    print(json.dumps(review(), ensure_ascii=False, indent=2))
