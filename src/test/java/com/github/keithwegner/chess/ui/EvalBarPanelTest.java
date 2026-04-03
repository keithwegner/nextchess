package com.github.keithwegner.chess.ui;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvalBarPanelTest {
    @Test
    void setEvalClampsValuesAndPaints() throws Exception {
        EvalBarPanel panel = new EvalBarPanel();
        panel.setFont(new Font("Dialog", Font.PLAIN, 12));
        panel.setSize(48, 240);

        panel.setEval(1.5, null);
        assertEquals(1.0, (double) field(panel, "whiteFraction"));
        assertEquals("0.00", field(panel, "label"));

        paint(panel);

        panel.setEval(-1.0, "+1.23");
        assertEquals(0.0, (double) field(panel, "whiteFraction"));
        assertEquals("+1.23", field(panel, "label"));
        paint(panel);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void paint(EvalBarPanel panel) {
        BufferedImage image = new BufferedImage(64, 240, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            panel.paint(graphics);
        } finally {
            graphics.dispose();
        }
    }
}
