package com.harshalkudale.knobdeck.intellij;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.fields.IntegerField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nullable;

/** Settings → Tools → KnobDeck. */
public final class SettingsConfigurable implements Configurable {
  private JBCheckBox enabled;
  private IntegerField port;
  private JBPasswordField password;
  private JBCheckBox focusOnly;
  private JBLabel status;

  @Override
  public @NlsContexts.ConfigurableName String getDisplayName() {
    return "KnobDeck";
  }

  @Override
  public @Nullable JComponent createComponent() {
    enabled = new JBCheckBox("Let KnobDeck control this IDE");
    port = new IntegerField("Port", 1, 65535);
    port.setColumns(6);
    password = new JBPasswordField();
    password.setColumns(20);
    focusOnly = new JBCheckBox("Only run actions while an IDE window is focused");
    status = new JBLabel();
    status.setForeground(UIUtil.getContextHelpForeground());
    JPanel panel = FormBuilder.createFormBuilder()
        .addComponent(enabled)
        .addLabeledComponent("Port:", port)
        .addLabeledComponent("Password (optional):", password)
        .addComponent(focusOnly)
        .addVerticalGap(8)
        .addComponent(status)
        .addComponent(new JBLabel("<html>Enter the same port and password in KnobDeck's IntelliJ plugin setup. "
            + "The server only accepts connections from this computer.</html>"))
        .addComponentFillVertically(new JPanel(), 0)
        .getPanel();
    reset();
    return panel;
  }

  @Override
  public boolean isModified() {
    KnobDeckSettings s = KnobDeckSettings.getInstance();
    return enabled.isSelected() != s.enabled()
        || port.getValue() != s.port()
        || !new String(password.getPassword()).equals(s.password())
        || focusOnly.isSelected() != s.focusOnly();
  }

  @Override
  public void apply() throws ConfigurationException {
    port.validateContent();
    KnobDeckSettings.getInstance().update(port.getValue(), new String(password.getPassword()), enabled.isSelected(), focusOnly.isSelected());
    ActionServer server = ActionServer.getInstance();
    server.stop();
    server.applySettings();
    status.setText(server.status());
  }

  @Override
  public void reset() {
    KnobDeckSettings s = KnobDeckSettings.getInstance();
    enabled.setSelected(s.enabled());
    port.setValue(s.port());
    password.setText(s.password());
    focusOnly.setSelected(s.focusOnly());
    status.setText(ActionServer.getInstance().status());
  }

  @Override
  public void disposeUIResources() {
    enabled = null;
    port = null;
    password = null;
    focusOnly = null;
    status = null;
  }
}
