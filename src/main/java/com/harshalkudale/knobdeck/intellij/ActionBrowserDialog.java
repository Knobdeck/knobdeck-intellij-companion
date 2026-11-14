package com.harshalkudale.knobdeck.intellij;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import org.jetbrains.annotations.Nullable;

/** Lists every IDE action id, for KnobDeck's "Customized Action". Double-click or Copy copies the id. */
final class ActionBrowserDialog extends DialogWrapper {
  private final DefaultTableModel model = new DefaultTableModel(new Object[] {"Action", "Action ID"}, 0) {
    @Override
    public boolean isCellEditable(int row, int column) {
      return false;
    }
  };
  private final JBTable table = new JBTable(model);
  private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
  private final SearchTextField search = new SearchTextField(false);
  private final JBLabel hint = new JBLabel("Paste the ID into a KnobDeck \"Customized Action\" button.");

  ActionBrowserDialog(@Nullable Project project) {
    super(project);
    setTitle("KnobDeck: Copy an Action ID");
    setOKButtonText("Copy ID");
    init();
    loadRows();
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    table.setRowSorter(sorter);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setShowGrid(false);
    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) doOKAction();
      }
    });
    search.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        String text = search.getText().trim();
        sorter.setRowFilter(text.isEmpty() ? null : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text)));
      }
    });
    JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(8)));
    panel.add(search, BorderLayout.NORTH);
    JBScrollPane scroll = new JBScrollPane(table);
    scroll.setPreferredSize(JBUI.size(640, 420));
    panel.add(scroll, BorderLayout.CENTER);
    panel.add(hint, BorderLayout.SOUTH);
    return panel;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return search.getTextEditor();
  }

  private void loadRows() {
    ActionManager manager = ActionManager.getInstance();
    List<String> ids = new ArrayList<>(manager.getActionIdList(""));
    ids.sort(String.CASE_INSENSITIVE_ORDER);
    for (String id : ids) {
      AnAction action = manager.getAction(id);
      if (action == null) continue;
      String text = action.getTemplatePresentation().getText();
      model.addRow(new Object[] {text == null ? "" : text, id});
    }
  }

  @Override
  protected void doOKAction() {
    int row = table.getSelectedRow();
    if (row < 0) return;
    String id = String.valueOf(model.getValueAt(table.convertRowIndexToModel(row), 1));
    CopyPasteManager.getInstance().setContents(new StringSelection(id));
    hint.setText("Copied \"" + id + "\".");
  }

  @Override
  protected Action [] createActions() {
    return new Action[] {getOKAction(), getCancelAction()};
  }
}
