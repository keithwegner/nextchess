package com.github.keithwegner.chess.ui;

import com.github.keithwegner.chess.engine.CandidateLine;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public final class AnalysisTableModel extends AbstractTableModel {
    private final List<CandidateLine> lines = new ArrayList<>();
    private final String[] columns = {"Move", "Eval", "Principal variation"};

    public void setLines(List<CandidateLine> newLines) {
        lines.clear();
        if (newLines != null) {
            lines.addAll(newLines);
        }
        fireTableDataChanged();
    }

    public CandidateLine lineAt(int row) {
        if (row < 0 || row >= lines.size()) {
            return null;
        }
        return lines.get(row);
    }

    @Override
    public int getRowCount() {
        return lines.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        CandidateLine line = lines.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> line.sanMove();
            case 1 -> line.evalText();
            case 2 -> line.pvSan();
            default -> "";
        };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }
}
