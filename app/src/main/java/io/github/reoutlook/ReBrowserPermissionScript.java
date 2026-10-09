package io.github.reoutlook;

import org.json.JSONArray;

import java.util.Set;
import java.util.TreeSet;

/** Fixed semantic website API policy installed before untrusted page scripts. */
final class ReBrowserPermissionScript {
    private ReBrowserPermissionScript() {}

    static String documentStart(Set<String> youtubeOrigins) {
        Set<String> supported = new TreeSet<>(youtubeOrigins);
        supported.retainAll(ReBrowserPlayerCompatibility.YOUTUBE_ORIGINS);
        return DOCUMENT_START.replace("const youtubePlayerOrigins = [];",
                "const youtubePlayerOrigins = " + new JSONArray(supported) + ";");
    }

    static final String DOCUMENT_START = """
            (() => {
              if (globalThis.__reBrowserPermissionPolicyInstalled) return;
              globalThis.__reBrowserPermissionPolicyInstalled = true;
              const bridge = globalThis.reBrowserPermissionBridge;
              const denied = message => new DOMException(message || 'Permission denied', 'NotAllowedError');
              const pending = new Map();
              let sequence = 0;
              const send = (operation, value = {}) => new Promise((resolve, reject) => {
                if (!bridge || typeof bridge.postMessage !== 'function') {
                  reject(denied('Permission bridge unavailable'));
                  return;
                }
                const id = String(++sequence);
                pending.set(id, {resolve, reject});
                try {
                  bridge.postMessage(JSON.stringify({id, operation, ...value}));
                } catch (_) {
                  pending.delete(id);
                  reject(denied('Permission request failed'));
                }
              });
              if (bridge) bridge.onmessage = event => {
                try {
                  const value = JSON.parse(String(event.data || '{}'));
                  const callback = pending.get(String(value.id || ''));
                  if (!callback) return;
                  pending.delete(String(value.id));
                  if (value.ok) callback.resolve(value.value);
                  else callback.reject(denied(String(value.message || 'Permission denied')));
                } catch (_) {}
              };

              const requireClipboardGesture = () => {
                if (navigator.userActivation && !navigator.userActivation.isActive) {
                  return Promise.reject(denied('Clipboard requires a user gesture'));
                }
                return null;
              };
              const clipboard = Object.freeze({
                readText: () => requireClipboardGesture() ||
                  send('clipboard-read').then(value => String(value || '')),
                writeText: value => requireClipboardGesture() ||
                  send('clipboard-write', {text: String(value ?? '')}).then(() => undefined),
                read: () => Promise.reject(new DOMException('Rich clipboard is unsupported', 'NotSupportedError')),
                write: () => Promise.reject(new DOMException('Rich clipboard is unsupported', 'NotSupportedError'))
              });
              try {
                const navigatorPrototype = Object.getPrototypeOf(navigator);
                Object.defineProperty(navigatorPrototype, 'clipboard', {
                  get: () => clipboard, configurable: false, enumerable: true
                });
                Object.defineProperty(navigator, 'clipboard', {
                  value: clipboard, configurable: false, enumerable: true, writable: false
                });
              } catch (_) {}

              const position = value => ({
                coords: {
                  latitude: Number(value.latitude), longitude: Number(value.longitude),
                  accuracy: Number(value.accuracy), altitude: value.altitude == null ? null : Number(value.altitude),
                  altitudeAccuracy: value.altitudeAccuracy == null ? null : Number(value.altitudeAccuracy),
                  heading: value.heading == null ? null : Number(value.heading),
                  speed: value.speed == null ? null : Number(value.speed)
                },
                timestamp: Number(value.timestamp || Date.now())
              });
              const locationRequest = options => send('location', {
                highAccuracy: Boolean(options && options.enableHighAccuracy),
                timeout: Math.max(1000, Math.min(30000, Number(options && options.timeout) || 15000)),
                maximumAge: Math.max(0, Math.min(300000, Number(options && options.maximumAge) || 0))
              });
              let nextWatch = 0;
              const watches = new Map();
              const geolocation = Object.freeze({
                getCurrentPosition(success, error, options) {
                  if (typeof success !== 'function') throw new TypeError('success callback required');
                  locationRequest(options).then(value => success(position(value))).catch(reason => {
                    if (typeof error === 'function') error({code: 1, message: String(reason.message || reason)});
                  });
                },
                watchPosition(success, error, options) {
                  if (typeof success !== 'function') throw new TypeError('success callback required');
                  const watchId = ++nextWatch;
                  const tick = () => {
                    if (!watches.has(watchId)) return;
                    locationRequest(options).then(value => success(position(value))).catch(reason => {
                      if (typeof error === 'function') error({code: 1, message: String(reason.message || reason)});
                    }).finally(() => {
                      if (watches.has(watchId)) watches.set(watchId, setTimeout(tick, 5000));
                    });
                  };
                  watches.set(watchId, setTimeout(tick, 0));
                  return watchId;
                },
                clearWatch(watchId) {
                  const timer = watches.get(Number(watchId));
                  if (timer != null) clearTimeout(timer);
                  watches.delete(Number(watchId));
                }
              });
              try {
                const navigatorPrototype = Object.getPrototypeOf(navigator);
                Object.defineProperty(navigatorPrototype, 'geolocation', {
                  get: () => geolocation, configurable: false, enumerable: true
                });
                Object.defineProperty(navigator, 'geolocation', {
                  value: geolocation, configurable: false, enumerable: true, writable: false
                });
              } catch (_) {}

              const blockedSensors = [
                'Accelerometer', 'Gyroscope', 'Magnetometer', 'LinearAccelerationSensor',
                'GravitySensor', 'AbsoluteOrientationSensor', 'RelativeOrientationSensor',
                'AmbientLightSensor', 'DeviceMotionEvent', 'DeviceOrientationEvent'
              ];
              for (const name of blockedSensors) {
                try { Object.defineProperty(globalThis, name, {value: undefined, configurable: false}); }
                catch (_) {}
              }
              const apply = Reflect.apply;
              const string = String;
              const lowerCase = String.prototype.toLowerCase;
              const defineProperty = Object.defineProperty;
              const originalAddEventListener = EventTarget.prototype.addEventListener;
              const blockedEvent = type => {
                const lower = apply(lowerCase, type, []);
                return lower === 'devicemotion' || lower === 'deviceorientation' ||
                  lower === 'deviceorientationabsolute';
              };
              const filteredAddEventListener = function(type, listener, options) {
                const eventType = string(type);
                if (blockedEvent(eventType)) return;
                return apply(originalAddEventListener, this, [eventType, listener, options]);
              };
              try {
                defineProperty(EventTarget.prototype, 'addEventListener', {
                  configurable: false, writable: false, value: filteredAddEventListener
                });

                // Opt-in player API facade, not a writable global event hook. Never install
                // this in frames or on another origin sharing this WebView's Profile.
                const youtubePlayerOrigins = [];
                if (globalThis.top === globalThis &&
                    youtubePlayerOrigins.includes(location.origin)) {
                  try {
                    const localName = Object.getOwnPropertyDescriptor(Element.prototype, 'localName').get;
                    const ownerDocument = Object.getOwnPropertyDescriptor(Node.prototype, 'ownerDocument').get;
                    const isConnected = Object.getOwnPropertyDescriptor(Node.prototype, 'isConnected').get;
                    const getAttribute = Element.prototype.getAttribute;
                    const playerElement = element => {
                      try {
                        return apply(localName, element, []) === 'div' &&
                          apply(ownerDocument, element, []) === document &&
                          apply(isConnected, element, []) &&
                          apply(getAttribute, element, ['id']) === 'movie_player';
                      } catch (_) { return false; }
                    };
                    defineProperty(HTMLDivElement.prototype, 'addEventListener', {
                      configurable: false, enumerable: true,
                      get: () => filteredAddEventListener,
                      set: function(replacement) {
                        if (!playerElement(this) || typeof replacement !== 'function') {
                          throw denied('Only the YouTube player DIV can install an event facade');
                        }
                        const element = this;
                        let facade;
                        const replace = next => {
                          if (typeof next !== 'function') throw denied('Event facade must be a function');
                          facade = function(type, ...args) {
                            if (this !== element || !playerElement(element)) {
                              throw denied('Event facade belongs to its player DIV');
                            }
                            const eventType = string(type);
                            if (blockedEvent(eventType)) return;
                            const forwarded = [eventType];
                            for (let index = 0; index < args.length; index++) forwarded[index + 1] = args[index];
                            return apply(next, element, forwarded);
                          };
                        };
                        replace(replacement);
                        defineProperty(element, 'addEventListener', {
                          configurable: false, enumerable: true,
                          get: () => facade,
                          set: function(next) {
                            if (this !== element || !playerElement(element)) {
                              throw denied('Event facade belongs to its player DIV');
                            }
                            replace(next);
                          }
                        });
                      }
                    });
                  } catch (_) {
                    // Unsupported DOM descriptors keep the original read-only filter.
                  }
                }
                for (const property of ['ondevicemotion', 'ondeviceorientation',
                    'ondeviceorientationabsolute']) {
                  Object.defineProperty(globalThis, property, {
                    configurable: false, get: () => null, set: () => {}
                  });
                }
              } catch (_) {}

              try {
                Object.defineProperty(globalThis, 'Notification', {
                  value: undefined, configurable: false, writable: false
                });
                Object.defineProperty(globalThis, 'PushManager', {
                  value: undefined, configurable: false, writable: false
                });
                if (globalThis.ServiceWorkerRegistration) {
                  Object.defineProperty(ServiceWorkerRegistration.prototype, 'pushManager', {
                    get: () => undefined, configurable: false
                  });
                  Object.defineProperty(ServiceWorkerRegistration.prototype, 'showNotification', {
                    value: () => Promise.reject(denied('Notifications are permanently disabled')),
                    configurable: false, writable: false
                  });
                }
              } catch (_) {}
            })();
            """;
}
