'use strict';

// Run against DumpPermissionScript's compiled output, not a separately maintained policy copy.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const policy = fs.readFileSync(0, 'utf8');
assert(policy.includes('const youtubePlayerOrigins = [];'), 'expected compiled policy input');

const fixture = `
  const nodes = new WeakMap();
  const registrations = [];
  class EventTarget {
    addEventListener(type, listener, options) {
      registrations.push({target: this, type: String(type), listener, options});
    }
  }
  class Node extends EventTarget {
    constructor(name, owner = null, connected = false) {
      super();
      nodes.set(this, {name, owner, connected, attrs: {}});
    }
    get ownerDocument() { if (!nodes.has(this)) throw new TypeError('Illegal receiver'); return nodes.get(this).owner; }
    get isConnected() { if (!nodes.has(this)) throw new TypeError('Illegal receiver'); return nodes.get(this).connected; }
  }
  class Element extends Node {
    get localName() { if (!nodes.has(this)) throw new TypeError('Illegal receiver'); return nodes.get(this).name; }
    getAttribute(name) { if (!nodes.has(this)) throw new TypeError('Illegal receiver'); return nodes.get(this).attrs[name] || null; }
  }
  class HTMLDivElement extends Element {
    constructor(owner, connected = true) { super('div', owner, connected); }
  }
  class Document extends Node {
    constructor() { super('#document'); }
  }
  const document = new Document();
  const player = new HTMLDivElement(document);
  nodes.get(player).attrs.id = 'movie_player';
  const otherDiv = new HTMLDivElement(document);
  const detached = new HTMLDivElement(document, false);
  nodes.get(detached).attrs.id = 'movie_player';
  const foreign = new HTMLDivElement(new Document());
  nodes.get(foreign).attrs.id = 'movie_player';
  const notDiv = new Element('span', document, true);
  nodes.get(notDiv).attrs.id = 'movie_player';
  const navigator = {};
  const location = {origin: originForTest};
  const window = globalThis;
  Object.setPrototypeOf(globalThis, EventTarget.prototype);
  globalThis.top = frameForTest ? {} : globalThis;
  class DOMException extends Error { constructor(message, name) { super(message); this.name = name; } }
  const reBrowserPermissionBridge = {postMessage() { throw new Error('native capabilities must not be invoked'); }};
  class ServiceWorkerRegistration {}
  const Notification = function() {};
  const PushManager = function() {};
  const Accelerometer = function() {};
`;
// Constructors blocked by the policy must be global properties, not immutable lexical bindings.
const adjustedFixture = fixture.replace(/const (Notification|PushManager|Accelerometer) =/g, 'globalThis.$1 =');
let checks = 0;
function realm(origin = 'https://m.youtube.com', enabled = true, frame = false) {
  const context = vm.createContext({originForTest: origin, frameForTest: frame});
  vm.runInContext(adjustedFixture, context);
  const script = policy.replace('const youtubePlayerOrigins = [];',
    `const youtubePlayerOrigins = ${JSON.stringify(enabled ? ['https://m.youtube.com'] : [])};`);
  vm.runInContext(script, context);
  return context;
}
function evaluate(context, code) { return vm.runInContext(code, context); }
function yes(context, code) { assert.equal(evaluate(context, code), true, code); checks++; }
function rejected(context, code) { assert.throws(() => evaluate(context, `'use strict'; ${code}`)); checks++; }

for (const [origin, enabled, frame] of [
  ['https://m.youtube.com', false, false],
  ['https://www.youtube.com', true, false],
  ['https://evil.test', true, false],
  ['http://m.youtube.com', true, false],
  ['https://m.youtube.com', true, true]
]) {
  const c = realm(origin, enabled, frame);
  rejected(c, 'player.addEventListener = function() {};');
  yes(c, "Object.getOwnPropertyDescriptor(EventTarget.prototype, 'addEventListener').writable === false");
}

const c = realm();
yes(c, "Object.getOwnPropertyDescriptor(EventTarget.prototype, 'addEventListener').writable === false");
for (const target of ['window', 'document', 'EventTarget.prototype', 'HTMLDivElement.prototype',
    'otherDiv', 'detached', 'foreign', 'notDiv', 'Object.create(HTMLDivElement.prototype)']) {
  rejected(c, `${target}.addEventListener = function() {};`);
}
// A player API facade succeeds, including repeated facade wrapping, but is not transferable.
evaluate(c, `
  const oldAdd = player.addEventListener;
  const apiEvents = [];
  player.addEventListener = function(type, callback, extra) {
    apiEvents.push({type, receiver: this, extra});
    return oldAdd.apply(this, [type, callback]);
  };
  player.addEventListener('onStateChange', () => {}, 7);
`);
yes(c, "apiEvents.length === 1 && apiEvents[0].receiver === player && apiEvents[0].extra === 7");
yes(c, "registrations.length === 1 && registrations[0].type === 'onStateChange'");
for (const target of ['window', 'document', 'otherDiv']) {
  rejected(c, `player.addEventListener.call(${target}, 'click', () => {});`);
}
rejected(c, "Object.defineProperty(player, 'addEventListener', {value: () => {}});");
rejected(c, "Object.getOwnPropertyDescriptor(player, 'addEventListener').set.call(window, () => {});");
rejected(c, 'player.addEventListener = null;');
// Moving the player to another document or detaching it cannot carry the compatibility along.
evaluate(c, 'nodes.get(player).owner = new Document();');
rejected(c, "player.addEventListener('click', () => {});");
rejected(c, 'player.addEventListener = function() {};');
evaluate(c, 'nodes.get(player).owner = document; nodes.get(player).connected = false;');
rejected(c, "player.addEventListener('click', () => {});");
rejected(c, 'player.addEventListener = function() {};');
evaluate(c, 'nodes.get(player).connected = true;');

// Sensitive event names never reach either the custom facade or the native registration path.
evaluate(c, `
  player.addEventListener('deviceorientation', () => {});
  player.addEventListener('DeviceMotion', () => {});
  document.addEventListener('deviceorientationabsolute', () => {});
  window.addEventListener('devicemotion', () => {});
  EventTarget.prototype.addEventListener.call(window, 'deviceorientation', () => {});
`);
yes(c, 'apiEvents.length === 1 && registrations.length === 1');
// Coerce a type once: a mutable toString cannot pass the filter as one name and register another.
evaluate(c, `
  let conversions = 0;
  player.addEventListener({toString() { conversions++; return conversions === 1 ? 'click' : 'devicemotion'; }}, () => {});
`);
yes(c, "conversions === 1 && registrations.at(-1).type === 'click'");
evaluate(c, `
  const firstFacade = player.addEventListener;
  player.addEventListener = function(type, callback) { return firstFacade.call(this, type, callback); };
  player.addEventListener('click', () => {});
`);
yes(c, "registrations.at(-1).type === 'click'");
// Mutating Function.prototype.call must not expose the privately captured native method.
evaluate(c, `
  const savedCall = Function.prototype.call;
  const savedApply = Reflect.apply;
  let nativeLeaked = false;
  Function.prototype.call = function(receiver, ...args) {
    if (this === EventTarget.prototype.addEventListener) return savedApply(this, receiver, args);
    nativeLeaked = true;
    return savedApply(this, receiver, args);
  };
  window.addEventListener('click', () => {});
  Function.prototype.call = savedCall;
`);
yes(c, 'nativeLeaked === false');
yes(c, "typeof navigator.clipboard.readText === 'function' && typeof navigator.geolocation.getCurrentPosition === 'function'");
yes(c, "typeof globalThis.Notification === 'undefined' && typeof globalThis.PushManager === 'undefined' && typeof globalThis.Accelerometer === 'undefined'");
yes(c, 'window.ondevicemotion === null && window.ondeviceorientation === null');
console.log(`permission policy: ${checks} checks passed`);
