package vn.zalopay.benchmark.core.ui;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/**
 * Simple metadata editor for gRPC request headers.
 *
 * <p>Columns:
 * - Key
 * - Value
 * - Binary (-bin)
 *
 * Serialization strategy:
 * - Outputs JSON string: {"k":"v"}
 * - If Binary column is true, ensures key ends with -bin and value is base64-encoded.
 */
public class MetadataEditorDialog extends JDialog {
    private final DefaultTableModel model;
    private boolean ok;

    public MetadataEditorDialog(Frame owner) {
        super(owner, "Edit Metadata", true);
        this.model = new DefaultTableModel(new Object[] {"Key", "Value", "Binary (-bin)"}, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 2) return Boolean.class;
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        };
        JTable table = new JTable(model);
        setLayout(new BorderLayout());
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel controls = new JPanel(new GridLayout(1, 0, 8, 0));
        JButton addBtn = new JButton("Add");
        JButton rmBtn = new JButton("Remove");
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");
        controls.add(addBtn);
        controls.add(rmBtn);
        controls.add(new JLabel());
        controls.add(okBtn);
        controls.add(cancelBtn);
        add(controls, BorderLayout.SOUTH);

        addBtn.addActionListener(e -> model.addRow(new Object[] {"", "", Boolean.FALSE}));
        rmBtn.addActionListener(
                e -> {
                    int row = table.getSelectedRow();
                    if (row >= 0) model.removeRow(row);
                });
        okBtn.addActionListener(
                e -> {
                    ok = true;
                    setVisible(false);
                });
        cancelBtn.addActionListener(
                e -> {
                    ok = false;
                    setVisible(false);
                });

        setSize(640, 360);
        setLocationRelativeTo(owner);
    }

    public boolean isOk() {
        return ok;
    }

    public void loadFromString(String metadata) {
        // very lenient: try JSON first, then k:v format
        clear();
        if (metadata == null || metadata.trim().isEmpty()) return;
        String s = metadata.trim();
        try {
            if (s.startsWith("{") && s.endsWith("}")) {
                // parse JSON of flat map: {"k":"v"}
                Map<String, Object> map =
                        new com.alibaba.fastjson.JSONObject().parseObject(s, Map.class);
                for (Map.Entry<String, Object> e : map.entrySet()) {
                    String k = e.getKey();
                    String v = String.valueOf(e.getValue());
                    boolean bin = k.endsWith("-bin");
                    model.addRow(new Object[] {k, v, bin});
                }
                return;
            }
        } catch (Exception ignore) {
        }
        // Fallback: key1:value1,key2:value2
        try {
            for (String part : s.split(",")) {
                String[] kv = part.split(":", 2);
                if (kv.length == 2) {
                    String k = kv[0];
                    String v = kv[1];
                    boolean bin = k.endsWith("-bin");
                    model.addRow(new Object[] {k, v, bin});
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this, "Invalid metadata format", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public String toJsonString() {
        Map<String, String> out = new LinkedHashMap<>();
        for (int r = 0; r < model.getRowCount(); r++) {
            String key = String.valueOf(model.getValueAt(r, 0)).trim();
            String value = String.valueOf(model.getValueAt(r, 1));
            boolean bin = Boolean.TRUE.equals(model.getValueAt(r, 2));
            if (key.isEmpty()) continue;
            if (bin && !key.endsWith("-bin")) key = key + "-bin";
            if (bin) {
                // ensure value is base64
                if (!looksLikeBase64(value)) {
                    value = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
                }
            }
            out.put(key, value);
        }
        java.util.Map<String, Object> gen = new java.util.LinkedHashMap<>(out);
        return new com.alibaba.fastjson.JSONObject(gen).toJSONString();
    }

    private boolean looksLikeBase64(String s) {
        try {
            Base64.getDecoder().decode(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void clear() {
        while (model.getRowCount() > 0) model.removeRow(0);
    }
}
