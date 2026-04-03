package com.github.keithwegner.chess.ui;

import com.github.keithwegner.chess.Move;
import com.github.keithwegner.chess.engine.CandidateLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnalysisTableModelTest {
    @Test
    void modelExposesLineDataAndIgnoresNullInputs() {
        AnalysisTableModel model = new AnalysisTableModel();
        assertEquals(0, model.getRowCount());
        assertEquals(3, model.getColumnCount());
        assertEquals("Move", model.getColumnName(0));
        assertEquals("Eval", model.getColumnName(1));
        assertEquals("Principal variation", model.getColumnName(2));
        assertNull(model.lineAt(-1));
        assertNull(model.lineAt(0));

        CandidateLine line = new CandidateLine(
                Move.fromUci("e2e4"),
                "e4",
                "+0.20",
                20,
                null,
                List.of(Move.fromUci("e2e4"), Move.fromUci("e7e5")),
                "1. e4 1... e5",
                6,
                100L,
                200L);
        model.setLines(List.of(line));
        assertEquals(1, model.getRowCount());
        assertEquals(line, model.lineAt(0));
        assertEquals("e4", model.getValueAt(0, 0));
        assertEquals("+0.20", model.getValueAt(0, 1));
        assertEquals("1. e4 1... e5", model.getValueAt(0, 2));
        assertEquals("", model.getValueAt(0, 9));
        assertFalse(model.isCellEditable(0, 0));

        model.setLines(null);
        assertEquals(0, model.getRowCount());
    }
}
