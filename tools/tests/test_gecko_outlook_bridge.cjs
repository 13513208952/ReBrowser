"use strict";

// Synthetic data only. Run against the Gradle-generated, packaged scripts.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const root = process.argv[2];
if (!root) throw new Error("Usage: node test_gecko_outlook_bridge.cjs <extensions/rebrowser directory>");
class CustomEvent extends Event {
  constructor(type, options = {}) { super(type); this.detail = options.detail; }
}

(async () => {
  const document = new EventTarget();
  document.querySelector = () => null;
  const events = [];
  document.addEventListener("__reoutlook_native_request_v1", event => {
    const value = JSON.parse(event.detail);
    events.push(value);
    if (value.type === "candidates") document.dispatchEvent(new CustomEvent(
      "__reoutlook_native_response_v1", {detail: JSON.stringify({
        type: "backfillDecision", token: value.token, missing: []
      })}));
  });
  let networkCalls = 0;
  const originalFetch = async request => {
    networkCalls++;
    const action = new URL(request.url).searchParams.get("action");
    const body = action === "FindConversation" ? {
      ResponseClass: "Success", ResponseCode: "NoError", TotalConversationsInView: 1,
      Conversations: [{ConversationId: {Id: "fixture-conversation"}, MessageCount: 1}]
    } : {
      ResponseMessages: {Items: [{ResponseClass: "Success", ResponseCode: "NoError",
        Conversation: {ConversationId: {Id: "fixture-conversation"}, TotalConversationNodesCount: 1,
          ConversationNodes: [{Items: [{InternetMessageId: "fixture-message", Subject: "Fixture",
            UniqueBody: {BodyType: "HTML", Value: "<p>synthetic mail</p>"}}]}]}}]}
    };
    return new Response(JSON.stringify({Body: body}));
  };
  const context = vm.createContext({document, CustomEvent, Request, Response, URL, Headers,
    fetch: originalFetch, setTimeout, location: {origin: "https://outlook.live.com",
      href: "https://outlook.live.com/mail/0/"}}, {codeGeneration: {strings: false, wasm: false}});
  context.window = context;
  context.top = context;
  const page = fs.readFileSync(path.join(root, "outlook-page.js"), "utf8");
  vm.runInContext(page, context);
  assert.notEqual(context.fetch, originalFetch, "fetch hook must actually install without eval");
  assert.equal(typeof context.reOutlookBridge.addEventListener, "function");
  let replies = 0;
  const listener = () => replies++;
  context.reOutlookBridge.addEventListener("message", listener);
  document.dispatchEvent(new CustomEvent("__reoutlook_native_response_v1", {detail: "{}"}));
  assert.equal(replies, 1);
  context.reOutlookBridge.removeEventListener("message", listener);
  document.dispatchEvent(new CustomEvent("__reoutlook_native_response_v1", {detail: "{}"}));
  assert.equal(replies, 1);
  const request = (action, body) => new Request("https://outlook.live.com/owa/service.svc?action=" + action,
    {method: "POST", headers: {"x-anchormailbox": "fixture@example.invalid",
      "x-owa-urlpostdata": JSON.stringify({Body: {Paging: {Offset: 0}}})}, body: JSON.stringify(body)});
  const find = await context.fetch(request("FindConversation", {}));
  assert.equal((await find.json()).Body.Conversations.length, 1, "original response remains readable");
  const conversation = await context.fetch(request("GetConversationItems", {
    Body: {Conversations: [{ConversationId: {Id: "fixture-conversation"}}]}
  }));
  assert.ok((await conversation.json()).Body.ResponseMessages);
  await new Promise(resolve => setTimeout(resolve, 80));
  assert.ok(events.some(value => value.type === "account"));
  assert.ok(events.some(value => value.type === "candidates"));
  assert.ok(events.some(value => value.type === "mail" && value.identity === "fixture-message"));
  assert.ok(events.some(value => value.type === "conversationComplete"));
  assert.equal(networkCalls, 2, "no unrequested backfill for already-cached candidates");
  const isolated = vm.createContext({document, location: context.location,
    getComputedStyle: () => ({})}, {codeGeneration: {strings: false, wasm: false}});
  isolated.window = isolated;
  isolated.top = isolated;
  vm.runInContext(fs.readFileSync(path.join(root, "outlook-content.js"), "utf8"), isolated);
  assert.equal(isolated.__reOutlookCurrentMessage(), null);
  assert.equal(isolated.__reOutlookScrollMailList(), "no-mail-row");
  console.log("Gecko Outlook bridge: CSP-safe installation, message listeners, response clones and capture passed");
})().catch(error => { console.error(error); process.exitCode = 1; });
