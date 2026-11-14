package com.harshalkudale.knobdeck.intellij;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Starts the action server when the IDE starts, or right away when the plugin is installed. */
public final class StartupListener implements AppLifecycleListener, DynamicPluginListener {
  /** Called once at IDE startup, before the first window opens. */
  @Override
  public void appFrameCreated(@NotNull List<String> commandLineArgs) {
    ActionServer.getInstance().applySettings();
  }

  @Override
  public void pluginLoaded(@NotNull IdeaPluginDescriptor plugin) {
    if (ActionServer.PLUGIN_ID.equals(plugin.getPluginId().getIdString())) ActionServer.getInstance().applySettings();
  }
}
