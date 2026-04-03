package com.github.keithwegner.chess.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.TableColumnModel;

import com.github.keithwegner.chess.Move;
import com.github.keithwegner.chess.Piece;
import com.github.keithwegner.chess.Position;
import com.github.keithwegner.chess.Side;
import com.github.keithwegner.chess.SquareUtil;
import com.github.keithwegner.chess.engine.AnalysisResult;
import com.github.keithwegner.chess.engine.CandidateLine;
import com.github.keithwegner.chess.engine.EngineConfig;
import com.github.keithwegner.chess.engine.EngineSupport;
import com.github.keithwegner.chess.engine.PositionAnalyzer;

public final class NextChessFrame extends JFrame {
    private final Position position = new Position();
    private final PositionAnalyzer analyzer = new PositionAnalyzer();
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "analysis-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger analysisToken = new AtomicInteger();

    private final List<HistoryEntry> historyEntries = new ArrayList<>();
    private final List<HistoryEntry> redoEntries = new ArrayList<>();

    private final Set<Integer> legalTargets = new HashSet<>();
    private int selectedSquare = -1;
    private boolean whiteBottom = true;
    private Piece palettePiece = Piece.NONE;
    private boolean eraseSelected = false;
    private Piece heldPiece = Piece.NONE;
    private int heldFromSquare = -1;
    private AnalysisResult analysisResult;

    private final BoardPanel boardPanel = new BoardPanel();
    private final EvalBarPanel evalBarPanel = new EvalBarPanel();
    private final AnalysisTableModel tableModel = new AnalysisTableModel();
    private final JTable linesTable = new JTable(tableModel);
    private final JLabel statusLabel = new JLabel("Ready", SwingConstants.LEFT);
    private final JLabel bestMoveLabel = new JLabel("Best move: —");
    private final JTextArea engineNoteArea = new JTextArea(3, 28);
    private final JTextField enginePathField = new JTextField();
    private final JComboBox<String> engineModeCombo = new JComboBox<>(new String[]{"Built-in Mini Engine", "External UCI Engine"});
    private final JTextField thinkTimeField = new JTextField("2.0", 8);
    private final JTextField depthField = new JTextField("4", 8);
    private final JTextField multiPvField = new JTextField("3", 8);
    private final JTextField threadsField = new JTextField("2", 6);
    private final JTextField hashField = new JTextField("128", 8);
    private final JCheckBox setupModeCheck = new JCheckBox("Setup mode");
    private final JLabel editorHintLabel = new JLabel();
    private final JTextField epField = new JTextField("-", 8);
    private final JTextField fullmoveField = new JTextField("1", 8);
    private final JTextField fenField = new JTextField();
    private final JRadioButton whiteToMoveRadio = new JRadioButton("White");
    private final JRadioButton blackToMoveRadio = new JRadioButton("Black");
    private final JCheckBox whiteCastleKingCheck = new JCheckBox("K");
    private final JCheckBox whiteCastleQueenCheck = new JCheckBox("Q");
    private final JCheckBox blackCastleKingCheck = new JCheckBox("k");
    private final JCheckBox blackCastleQueenCheck = new JCheckBox("q");
    private final JTextArea historyArea = new JTextArea(10, 28);
    private final Map<String, JButton> paletteButtons = new HashMap<>();

    private boolean syncingControls;

    public NextChessFrame() {
        super("Next Chess Desktop — Java");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1240, 820));
        setPreferredSize(new Dimension(1440, 940));

        buildUi();
        bindUi();
        syncControlsFromPosition();
        refreshAll(false);

        pack();
        setLocationRelativeTo(null);
    }

    @Override
    public void dispose() {
        analysisExecutor.shutdownNow();
        super.dispose();
    }

    private void buildUi() {
        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setContentPane(content);

        JPanel left = buildBoardPane();
        JPanel right = buildRightPane();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        splitPane.setResizeWeight(0.68);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);
        content.add(splitPane, BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(0xC7CDD1)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        content.add(statusLabel, BorderLayout.SOUTH);
    }

    private JPanel buildBoardPane() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        evalBarPanel.setPreferredSize(new Dimension(38, 760));
        boardPanel.setPreferredSize(new Dimension(820, 820));
        panel.add(evalBarPanel, BorderLayout.WEST);
        panel.add(boardPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildRightPane() {
        JPanel right = new JPanel(new BorderLayout(0, 10));
        right.setPreferredSize(new Dimension(470, 820));
        right.add(buildActionRow(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Analysis", buildAnalysisTab());
        tabs.addTab("Position", buildPositionTab());
        right.add(tabs, BorderLayout.CENTER);
        return right;
    }

    private JComponent buildActionRow() {
        JPanel actions = new JPanel(new GridLayout(1, 5, 6, 0));
        JButton analyzeButton = new JButton("Analyze");
        JButton playBestButton = new JButton("Play Best");
        JButton undoButton = new JButton("Undo");
        JButton redoButton = new JButton("Redo");
        JButton flipButton = new JButton("Flip");
        analyzeButton.addActionListener(e -> startAnalysis());
        playBestButton.addActionListener(e -> playBestMove());
        undoButton.addActionListener(e -> undoMove());
        redoButton.addActionListener(e -> redoMove());
        flipButton.addActionListener(e -> {
            whiteBottom = !whiteBottom;
            refreshAll(true);
        });
        actions.add(analyzeButton);
        actions.add(playBestButton);
        actions.add(undoButton);
        actions.add(redoButton);
        actions.add(flipButton);
        return actions;
    }

    private JComponent buildAnalysisTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(buildEnginePanel());
        north.add(Box.createVerticalStrut(10));
        north.add(buildBestLinePanel());
        panel.add(north, BorderLayout.NORTH);

        linesTable.setFillsViewportHeight(true);
        linesTable.setRowHeight(24);
        linesTable.setAutoCreateRowSorter(false);
        TableColumnModel columns = linesTable.getColumnModel();
        columns.getColumn(0).setPreferredWidth(90);
        columns.getColumn(1).setPreferredWidth(70);
        columns.getColumn(2).setPreferredWidth(280);
        linesTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    playSelectedCandidate();
                }
            }
        });
        JScrollPane scroll = new JScrollPane(linesTable);
        scroll.setBorder(BorderFactory.createTitledBorder("Candidate Lines"));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildEnginePanel() {
        JPanel engine = new JPanel(new GridBagLayout());
        engine.setBorder(BorderFactory.createTitledBorder("Engine"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addLabel(engine, gbc, row, 0, "Mode");
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        engine.add(engineModeCombo, gbc);

        row++;
        addLabel(engine, gbc, row, 0, "UCI path");
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        engine.add(enginePathField, gbc);
        gbc.weightx = 0.0;
        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> browseEngine());
        gbc.gridx = 2;
        engine.add(browse, gbc);
        JButton detect = new JButton("Detect");
        detect.addActionListener(e -> detectEngine());
        gbc.gridx = 3;
        engine.add(detect, gbc);

        row++;
        addLabel(engine, gbc, row, 0, "Think time (s)");
        gbc.gridx = 1;
        engine.add(thinkTimeField, gbc);
        addLabel(engine, gbc, row, 2, "Depth");
        gbc.gridx = 3;
        engine.add(depthField, gbc);

        row++;
        addLabel(engine, gbc, row, 0, "MultiPV");
        gbc.gridx = 1;
        engine.add(multiPvField, gbc);
        addLabel(engine, gbc, row, 2, "Threads / Hash MB");
        JPanel th = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        threadsField.setColumns(5);
        hashField.setColumns(7);
        th.add(threadsField);
        th.add(new JLabel("/"));
        th.add(hashField);
        gbc.gridx = 3;
        engine.add(th, gbc);

        return engine;
    }

    private JComponent buildBestLinePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Best Line"));
        bestMoveLabel.setFont(bestMoveLabel.getFont().deriveFont(Font.BOLD, 13f));
        panel.add(bestMoveLabel, BorderLayout.NORTH);
        engineNoteArea.setEditable(false);
        engineNoteArea.setLineWrap(true);
        engineNoteArea.setWrapStyleWord(true);
        engineNoteArea.setOpaque(false);
        engineNoteArea.setBorder(BorderFactory.createEmptyBorder());
        panel.add(engineNoteArea, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildPositionTab() {
        JPanel outer = new JPanel(new BorderLayout());
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        panel.add(buildEditorPanel());
        panel.add(Box.createVerticalStrut(10));
        panel.add(buildPalettePanel());
        panel.add(Box.createVerticalStrut(10));
        panel.add(buildMetadataPanel());
        panel.add(Box.createVerticalStrut(10));
        panel.add(buildFenPanel());
        panel.add(Box.createVerticalStrut(10));
        panel.add(buildHistoryPanel());

        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(null);
        outer.add(scroll, BorderLayout.CENTER);
        return outer;
    }

    private JComponent buildEditorPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Editor"));
        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.add(setupModeCheck, BorderLayout.WEST);
        editorHintLabel.setVerticalAlignment(SwingConstants.TOP);
        top.add(editorHintLabel, BorderLayout.CENTER);
        panel.add(top, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new GridLayout(1, 3, 6, 0));
        JButton newGame = new JButton("New game");
        JButton startPos = new JButton("Start position");
        JButton clearBoard = new JButton("Clear board");
        newGame.addActionListener(e -> newGame());
        startPos.addActionListener(e -> newGame());
        clearBoard.addActionListener(e -> clearBoard());
        buttons.add(newGame);
        buttons.add(startPos);
        buttons.add(clearBoard);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildPalettePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createTitledBorder("Piece palette"));
        JPanel grid = new JPanel(new GridLayout(3, 6, 4, 4));
        String[] keys = {"P", "N", "B", "R", "Q", "K", "p", "n", "b", "r", "q", "k"};
        Font paletteFont = boardPanel.getFont().deriveFont(Font.PLAIN, 24f);
        for (String key : keys) {
            Piece piece = Piece.fromFenChar(key.charAt(0));
            JButton button = new JButton(String.valueOf(piece.unicode()));
            button.setFont(paletteFont);
            button.addActionListener(e -> selectPalettePiece(piece, false));
            paletteButtons.put(key, button);
            grid.add(button);
        }
        JButton erase = new JButton("⌫");
        erase.setFont(boardPanel.getFont().deriveFont(Font.BOLD, 18f));
        erase.addActionListener(e -> selectPalettePiece(Piece.NONE, true));
        paletteButtons.put("ERASE", erase);
        grid.add(erase);
        grid.add(new JLabel());
        grid.add(new JLabel());
        grid.add(new JLabel());
        grid.add(new JLabel());
        grid.add(new JLabel());
        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildMetadataPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Position metadata"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        ButtonGroup sideGroup = new ButtonGroup();
        sideGroup.add(whiteToMoveRadio);
        sideGroup.add(blackToMoveRadio);

        int row = 0;
        addLabel(panel, gbc, row, 0, "Side to move");
        JPanel sidePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        sidePanel.add(whiteToMoveRadio);
        sidePanel.add(blackToMoveRadio);
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 3;
        panel.add(sidePanel, gbc);

        row++;
        addLabel(panel, gbc, row, 0, "Castling");
        JPanel castlingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        castlingPanel.add(whiteCastleKingCheck);
        castlingPanel.add(whiteCastleQueenCheck);
        castlingPanel.add(blackCastleKingCheck);
        castlingPanel.add(blackCastleQueenCheck);
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        panel.add(castlingPanel, gbc);
        addLabel(panel, gbc, row, 2, "Fullmove no.");
        gbc.gridx = 3;
        panel.add(fullmoveField, gbc);

        row++;
        addLabel(panel, gbc, row, 0, "En passant");
        gbc.gridx = 1;
        panel.add(epField, gbc);
        return panel;
    }

    private JComponent buildFenPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createTitledBorder("FEN"));
        panel.add(fenField, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new GridLayout(1, 3, 6, 0));
        JButton load = new JButton("Load FEN");
        JButton paste = new JButton("Paste & Load");
        JButton copy = new JButton("Copy FEN");
        load.addActionListener(e -> loadFenFromField());
        paste.addActionListener(e -> pasteAndLoadFen());
        copy.addActionListener(e -> copyFen());
        buttons.add(load);
        buttons.add(paste);
        buttons.add(copy);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Move history"));
        historyArea.setEditable(false);
        historyArea.setLineWrap(true);
        historyArea.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(historyArea);
        panel.add(scroll, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(420, 220));
        return panel;
    }

    private void bindUi() {
        boardPanel.setBoardListener(this::onBoardClick);
        setupModeCheck.addActionListener(e -> toggleSetupMode());
        engineModeCombo.addActionListener(e -> updateEnginePathState());
        fenField.addActionListener(e -> loadFenFromField());

        whiteToMoveRadio.addActionListener(e -> applyMetadataFromControlsIfEditing());
        blackToMoveRadio.addActionListener(e -> applyMetadataFromControlsIfEditing());
        whiteCastleKingCheck.addActionListener(e -> applyMetadataFromControlsIfEditing());
        whiteCastleQueenCheck.addActionListener(e -> applyMetadataFromControlsIfEditing());
        blackCastleKingCheck.addActionListener(e -> applyMetadataFromControlsIfEditing());
        blackCastleQueenCheck.addActionListener(e -> applyMetadataFromControlsIfEditing());
        epField.addActionListener(e -> applyMetadataFromControlsIfEditing());
        fullmoveField.addActionListener(e -> applyMetadataFromControlsIfEditing());
        FocusAdapter metadataFocus = new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                applyMetadataFromControlsIfEditing();
            }
        };
        epField.addFocusListener(metadataFocus);
        fullmoveField.addFocusListener(metadataFocus);
    }

    private void updateEnginePathState() {
        boolean external = engineModeCombo.getSelectedIndex() == 1;
        enginePathField.setEnabled(external);
    }

    private void selectPalettePiece(Piece piece, boolean erase) {
        if (heldPiece != Piece.NONE) {
            restoreHeldPiece();
        }
        this.palettePiece = piece == null ? Piece.NONE : piece;
        this.eraseSelected = erase;
        updatePaletteButtonHighlights();
        updateEditorHint();
    }

    private void updatePaletteButtonHighlights() {
        for (Map.Entry<String, JButton> entry : paletteButtons.entrySet()) {
            JButton button = entry.getValue();
            boolean selected;
            if ("ERASE".equals(entry.getKey())) {
                selected = eraseSelected;
            } else {
                selected = palettePiece != Piece.NONE && entry.getKey().charAt(0) == palettePiece.fenChar() && !eraseSelected;
            }
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(selected ? new java.awt.Color(0x2E86DE) : new java.awt.Color(0xC7CDD1), selected ? 2 : 1),
                    BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        }
    }

    private void onBoardClick(int square, boolean rightClick) {
        if (setupModeCheck.isSelected()) {
            handleSetupClick(square, rightClick);
        } else {
            handlePlayClick(square, rightClick);
        }
    }

    private void handleSetupClick(int square, boolean rightClick) {
        if (rightClick || eraseSelected) {
            position.setPieceAt(square, Piece.NONE);
            resetPositionHistory();
            refreshAll(false);
            setStatus("Removed piece from " + SquareUtil.name(square) + ".");
            return;
        }

        if (heldPiece != Piece.NONE) {
            position.setPieceAt(square, heldPiece);
            heldPiece = Piece.NONE;
            heldFromSquare = -1;
            resetPositionHistory();
            refreshAll(false);
            setStatus("Placed piece on " + SquareUtil.name(square) + ".");
            return;
        }

        if (palettePiece != Piece.NONE) {
            position.setPieceAt(square, palettePiece);
            resetPositionHistory();
            refreshAll(false);
            setStatus("Placed " + palettePiece.unicode() + " on " + SquareUtil.name(square) + ".");
            return;
        }

        Piece existing = position.pieceAt(square);
        if (existing != Piece.NONE) {
            heldPiece = existing;
            heldFromSquare = square;
            position.setPieceAt(square, Piece.NONE);
            resetPositionHistory();
            refreshAll(false);
            setStatus("Picked up piece from " + SquareUtil.name(square) + ".");
        }
    }

    private void handlePlayClick(int square, boolean rightClick) {
        if (rightClick) {
            clearSelection();
            return;
        }
        Piece clicked = position.pieceAt(square);
        if (selectedSquare < 0) {
            if (clicked != Piece.NONE && clicked.side() == position.sideToMove()) {
                selectSquare(square);
            }
            return;
        }
        if (square == selectedSquare) {
            clearSelection();
            return;
        }
        if (clicked != Piece.NONE && clicked.side() == position.sideToMove()) {
            selectSquare(square);
            return;
        }
        List<Move> matchingMoves = new ArrayList<>();
        for (Move move : position.generateLegalMovesFrom(selectedSquare)) {
            if (move.to() == square) {
                matchingMoves.add(move);
            }
        }
        if (matchingMoves.isEmpty()) {
            clearSelection();
            return;
        }
        Move chosen = matchingMoves.size() == 1 ? matchingMoves.get(0) : promptForPromotion(matchingMoves);
        if (chosen != null) {
            applyMove(chosen);
        }
    }

    private Move promptForPromotion(List<Move> options) {
        String[] labels = new String[options.size()];
        for (int i = 0; i < options.size(); i++) {
            labels[i] = options.get(i).promotion().type().name().substring(0, 1)
                    + options.get(i).promotion().type().name().substring(1).toLowerCase();
        }
        String selected = (String) JOptionPane.showInputDialog(
                this,
                "Choose a promotion piece:",
                "Promotion",
                JOptionPane.PLAIN_MESSAGE,
                null,
                labels,
                labels[0]);
        if (selected == null) {
            return null;
        }
        for (int i = 0; i < labels.length; i++) {
            if (labels[i].equals(selected)) {
                return options.get(i);
            }
        }
        return null;
    }

    private void selectSquare(int square) {
        selectedSquare = square;
        legalTargets.clear();
        for (Move move : position.generateLegalMovesFrom(square)) {
            legalTargets.add(move.to());
        }
        refreshAll(true);
    }

    private void clearSelection() {
        selectedSquare = -1;
        legalTargets.clear();
        refreshAll(true);
    }

    private void applyMove(Move move) {
        try {
            String san = position.moveToSan(move);
            position.makeMove(move);
            historyEntries.add(new HistoryEntry(move, san));
            redoEntries.clear();
            selectedSquare = -1;
            legalTargets.clear();
            refreshAll(false);
            setStatus("Played " + san + ".");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Move error", JOptionPane.ERROR_MESSAGE);
            clearSelection();
        }
    }

    private void undoMove() {
        if (historyEntries.isEmpty()) {
            return;
        }
        position.undoMove();
        HistoryEntry entry = historyEntries.remove(historyEntries.size() - 1);
        redoEntries.add(entry);
        selectedSquare = -1;
        legalTargets.clear();
        refreshAll(false);
        setStatus("Undid " + entry.san() + ".");
    }

    private void redoMove() {
        if (redoEntries.isEmpty()) {
            return;
        }
        HistoryEntry entry = redoEntries.remove(redoEntries.size() - 1);
        position.makeMove(entry.move());
        historyEntries.add(entry);
        selectedSquare = -1;
        legalTargets.clear();
        refreshAll(false);
        setStatus("Redid " + entry.san() + ".");
    }

    private void playBestMove() {
        CandidateLine best = analysisResult == null ? null : analysisResult.bestLine();
        if (best == null) {
            return;
        }
        if (!position.toFen().equals(analysisResult.sourceFen())) {
            setStatus("Analysis is stale for the current position.");
            return;
        }
        if (!position.isMoveLegal(best.move())) {
            setStatus("Best move is not legal in the current position.");
            return;
        }
        applyMove(best.move());
    }

    private void playSelectedCandidate() {
        int row = linesTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        CandidateLine line = tableModel.lineAt(linesTable.convertRowIndexToModel(row));
        if (line == null) {
            return;
        }
        if (!position.isMoveLegal(line.move())) {
            setStatus("Selected line is stale for the current position.");
            return;
        }
        applyMove(line.move());
    }

    private void toggleSetupMode() {
        if (!setupModeCheck.isSelected()) {
            restoreHeldPiece();
        }
        selectedSquare = -1;
        legalTargets.clear();
        refreshAll(true);
    }

    private void restoreHeldPiece() {
        if (heldPiece != Piece.NONE && heldFromSquare >= 0 && position.pieceAt(heldFromSquare) == Piece.NONE) {
            position.setPieceAt(heldFromSquare, heldPiece);
        }
        heldPiece = Piece.NONE;
        heldFromSquare = -1;
    }

    private void applyMetadataFromControlsIfEditing() {
        if (syncingControls || !setupModeCheck.isSelected()) {
            return;
        }
        applyMetadataFromControls();
    }

    private void applyMetadataFromControls() {
        position.setSideToMove(whiteToMoveRadio.isSelected() ? Side.WHITE : Side.BLACK);
        position.setWhiteCastleKing(whiteCastleKingCheck.isSelected());
        position.setWhiteCastleQueen(whiteCastleQueenCheck.isSelected());
        position.setBlackCastleKing(blackCastleKingCheck.isSelected());
        position.setBlackCastleQueen(blackCastleQueenCheck.isSelected());
        String ep = epField.getText().trim();
        try {
            position.setEnPassantSquare(ep.isEmpty() || ep.equals("-") ? -1 : SquareUtil.parse(ep));
        } catch (Exception ex) {
            position.setEnPassantSquare(-1);
        }
        try {
            position.setFullmoveNumber(Integer.parseInt(fullmoveField.getText().trim()));
        } catch (Exception ex) {
            position.setFullmoveNumber(1);
        }
        resetPositionHistory();
        refreshAll(false);
    }

    private void newGame() {
        position.loadFromFen(Position.startFen());
        resetPositionHistory();
        heldPiece = Piece.NONE;
        heldFromSquare = -1;
        clearPaletteSelection();
        refreshAll(false);
        setStatus("New game loaded.");
    }

    private void clearBoard() {
        Position empty = Position.empty();
        position.loadFromFen(empty.toFen());
        resetPositionHistory();
        heldPiece = Piece.NONE;
        heldFromSquare = -1;
        clearPaletteSelection();
        refreshAll(false);
        setStatus("Board cleared.");
    }

    private void resetPositionHistory() {
        position.resetHistory();
        historyEntries.clear();
        redoEntries.clear();
    }

    private void clearPaletteSelection() {
        palettePiece = Piece.NONE;
        eraseSelected = false;
        updatePaletteButtonHighlights();
    }

    private void loadFenFromField() {
        try {
            position.loadFromFen(fenField.getText().trim());
            resetPositionHistory();
            heldPiece = Piece.NONE;
            heldFromSquare = -1;
            clearPaletteSelection();
            selectedSquare = -1;
            legalTargets.clear();
            refreshAll(false);
            if (!position.isAnalyzable()) {
                setStatus("Loaded FEN, but the position is not valid for analysis.");
            } else {
                setStatus("FEN loaded.");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid FEN", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void pasteAndLoadFen() {
        try {
            String text = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            fenField.setText(text == null ? "" : text.trim());
            loadFenFromField();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Clipboard does not contain text.", "Clipboard", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void copyFen() {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(position.toFen()), null);
        setStatus("Copied FEN to clipboard.");
    }

    private void browseEngine() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a UCI engine executable");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path selected = chooser.getSelectedFile().toPath();
            enginePathField.setText(selected.toString());
            engineModeCombo.setSelectedIndex(1);
        }
    }

    private void detectEngine() {
        String detected = EngineSupport.detectDefaultEnginePath();
        if (detected.isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "No Stockfish executable was found in the common locations this app checks.",
                    "Engine not found",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        enginePathField.setText(detected);
        engineModeCombo.setSelectedIndex(1);
        setStatus("Detected engine: " + detected);
    }

    private void startAnalysis() {
        if (!position.isAnalyzable()) {
            JOptionPane.showMessageDialog(this, position.validityMessage(), "Invalid position", JOptionPane.WARNING_MESSAGE);
            return;
        }
        EngineConfig config = buildEngineConfig();
        int token = analysisToken.incrementAndGet();
        Position snapshot = position.copy();
        setStatus("Analyzing position...");
        analysisExecutor.submit(() -> {
            AnalysisResult result = analyzer.analyze(snapshot, config);
            SwingUtilities.invokeLater(() -> {
                if (analysisToken.get() != token) {
                    return;
                }
                analysisResult = result;
                refreshAll(false);
                setStatus("Analysis complete with " + result.engineName() + ".");
            });
        });
    }

    private EngineConfig buildEngineConfig() {
        EngineConfig config = new EngineConfig();
        config.setMode(engineModeCombo.getSelectedIndex() == 1 ? EngineConfig.Mode.UCI : EngineConfig.Mode.BUILTIN);
        config.setEnginePath(enginePathField.getText().trim());
        config.setThinkTimeSeconds(parseDouble(thinkTimeField.getText(), 2.0));
        config.setDepth(parseInt(depthField.getText(), 4));
        config.setMultiPv(parseInt(multiPvField.getText(), 3));
        config.setThreads(parseInt(threadsField.getText(), 2));
        config.setHashMb(parseInt(hashField.getText(), 128));
        return config;
    }

    private int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private double parseDouble(String text, double fallback) {
        try {
            return Double.parseDouble(text.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private void syncControlsFromPosition() {
        syncingControls = true;
        try {
            whiteToMoveRadio.setSelected(position.sideToMove() == Side.WHITE);
            blackToMoveRadio.setSelected(position.sideToMove() == Side.BLACK);
            whiteCastleKingCheck.setSelected(position.whiteCastleKing());
            whiteCastleQueenCheck.setSelected(position.whiteCastleQueen());
            blackCastleKingCheck.setSelected(position.blackCastleKing());
            blackCastleQueenCheck.setSelected(position.blackCastleQueen());
            epField.setText(position.enPassantSquare() >= 0 ? SquareUtil.name(position.enPassantSquare()) : "-");
            fullmoveField.setText(String.valueOf(position.fullmoveNumber()));
            fenField.setText(position.toFen());
        } finally {
            syncingControls = false;
        }
    }

    private void refreshAll(boolean drawOnly) {
        syncControlsFromPosition();
        updateEditorHint();
        updatePaletteButtonHighlights();

        boardPanel.setPosition(position);
        boardPanel.setSelectedSquare(selectedSquare);
        boardPanel.setLegalTargets(legalTargets);
        boardPanel.setOrientationWhiteBottom(whiteBottom);
        boardPanel.setLastMove(historyEntries.isEmpty() ? null : historyEntries.get(historyEntries.size() - 1).move());
        boardPanel.setCheckSquare(position.isKingInCheck(position.sideToMove()) ? position.findKing(position.sideToMove()) : -1);

        boolean analysisMatches = analysisResult != null && analysisResult.sourceFen().equals(position.toFen());
        CandidateLine best = analysisMatches && analysisResult != null ? analysisResult.bestLine() : null;
        boardPanel.setBestMove(best == null ? null : best.move());

        if (best != null) {
            bestMoveLabel.setText("Best move: " + best.sanMove() + "   " + best.evalText());
            engineNoteArea.setText((analysisResult == null ? "" : analysisResult.engineName())
                    + (analysisResult != null && !analysisResult.note().isBlank() ? "\n" + analysisResult.note() : ""));
            evalBarPanel.setEval(EngineSupport.scoreToBarFraction(best.scoreCpWhite(), best.mateWhite()), best.evalText());
        } else {
            bestMoveLabel.setText("Best move: —");
            engineNoteArea.setText(analysisResult == null ? "" : analysisResult.note());
            evalBarPanel.setEval(0.5, "0.00");
        }
        tableModel.setLines(analysisResult == null ? List.of() : analysisResult.lines());
        historyArea.setText(formatHistoryText());
        historyArea.setCaretPosition(historyArea.getDocument().getLength());

        enginePathField.setEnabled(engineModeCombo.getSelectedIndex() == 1);
        setEditorControlsEnabled(setupModeCheck.isSelected());

        if (!drawOnly) {
            revalidate();
        }
        repaint();
    }

    private void setEditorControlsEnabled(boolean enabled) {
        for (Component component : List.of(
                whiteToMoveRadio, blackToMoveRadio,
                whiteCastleKingCheck, whiteCastleQueenCheck, blackCastleKingCheck, blackCastleQueenCheck,
                epField, fullmoveField)) {
            component.setEnabled(enabled);
        }
        for (JButton button : paletteButtons.values()) {
            button.setEnabled(enabled);
        }
    }

    private void updateEditorHint() {
        if (!setupModeCheck.isSelected()) {
            editorHintLabel.setText("Setup mode is off. Board clicks use legal moves.");
            return;
        }
        if (heldPiece != Piece.NONE) {
            editorHintLabel.setText("Editing: click a square to drop " + heldPiece.unicode() + ".");
            return;
        }
        if (eraseSelected) {
            editorHintLabel.setText("Editing: click a square to remove its piece. Right-click also removes.");
            return;
        }
        if (palettePiece != Piece.NONE) {
            editorHintLabel.setText("Editing: click a square to place " + palettePiece.unicode() + ".");
            return;
        }
        editorHintLabel.setText("Editing: choose a piece from the palette, or click a board piece to pick it up.");
    }

    private String formatHistoryText() {
        if (historyEntries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < historyEntries.size(); i++) {
            if (i % 2 == 0) {
                sb.append((i / 2) + 1).append('.').append(' ');
            }
            sb.append(historyEntries.get(i).san()).append(' ');
        }
        return sb.toString().trim();
    }

    private void addLabel(JPanel panel, GridBagConstraints gbc, int row, int col, String text) {
        gbc.gridx = col;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        panel.add(new JLabel(text), gbc);
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private record HistoryEntry(Move move, String san) {
    }
}
