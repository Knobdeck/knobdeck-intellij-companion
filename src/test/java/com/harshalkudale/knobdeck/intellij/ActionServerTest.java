package com.harshalkudale.knobdeck.intellij;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;

/**
 * The companion's HTTP contract, in a headless IDE. When KNOBDECK_INTELLIJ_PLUGIN points at a built
 * KnobDeck `intellij` plugin (and KNOBDECK_INTELLIJ_SETUP at its setup program), the same server is
 * also driven through those real binaries.
 */
public class ActionServerTest extends BasePlatformTestCase {
  private static final String TEST_ACTION = "KnobDeck.Test.Counter";
  private final AtomicInteger presses = new AtomicInteger();
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private int port;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    try (ServerSocket s = new ServerSocket(0)) {
      port = s.getLocalPort();
    }
    KnobDeckSettings.getInstance().update(port, "", true, false);
    ActionServer.getInstance().stop();
    ActionServer.getInstance().applySettings();
    assertEquals(port, ActionServer.getInstance().port());
    ActionManager.getInstance().registerAction(TEST_ACTION, new DumbAwareAction("Counter") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        presses.incrementAndGet();
      }
    });
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      ActionManager.getInstance().unregisterAction(TEST_ACTION);
      ActionServer.getInstance().stop();
    } finally {
      super.tearDown();
    }
  }

  /** Runs {@code work} off the UI thread while this (UI) thread keeps the event queue moving. */
  private static <T> T offUi(Callable<T> work) throws Exception {
    CompletableFuture<T> f = CompletableFuture.supplyAsync(() -> {
      try {
        return work.call();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    long end = System.currentTimeMillis() + 60_000;
    while (!f.isDone()) {
      if (System.currentTimeMillis() > end) fail("timed out");
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      Thread.sleep(10);
    }
    return f.get();
  }

  private HttpResponse<String> get(String path, String... headers) throws Exception {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15));
    for (int i = 0; i < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
    return offUi(() -> http.send(b.build(), HttpResponse.BodyHandlers.ofString()));
  }

  public void testHealth() throws Exception {
    HttpResponse<String> r = get("/health");
    assertEquals(200, r.statusCode());
    assertTrue(r.body(), r.body().contains("\"app\":\"KnobDeck IntelliJ Companion\""));
    assertTrue(r.body(), r.body().contains("\"version\":\"1."));
  }

  public void testRunsAnAction() throws Exception {
    HttpResponse<String> r = get("/api/action/" + TEST_ACTION);
    assertEquals(r.body(), 200, r.statusCode());
    assertEquals(1, presses.get());
  }

  public void testUnknownActionIs404() throws Exception {
    HttpResponse<String> r = get("/api/action/No.Such.Action");
    assertEquals(404, r.statusCode());
    assertTrue(r.body(), r.body().contains("has no action"));
  }

  public void testUnknownRunConfigurationIs404() throws Exception {
    HttpResponse<String> r = get("/api/action/Run?name=Missing%20Config");
    assertEquals(r.body(), 404, r.statusCode());
    assertTrue(r.body(), r.body().contains("Run configuration \\\"Missing Config\\\" was not found"));
  }

  public void testRunConfigurationQuery() throws Exception {
    HttpResponse<String> r = get("/api/query/run-configs");
    assertEquals(200, r.statusCode());
    assertEquals("{\"items\":[]}", r.body());
  }

  public void testPassword() throws Exception {
    KnobDeckSettings.getInstance().update(port, "s3cret", true, false);
    assertEquals(403, get("/health").statusCode());
    assertEquals(403, get("/health", "Authorization", "nope").statusCode());
    assertEquals(200, get("/health", "Authorization", "s3cret").statusCode());
  }

  public void testRefusesWebPages() throws Exception {
    assertEquals(403, get("/api/action/" + TEST_ACTION, "Origin", "https://evil.example").statusCode());
    assertEquals(403, get("/api/action/" + TEST_ACTION, "Sec-Fetch-Site", "cross-site").statusCode());
    assertEquals(0, presses.get());
  }

  public void testDisabledServerStops() throws Exception {
    KnobDeckSettings.getInstance().update(port, "", false, false);
    ActionServer.getInstance().applySettings();
    assertEquals("Off", ActionServer.getInstance().status());
    try {
      get("/health");
      fail("server still answering");
    } catch (Exception expected) {
      assertTrue(expected.getCause() instanceof IOException || expected.getCause() instanceof RuntimeException);
    }
  }

  /** Every IDE action the KnobDeck plugin's buttons send exists in the IDE (or is ours). */
  public void testKnobDeckActionIdsExist() {
    ActionManager am = ActionManager.getInstance();
    for (String id : List.of(ActionExecutor.ACTION_BROWSER_ID, "Vcs.UpdateProject", "NewProject", "Run", "Debug", "StepOver",
        "StepInto", "StepOut", "Resume", "Pause", "SearchEverywhere", "Stop", "ShowProjectStructureSettings", "Debugger.PopFrame")) {
      assertNotNull("missing action " + id, am.getAction(id));
    }
  }

  // ---- the real KnobDeck plugin --------------------------------------------------------------

  private String runCompat(String job) throws Exception {
    Path dir = Path.of(System.getenv("KNOBDECK_COMPAT_DIR"));
    Process p = new ProcessBuilder("node", dir.resolve("cli.mjs").toString(), job).redirectError(ProcessBuilder.Redirect.INHERIT).start();
    String out = offUi(() -> new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    assertEquals(0, (int) offUi(p::waitFor));
    return out;
  }

  public void testKnobDeckPluginDrivesTheCompanion() throws Exception {
    String exe = System.getenv("KNOBDECK_INTELLIJ_PLUGIN");
    if (exe == null || exe.isEmpty() || !Files.exists(Path.of(exe))) {
      System.out.println("skipped: set KNOBDECK_INTELLIJ_PLUGIN");
      return;
    }
    KnobDeckSettings.getInstance().update(port, "pw", true, false);
    String job = "{\"exe\":" + quote(exe) + ",\"env\":{\"IDEA_PORT\":\"" + port + "\",\"IDEA_PASSWORD\":\"pw\"},\"steps\":["
        + "{\"invoke\":\"custom-action\",\"inputs\":{\"actionId\":\"" + TEST_ACTION + "\"}},"
        + "{\"invoke\":\"custom-action\",\"inputs\":{\"actionId\":\"No.Such.Action\"}},"
        + "{\"invoke\":\"run\",\"inputs\":{\"runConfig\":\"Missing Config\"}},"
        + "{\"invoke\":\"custom-action\",\"inputs\":{\"actionId\":\"" + TEST_ACTION + "\",\"port\":\"" + port + "\"}}"
        + "]}";
    String out = runCompat(job);
    assertEquals(out, 2, presses.get());
    assertTrue(out, out.startsWith("{\"results\":[{\"errors\":[]},"));
    // intellij 1.1.1+ shows the companion's reason; 1.1.0 (released before it) only the status code.
    assertTrue(out, out.contains("has no action \\\"No.Such.Action\\\"") || out.contains("{\"errors\":[\"IntelliJ companion plugin returned HTTP 404.\"]},{"));
    assertTrue(out, out.contains("Run configuration \\\"Missing Config\\\" was not found") || out.endsWith("HTTP 404.\"]},{\"errors\":[]}]}\n"));

    KnobDeckSettings.getInstance().update(port, "changed", true, false);
    String denied = runCompat("{\"exe\":" + quote(exe) + ",\"env\":{\"IDEA_PORT\":\"" + port + "\",\"IDEA_PASSWORD\":\"pw\"},\"steps\":[{\"invoke\":\"search-everywhere\"}]}");
    assertTrue(denied, denied.contains("rejected the configured password"));
  }

  public void testKnobDeckSetupVerifiesTheCompanion() throws Exception {
    String exe = System.getenv("KNOBDECK_INTELLIJ_SETUP");
    if (exe == null || exe.isEmpty() || !Files.exists(Path.of(exe))) {
      System.out.println("skipped: set KNOBDECK_INTELLIJ_SETUP");
      return;
    }
    KnobDeckSettings.getInstance().update(port, "pw", true, false);
    String ok = runCompat("{\"setup\":{\"exe\":" + quote(exe) + ",\"inputs\":{\"host\":\"127.0.0.1\",\"port\":\"" + port + "\",\"password\":\"pw\"}}}");
    assertTrue(ok, ok.contains("\"ok\":true"));
    assertTrue(ok, ok.contains("IDEA_PORT=" + port));
    String bad = runCompat("{\"setup\":{\"exe\":" + quote(exe) + ",\"inputs\":{\"host\":\"127.0.0.1\",\"port\":\"" + port + "\",\"password\":\"nope\"}}}");
    assertTrue(bad, bad.contains("\"ok\":false"));
    assertTrue(bad, bad.contains("rejected the configured password"));
  }

  private static String quote(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
