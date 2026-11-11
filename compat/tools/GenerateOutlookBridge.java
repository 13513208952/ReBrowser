package io.github.reoutlook;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Build-only generator: use javac's text-block decoding, not copied/escaped JS literals. */
public final class GenerateOutlookBridge {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]).resolve("extensions/rebrowser");
        Files.createDirectories(output);
        String header = "// Generated from shared CaptureScript; do not edit.\n\"use strict\";\n";
        String bridge = """
                (() => {
                  if (window.top !== window || globalThis.__reOutlookGeckoPageInstalled) return;
                  globalThis.__reOutlookGeckoPageInstalled = true;
                  const requestEvent = "__reoutlook_native_request_v1";
                  const responseEvent = "__reoutlook_native_response_v1";
                  const listeners = new Set();
                  const bridge = {
                    onmessage: null,
                    addEventListener(type, listener) {
                      if (type === "message" && typeof listener === "function") listeners.add(listener);
                    },
                    removeEventListener(type, listener) {
                      if (type === "message") listeners.delete(listener);
                    },
                    postMessage(payload) {
                      document.dispatchEvent(new CustomEvent(requestEvent, {detail: String(payload || "")}));
                    }
                  };
                  document.addEventListener(responseEvent, event => {
                    const message = {data: String(event.detail || "")};
                    if (typeof bridge.onmessage === "function") bridge.onmessage(message);
                    for (const listener of Array.from(listeners)) listener(message);
                  });
                  Object.defineProperty(globalThis, "reOutlookBridge", {
                    value: bridge, configurable: false, enumerable: false, writable: false
                  });
                })();
                """;
        Files.writeString(output.resolve("outlook-page.js"), header + bridge
                + CaptureScript.OWA_SERVICE_RESPONSES, StandardCharsets.UTF_8);
        // DOM-only commands run in the isolated content world: no eval, no browser API in MAIN.
        Files.writeString(output.resolve("outlook-content.js"), header + """
                (() => {
                  if (window.top !== window || globalThis.__reOutlookGeckoPolicy) return;
                  globalThis.__reOutlookGeckoPolicy = true;
                  globalThis.__reOutlookCurrentMessage = () =>
                """ + CaptureScript.CURRENT_MESSAGE + ";\n"
                + "globalThis.__reOutlookScrollMailList = () =>\n"
                + CaptureScript.SCROLL_MAIL_LIST + ";\n})();\n", StandardCharsets.UTF_8);
    }
}
