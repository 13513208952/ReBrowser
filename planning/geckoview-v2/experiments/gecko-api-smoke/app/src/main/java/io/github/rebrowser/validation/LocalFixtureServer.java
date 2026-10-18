package io.github.rebrowser.validation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Validation fixture only: ephemeral app-loopback, no file/credential/media access. */
final class LocalFixtureServer implements AutoCloseable {
    private final ServerSocket server;
    LocalFixtureServer() throws Exception {
        server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
        Thread worker = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(3000);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    String request = in.readLine();
                    if (request == null) continue;
                    String target = request.split(" ")[1];
                    String kind = target.startsWith("/a-seed") ? "A-SEED" : target.startsWith("/b-check") ? "B-CHECK" : target.startsWith("/a-read") ? "A-READ" : "OTHER";
                    String assign = kind.equals("A-SEED") ? "document.cookie='rbv=A; path=/; SameSite=Lax';localStorage.setItem('rbv','A');" : kind.equals("B-CHECK") ? "document.cookie='rbv=B; path=/; SameSite=Lax';localStorage.setItem('rbv','B');" : "";
                    String html = "<!doctype html><meta charset=utf-8><title>Synthetic fixture</title><script>" +
                        "window.rbvBeforeCookie=document.cookie;window.rbvBeforeLocal=localStorage.getItem('rbv');" + assign +
                        "window.rbvCase='" + kind + "';window.rbvMainMarker='MAIN_SYNTHETIC_READY';" +
                        "document.documentElement.setAttribute('data-rbv-case',window.rbvCase);document.documentElement.setAttribute('data-rbv-before-cookie',window.rbvBeforeCookie);document.documentElement.setAttribute('data-rbv-before-local',window.rbvBeforeLocal||'');</script>" +
                        "<h1>Local synthetic " + kind + "</h1><p>No account, remote site, audio or cleanup.</p>";
                    byte[] body = html.getBytes(StandardCharsets.UTF_8);
                    OutputStream out = socket.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    out.write(body);out.flush();
                } catch (Exception ignored) { if (server.isClosed()) return; }
            }
        }, "rbv-loopback-fixture");
        worker.setDaemon(true);worker.start();
    }
    String url(String page) { return "http://127.0.0.1:" + server.getLocalPort() + "/" + page; }
    boolean isFixture(String value) { return value != null && value.startsWith("http://127.0.0.1:" + server.getLocalPort() + "/"); }
    @Override public void close() throws Exception { server.close(); }
}
