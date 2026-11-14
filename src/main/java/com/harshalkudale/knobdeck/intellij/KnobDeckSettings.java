package com.harshalkudale.knobdeck.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

/** IDE-wide companion settings (Settings → Tools → KnobDeck). Not synced between machines. */
@Service(Service.Level.APP)
@State(name = "KnobDeckCompanion", storages = @Storage(value = "knobdeck-companion.xml", roamingType = RoamingType.DISABLED))
public final class KnobDeckSettings implements PersistentStateComponent<KnobDeckSettings.Data> {
  public static final int DEFAULT_PORT = 21420;

  public static final class Data {
    public int port = DEFAULT_PORT;
    public String password = "";
    public boolean enabled = true;
    /** Ignore requests while no IDE window has focus. Off by default: KnobDeck often has focus. */
    public boolean focusOnly = false;
  }

  private Data data = new Data();

  public static KnobDeckSettings getInstance() {
    return ApplicationManager.getApplication().getService(KnobDeckSettings.class);
  }

  @Override
  public @NotNull Data getState() {
    return data;
  }

  @Override
  public void loadState(@NotNull Data state) {
    data = state;
  }

  public int port() {
    return data.port;
  }

  public String password() {
    return data.password == null ? "" : data.password;
  }

  public boolean enabled() {
    return data.enabled;
  }

  public boolean focusOnly() {
    return data.focusOnly;
  }

  public void update(int port, String password, boolean enabled, boolean focusOnly) {
    data.port = port;
    data.password = password == null ? "" : password;
    data.enabled = enabled;
    data.focusOnly = focusOnly;
  }
}
