package com.github.keithwegner.chess.ui;

import com.github.keithwegner.chess.Move;
import com.github.keithwegner.chess.Position;
import com.github.keithwegner.chess.SquareUtil;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardPanelTest {
    @Test
    void boardPanelMapsSquaresHandlesClicksAndPaintsInBothOrientations() {
        BoardPanel panel = new BoardPanel();
        panel.setFont(new Font("Dialog", Font.PLAIN, 12));
        panel.setSize(400, 400);

        paint(panel);

        AtomicInteger clickedSquare = new AtomicInteger(-1);
        AtomicBoolean rightClick = new AtomicBoolean(false);
        panel.dispatchEvent(mouse(panel, 10, 10, false));
        panel.setBoardListener((square, right) -> {
            clickedSquare.set(square);
            rightClick.set(right);
        });
        panel.setPosition(new Position());
        panel.setLegalTargets(null);
        panel.setLegalTargets(Set.of(sq("e4"), sq("e7")));
        panel.setSelectedSquare(sq("e2"));
        panel.setCheckSquare(sq("e8"));
        panel.setLastMove(Move.fromUci("e2e4"));
        panel.setBestMove(new Move(sq("e2"), sq("e2")));

        Point whitePoint = centerForBoardCoords(4, 6);
        assertEquals(sq("e2"), panel.squareAt(whitePoint));
        assertEquals(-1, panel.squareAt(new Point(0, 0)));
        panel.dispatchEvent(mouse(panel, whitePoint.x, whitePoint.y, false));
        assertEquals(sq("e2"), clickedSquare.get());
        assertFalse(rightClick.get());
        panel.dispatchEvent(mouse(panel, 0, 0, false));
        assertEquals(sq("e2"), clickedSquare.get());

        paint(panel);

        panel.setBestMove(Move.fromUci("e2e4"));
        panel.setOrientationWhiteBottom(false);
        assertEquals(sq("d7"), panel.squareAt(whitePoint));
        panel.dispatchEvent(mouse(panel, whitePoint.x, whitePoint.y, true));
        assertEquals(sq("d7"), clickedSquare.get());
        assertTrue(rightClick.get());

        panel.setLastMove(null);
        paint(panel);
    }

    private static MouseEvent mouse(BoardPanel panel, int x, int y, boolean rightClick) {
        return new MouseEvent(
                panel,
                MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(),
                rightClick ? InputEvent.BUTTON3_DOWN_MASK : 0,
                x,
                y,
                1,
                rightClick,
                rightClick ? MouseEvent.BUTTON3 : MouseEvent.BUTTON1);
    }

    private static Point centerForBoardCoords(int boardFile, int boardRankFromTop) {
        int squareSize = 44;
        int origin = 24;
        return new Point(origin + boardFile * squareSize + squareSize / 2,
                origin + boardRankFromTop * squareSize + squareSize / 2);
    }

    private static void paint(BoardPanel panel) {
        BufferedImage image = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            panel.paint(graphics);
        } finally {
            graphics.dispose();
        }
    }

    private static int sq(String square) {
        return SquareUtil.parse(square);
    }
}
