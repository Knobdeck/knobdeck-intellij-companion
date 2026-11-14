package com.harshalkudale.knobdeck.intellij;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.Nullable;

/**
 * The local HTTP action server KnobDeck's IntelliJ plugin talks to. Listens on loopback only.
 * See PROTOCOL.md in the repository for the wire format.
 */
@Service(Service.Level.APP)
public final class ActionServer implements Disposable {
  private static final Logger LOG = Logger.getInstance(ActionServer.class);
  static final String PLUGIN_ID = "com.harshalkudale.knobdeck.companion";
  private static final Set<String> LOOPBACK_HOSTS = Set.of("127.0.0.1", "localhost", "[::1]");

  private static final String VERSION = readVersion();

  private static String readVersion() {
    try (var in = ActionServer.class.getResourceAsStream("/knobdeck-companion.properties")) {
      var props = new java.util.Properties();
      if (in != null) props.load(in);
      return props.getProperty("version", "dev");
    } catch (IOException e) {
      return "dev";
    }
  }

  private @Nullable HttpServer server;
  private @Nullable ExecutorService executor;
  private int boundPort;
  private @Nullable String lastError;

  public static ActionServer getInstance() {
    return ApplicationManager.getApplication().getService(ActionServer.class);
  }

  /** Starts, restarts or stops the server to match the settings. */
  public synchronized void applySettings() {
    KnobDeckSettings settings = KnobDeckSettings.getInstance();
    if (!settings.enabled()) {
      stop();
      lastError = null;
      return;
    }
    if (server != null && boundPort == settings.port()) return;
    stop();
    try {
      HttpServer created = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), settings.port()), 16);
      created.createContext("/", this::handle);
      executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "KnobDeck action server");
        t.setDaemon(true);
        return t;
      });
      created.setExecutor(executor);
      created.start();
      server = created;
      boundPort = settings.port();
      lastError = null;
      LOG.info("KnobDeck action server listening on 127.0.0.1:" + boundPort);
    } catch (IOException e) {
      lastError = "Port " + settings.port() + " is not available (" + e.getMessage() + "). Is another IDE using it? Pick another port here and in KnobDeck.";
      LOG.warn("KnobDeck action server could not start: " + e.getMessage());
    }
  }

  public synchronized void stop() {
    if (server != null) server.stop(0);
    if (executor != null) executor.shutdownNow();
    server = null;
    executor = null;
    boundPort = 0;
  }

  public synchronized String status() {
    if (server != null) return "Listening on 127.0.0.1:" + boundPort;
    if (lastError != null) return lastError;
    return KnobDeckSettings.getInstance().enabled() ? "Not running" : "Off";
  }

  public synchronized int port() {
    return boundPort;
  }

  @Override
  public void dispose() {
    stop();
  }

  // ---- requests ---------------------------------------------------------------------------------

  private void handle(HttpExchange exchange) throws IOException {
    try {
      KnobDeckSettings settings = KnobDeckSettings.getInstance();
      if (isBrowserOrForeign(exchange)) {
        send(exchange, 403, error("Requests from web pages are not accepted."));
        return;
      }
      String password = settings.password();
      if (!password.isEmpty() && !safeEquals(exchange.getRequestHeaders().getFirst("Authorization"), password)) {
        // 403 is what KnobDeck's plugin reads as "wrong password".
        send(exchange, 403, error("Wrong password. Use the same password in KnobDeck and in Settings → Tools → KnobDeck."));
        return;
      }
      if (!"GET".equals(exchange.getRequestMethod())) {
        send(exchange, 405, error("Only GET is supported."));
        return;
      }

      String path = exchange.getRequestURI().getRawPath();
      if (path.equals("/health")) {
        send(exchange, 200, health());
      } else if (path.equals("/api/query/run-configs")) {
        send(exchange, 200, Map.of("items", ActionExecutor.runConfigurations()));
      } else if (path.startsWith("/api/action/")) {
        String actionId = decodePath(path.substring("/api/action/".length()));
        if (actionId.isBlank()) {
          send(exchange, 400, error("Action ID is required."));
          return;
        }
        ActionExecutor.Outcome outcome = ActionExecutor.perform(actionId, query(exchange, "name"), settings.focusOnly());
        if (outcome.status() == 200) send(exchange, 200, Map.of("ok", true));
        else if (outcome.pending()) send(exchange, 202, Map.of("ok", true, "pending", true, "message", outcome.message()));
        else send(exchange, outcome.status(), error(outcome.message()));
      } else {
        send(exchange, 404, error("Not found."));
      }
    } catch (Throwable t) {
      LOG.warn("KnobDeck request failed", t);
      send(exchange, 500, error(t.getMessage() == null ? t.toString() : t.getMessage()));
    }
  }

  private static Map<String, Object> health() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("ok", true);
    body.put("app", "KnobDeck IntelliJ Companion");
    body.put("version", VERSION);
    body.put("ide", ApplicationInfo.getInstance().getFullApplicationName());
    body.put("build", ApplicationInfo.getInstance().getBuild().asString());
    return body;
  }

  /**
   * Web pages send {@code Origin} / {@code Sec-Fetch-*}, and a DNS-rebinding page arrives under its
   * own host name. KnobDeck sends neither, so this keeps websites from driving the IDE.
   */
  private static boolean isBrowserOrForeign(HttpExchange exchange) {
    var h = exchange.getRequestHeaders();
    if (h.containsKey("Origin") || h.containsKey("Sec-fetch-site") || h.containsKey("Sec-fetch-mode") || h.containsKey("Sec-fetch-dest")) return true;
    String host = h.getFirst("Host");
    if (host == null) return false;
    host = host.toLowerCase();
    String name = host.replaceFirst(":\\d+$", "");
    String port = host.length() > name.length() ? host.substring(name.length() + 1) : "";
    return !LOOPBACK_HOSTS.contains(name) || (!port.isEmpty() && !port.equals(String.valueOf(exchange.getLocalAddress().getPort())));
  }

  private static boolean safeEquals(@Nullable String given, String expected) {
    try {
      MessageDigest sha = MessageDigest.getInstance("SHA-256");
      byte[] a = sha.digest((given == null ? "" : given).getBytes(StandardCharsets.UTF_8));
      byte[] b = sha.digest(expected.getBytes(StandardCharsets.UTF_8));
      return MessageDigest.isEqual(a, b);
    } catch (Exception e) {
      return false;
    }
  }

  private static @Nullable String query(HttpExchange exchange, String key) {
    String raw = exchange.getRequestURI().getRawQuery();
    if (raw == null) return null;
    for (String pair : raw.split("&")) {
      int i = pair.indexOf('=');
      String k = decodeQuery(i < 0 ? pair : pair.substring(0, i));
      if (k.equals(key)) return i < 0 ? "" : decodeQuery(pair.substring(i + 1));
    }
    return null;
  }

  /** A path segment: "+" is a literal plus. */
  private static String decodePath(String s) {
    return URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
  }

  /** A query value: "+" is a space (KnobDeck's HTTP client form-encodes spaces). */
  private static String decodeQuery(String s) {
    return URLDecoder.decode(s, StandardCharsets.UTF_8);
  }

  private static Map<String, Object> error(String message) {
    return Map.of("ok", false, "error", message);
  }

  private static void send(HttpExchange exchange, int status, Object body) throws IOException {
    byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }
}
