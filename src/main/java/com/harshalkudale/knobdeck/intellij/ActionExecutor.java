package com.harshalkudale.knobdeck.intellij;

import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import java.awt.Component;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.Nullable;

/** Runs IDE actions for KnobDeck on the UI thread, in the window the user last worked in. */
final class ActionExecutor {
  /** Our action browser. KnobDeck's "Open Action Browser" button asks for this id. */
  static final String ACTION_BROWSER_ID = "KnobDeck.ShowActionBrowser";
  /** The id early KnobDeck plugin builds sent for the action browser. */
  static final String LEGACY_ACTION_BROWSER_ID = "streamdeck.show.action.browser";

  /** How long a request waits for its action before answering "pending". */
  static long waitMs = 5000;

  private ActionExecutor() {}

  record Outcome(int status, String message, boolean pending) {
    static Outcome ok() {
      return new Outcome(200, null, false);
    }

    static Outcome error(int status, String message) {
      return new Outcome(status, message, false);
    }
  }

  /** Runs action {@code actionId}; "Run" / "Debug" with {@code runConfig} start that configuration. */
  static Outcome perform(String actionId, @Nullable String runConfig, boolean focusOnly) {
    String id = resolveId(actionId);
    boolean run = id.equalsIgnoreCase("Run");
    boolean debug = id.equalsIgnoreCase("Debug");
    if ((run || debug) && runConfig != null && !runConfig.isBlank()) {
      return onUiThread(() -> runConfiguration(runConfig.trim(), debug, focusOnly));
    }
    AnAction action = ActionManager.getInstance().getAction(id);
    if (action == null) return Outcome.error(404, "The IDE has no action \"" + actionId + "\". Is the plugin that provides it installed?");
    return onUiThread(() -> invoke(id, action, focusOnly));
  }

  private static String resolveId(String actionId) {
    if (actionId.equals(LEGACY_ACTION_BROWSER_ID) && ActionManager.getInstance().getAction(actionId) == null) {
      return ACTION_BROWSER_ID;
    }
    return actionId;
  }

  /** Run configurations of the project in the window the user last worked in. */
  static List<Map<String, String>> runConfigurations() {
    List<Map<String, String>> items = new ArrayList<>();
    CompletableFuture<Void> done = new CompletableFuture<>();
    ApplicationManager.getApplication().invokeLater(() -> {
      try {
        Project project = currentProject();
        if (project != null) {
          for (RunnerAndConfigurationSettings s : RunManager.getInstance(project).getAllSettings()) {
            if (s.isTemporary()) continue;
            items.add(Map.of("value", s.getName(), "label", s.getName()));
          }
        }
        done.complete(null);
      } catch (Throwable t) {
        done.completeExceptionally(t);
      }
    }, ModalityState.any());
    try {
      done.get(waitMs, TimeUnit.MILLISECONDS);
    } catch (Exception ignored) {
      // Answer with what we have; the list is only a convenience.
    }
    return items;
  }

  private interface UiTask {
    Outcome run() throws Exception;
  }

  /**
   * Runs {@code task} on the UI thread outside modal dialogs. If it hasn't run within
   * {@link #waitMs} (say a modal dialog is open) it stays queued and we answer "pending".
   */
  private static Outcome onUiThread(UiTask task) {
    CompletableFuture<Outcome> result = new CompletableFuture<>();
    ApplicationManager.getApplication().invokeLater(() -> {
      try {
        result.complete(task.run());
      } catch (Throwable t) {
        result.completeExceptionally(t);
      }
    }, ModalityState.nonModal());
    try {
      return result.get(waitMs, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      return new Outcome(202, "The IDE is busy (is a dialog open?); the action will run when it can.", true);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause() == null ? e : e.getCause();
      return Outcome.error(500, cause.getMessage() == null ? cause.toString() : cause.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Outcome.error(500, "Interrupted.");
    }
  }

  private static @Nullable IdeFrame lastFrame() {
    return IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
  }

  private static boolean ideHasFocus() {
    Window active = javax.swing.FocusManager.getCurrentManager().getActiveWindow();
    return active != null;
  }

  /** The component the user last focused in the last focused IDE window: actions act there. */
  private static @Nullable Component contextComponent() {
    IdeFrame frame = lastFrame();
    if (frame == null || frame.getComponent() == null) return null;
    Window window = SwingUtilities.getWindowAncestor(frame.getComponent());
    Component last = window == null ? null : IdeFocusManager.getGlobalInstance().getLastFocusedFor(window);
    return last != null ? last : frame.getComponent();
  }

  private static @Nullable Project currentProject() {
    IdeFrame frame = lastFrame();
    if (frame != null && frame.getProject() != null && !frame.getProject().isDisposed()) return frame.getProject();
    Project[] open = ProjectManager.getInstance().getOpenProjects();
    return open.length > 0 ? open[0] : null;
  }

  private static Outcome invoke(String id, AnAction action, boolean focusOnly) {
    if (focusOnly && !ideHasFocus()) return Outcome.error(409, "The IDE window is not focused (Settings → Tools → KnobDeck).");
    ActionCallback callback = ActionManager.getInstance().tryToExecute(action, null, contextComponent(), ActionPlaces.KEYBOARD_SHORTCUT, true);
    if (callback.isRejected()) {
      return Outcome.error(409, "\"" + id + "\" is not available here right now (e.g. no running debug session or no project).");
    }
    return Outcome.ok();
  }

  private static Outcome runConfiguration(String name, boolean debug, boolean focusOnly) {
    if (focusOnly && !ideHasFocus()) return Outcome.error(409, "The IDE window is not focused (Settings → Tools → KnobDeck).");
    Project project = currentProject();
    if (project == null) return Outcome.error(409, "No project is open.");
    RunnerAndConfigurationSettings settings = RunManager.getInstance(project).findConfigurationByName(name);
    if (settings == null) return Outcome.error(404, "Run configuration \"" + name + "\" was not found in " + project.getName() + ".");
    var executor = debug ? DefaultDebugExecutor.getDebugExecutorInstance() : DefaultRunExecutor.getRunExecutorInstance();
    if (executor == null) executor = ExecutorRegistry.getInstance().getExecutorById(debug ? DefaultDebugExecutor.EXECUTOR_ID : DefaultRunExecutor.EXECUTOR_ID);
    ProgramRunnerUtil.executeConfiguration(settings, executor);
    return Outcome.ok();
  }
}
