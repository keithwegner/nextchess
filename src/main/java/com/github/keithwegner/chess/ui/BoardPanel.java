package com.github.keithwegner.chess.ui;

import com.github.keithwegner.chess.Move;
import com.github.keithwegner.chess.Piece;
import com.github.keithwegner.chess.Position;
import com.github.keithwegner.chess.SquareUtil;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.HashSet;
import java.util.Set;

public final class BoardPanel extends JPanel {
    public interface BoardListener {
        void onSquareClick(int square, boolean rightClick);
    }

    private static final Color LIGHT_SQUARE = new Color(0xF0D9B5);
    private static final Color DARK_SQUARE = new Color(0xB58863);
    private static final Color SELECTED_SQUARE = new Color(0x5DADE2);
    private static final Color LAST_MOVE_SQUARE = new Color(0xF7DC6F);
    private static final Color CHECK_SQUARE = new Color(0xF1948A);
    private static final Color TARGET_DOT = new Color(0x1F618D);
    private static final Color TARGET_CAPTURE = new Color(0x922B21);
    private static final Color ARROW_COLOR = new Color(0x2E86DE);

    private Position position;
    private final Set<Integer> legalTargets = new HashSet<>();
    private int selectedSquare = -1;
    private int checkSquare = -1;
    private int lastMoveFrom = -1;
    private int lastMoveTo = -1;
    private boolean whiteBottom = true;
    private Move bestMove;
    private BoardListener listener;

    public BoardPanel() {
        setOpaque(true);
        setBackground(LIGHT_SQUARE);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (listener == null || position == null) {
                    return;
                }
                int square = squareAt(e.getPoint());
                if (square < 0) {
                    return;
                }
                listener.onSquareClick(square, SwingUtilities.isRightMouseButton(e));
            }
        });
    }

    public void setBoardListener(BoardListener listener) {
        this.listener = listener;
    }

    public void setPosition(Position position) {
        this.position = position;
        repaint();
    }

    public void setLegalTargets(Set<Integer> targets) {
        legalTargets.clear();
        if (targets != null) {
            legalTargets.addAll(targets);
        }
        repaint();
    }

    public void setSelectedSquare(int selectedSquare) {
        this.selectedSquare = selectedSquare;
        repaint();
    }

    public void setCheckSquare(int checkSquare) {
        this.checkSquare = checkSquare;
        repaint();
    }

    public void setLastMove(Move move) {
        if (move == null) {
            this.lastMoveFrom = -1;
            this.lastMoveTo = -1;
        } else {
            this.lastMoveFrom = move.from();
            this.lastMoveTo = move.to();
        }
        repaint();
    }

    public void setOrientationWhiteBottom(boolean whiteBottom) {
        this.whiteBottom = whiteBottom;
        repaint();
    }

    public void setBestMove(Move bestMove) {
        this.bestMove = bestMove;
        repaint();
    }

    public int squareAt(Point point) {
        int squareSize = squareSize();
        int boardPixels = squareSize * 8;
        int x0 = (getWidth() - boardPixels) / 2;
        int y0 = (getHeight() - boardPixels) / 2;
        int x = point.x - x0;
        int y = point.y - y0;
        if (x < 0 || y < 0 || x >= boardPixels || y >= boardPixels) {
            return -1;
        }
        int boardFile = x / squareSize;
        int boardRankFromTop = y / squareSize;
        int file = whiteBottom ? boardFile : 7 - boardFile;
        int rank = whiteBottom ? 7 - boardRankFromTop : boardRankFromTop;
        return SquareUtil.square(file, rank);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int squareSize = squareSize();
        int boardPixels = squareSize * 8;
        int x0 = (getWidth() - boardPixels) / 2;
        int y0 = (getHeight() - boardPixels) / 2;

        drawBoardSquares(g2, x0, y0, squareSize);
        drawCoordinates(g2, x0, y0, squareSize);
        drawBestMoveArrow(g2, x0, y0, squareSize);
        drawTargetHints(g2, x0, y0, squareSize);
        drawPieces(g2, x0, y0, squareSize);

        g2.dispose();
    }

    private void drawBoardSquares(Graphics2D g2, int x0, int y0, int squareSize) {
        for (int boardRankFromTop = 0; boardRankFromTop < 8; boardRankFromTop++) {
            for (int boardFile = 0; boardFile < 8; boardFile++) {
                int file = whiteBottom ? boardFile : 7 - boardFile;
                int rank = whiteBottom ? 7 - boardRankFromTop : boardRankFromTop;
                int square = SquareUtil.square(file, rank);
                Color color = ((file + rank) & 1) == 0 ? LIGHT_SQUARE : DARK_SQUARE;
                if (square == lastMoveFrom || square == lastMoveTo) {
                    color = LAST_MOVE_SQUARE;
                }
                if (square == checkSquare) {
                    color = CHECK_SQUARE;
                }
                if (square == selectedSquare) {
                    color = SELECTED_SQUARE;
                }
                g2.setColor(color);
                g2.fillRect(x0 + boardFile * squareSize, y0 + boardRankFromTop * squareSize, squareSize, squareSize);
            }
        }
    }

    private void drawCoordinates(Graphics2D g2, int x0, int y0, int squareSize) {
        g2.setFont(getFont().deriveFont(Font.PLAIN, Math.max(11f, squareSize * 0.14f)));
        FontMetrics fm = g2.getFontMetrics();
        for (int boardFile = 0; boardFile < 8; boardFile++) {
            int file = whiteBottom ? boardFile : 7 - boardFile;
            String text = String.valueOf((char) ('a' + file));
            int tx = x0 + boardFile * squareSize + squareSize - fm.stringWidth(text) - 4;
            int ty = y0 + 8 * squareSize - 6;
            g2.setColor(((file + (whiteBottom ? 0 : 7)) & 1) == 0 ? DARK_SQUARE : LIGHT_SQUARE);
            g2.drawString(text, tx, ty);
        }
        for (int boardRankFromTop = 0; boardRankFromTop < 8; boardRankFromTop++) {
            int rank = whiteBottom ? 7 - boardRankFromTop : boardRankFromTop;
            String text = String.valueOf(rank + 1);
            int tx = x0 + 4;
            int ty = y0 + boardRankFromTop * squareSize + fm.getAscent() + 2;
            g2.setColor((((whiteBottom ? 0 : 7) + rank) & 1) == 0 ? DARK_SQUARE : LIGHT_SQUARE);
            g2.drawString(text, tx, ty);
        }
    }

    private void drawPieces(Graphics2D g2, int x0, int y0, int squareSize) {
        if (position == null) {
            return;
        }
        Font pieceFont = choosePieceFont(squareSize);
        g2.setFont(pieceFont);
        FontMetrics fm = g2.getFontMetrics();
        for (int boardRankFromTop = 0; boardRankFromTop < 8; boardRankFromTop++) {
            for (int boardFile = 0; boardFile < 8; boardFile++) {
                int file = whiteBottom ? boardFile : 7 - boardFile;
                int rank = whiteBottom ? 7 - boardRankFromTop : boardRankFromTop;
                int square = SquareUtil.square(file, rank);
                Piece piece = position.pieceAt(square);
                if (piece == Piece.NONE) {
                    continue;
                }
                String text = String.valueOf(piece.unicode());
                int textWidth = fm.stringWidth(text);
                int tx = x0 + boardFile * squareSize + (squareSize - textWidth) / 2;
                int ty = y0 + boardRankFromTop * squareSize + (squareSize + fm.getAscent() - fm.getDescent()) / 2;
                g2.setColor(Color.BLACK);
                g2.drawString(text, tx, ty);
            }
        }
    }

    private void drawTargetHints(Graphics2D g2, int x0, int y0, int squareSize) {
        if (position == null) {
            return;
        }
        for (int square : legalTargets) {
            int[] coords = boardCoords(square);
            int px = x0 + coords[0] * squareSize;
            int py = y0 + coords[1] * squareSize;
            Piece piece = position.pieceAt(square);
            if (piece == Piece.NONE) {
                g2.setColor(TARGET_DOT);
                int diameter = Math.max(12, squareSize / 5);
                g2.fillOval(px + (squareSize - diameter) / 2, py + (squareSize - diameter) / 2, diameter, diameter);
            } else {
                g2.setColor(TARGET_CAPTURE);
                g2.setStroke(new BasicStroke(Math.max(3f, squareSize / 16f)));
                int inset = Math.max(6, squareSize / 10);
                g2.drawOval(px + inset, py + inset, squareSize - inset * 2, squareSize - inset * 2);
            }
        }
    }

    private void drawBestMoveArrow(Graphics2D g2, int x0, int y0, int squareSize) {
        if (bestMove == null) {
            return;
        }
        Point from = squareCenter(bestMove.from(), x0, y0, squareSize);
        Point to = squareCenter(bestMove.to(), x0, y0, squareSize);
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double length = Math.hypot(dx, dy);
        if (length < 1.0) {
            return;
        }
        double ux = dx / length;
        double uy = dy / length;
        int padding = Math.max(12, squareSize / 5);
        int x1 = (int) Math.round(from.x + ux * padding);
        int y1 = (int) Math.round(from.y + uy * padding);
        int x2 = (int) Math.round(to.x - ux * padding);
        int y2 = (int) Math.round(to.y - uy * padding);

        g2.setColor(new Color(ARROW_COLOR.getRed(), ARROW_COLOR.getGreen(), ARROW_COLOR.getBlue(), 180));
        g2.setStroke(new BasicStroke(Math.max(6f, squareSize / 8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(x1, y1, x2, y2);

        double arrowSize = Math.max(14, squareSize / 4.0);
        Path2D arrowHead = new Path2D.Double();
        arrowHead.moveTo(0, 0);
        arrowHead.lineTo(-arrowSize, arrowSize / 2.5);
        arrowHead.lineTo(-arrowSize, -arrowSize / 2.5);
        arrowHead.closePath();

        AffineTransform tx = AffineTransform.getTranslateInstance(x2, y2);
        tx.rotate(Math.atan2(dy, dx));
        g2.fill(tx.createTransformedShape(arrowHead));
    }

    private Point squareCenter(int square, int x0, int y0, int squareSize) {
        int[] coords = boardCoords(square);
        return new Point(
                x0 + coords[0] * squareSize + squareSize / 2,
                y0 + coords[1] * squareSize + squareSize / 2);
    }

    private int[] boardCoords(int square) {
        int file = SquareUtil.file(square);
        int rank = SquareUtil.rank(square);
        int boardFile = whiteBottom ? file : 7 - file;
        int boardRankFromTop = whiteBottom ? 7 - rank : rank;
        return new int[]{boardFile, boardRankFromTop};
    }

    private Font choosePieceFont(int squareSize) {
        String[] families = {"Segoe UI Symbol", "Apple Symbols", "Arial Unicode MS", "Noto Sans Symbols 2", "DejaVu Sans"};
        for (String family : families) {
            Font candidate = new Font(family, Font.PLAIN, Math.max(28, (int) (squareSize * 0.74)));
            if (candidate.canDisplay('\u2654')) {
                return candidate;
            }
        }
        return getFont().deriveFont(Font.PLAIN, Math.max(28f, squareSize * 0.74f));
    }

    private int squareSize() {
        int margin = margin();
        return Math.max(40, Math.min((getWidth() - margin * 2) / 8, (getHeight() - margin * 2) / 8));
    }

    private int margin() {
        return 24;
    }
}
