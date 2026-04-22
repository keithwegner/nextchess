const ui = {};

let state = null;
let orientationWhiteBottom = true;
let selectedSquare = "";
let activeMode = "play";
let setupPieceFen = "P";
let eraseMode = false;
let promotionChoices = [];
let busy = false;
let toastTimer = 0;

const setupPieces = [
    {fen: "P", label: "White pawn"},
    {fen: "N", label: "White knight"},
    {fen: "B", label: "White bishop"},
    {fen: "R", label: "White rook"},
    {fen: "Q", label: "White queen"},
    {fen: "K", label: "White king"},
    {fen: "p", label: "Black pawn"},
    {fen: "n", label: "Black knight"},
    {fen: "b", label: "Black bishop"},
    {fen: "r", label: "Black rook"},
    {fen: "q", label: "Black queen"},
    {fen: "k", label: "Black king"}
];

const promotionLabels = {
    QUEEN: "Queen",
    ROOK: "Rook",
    BISHOP: "Bishop",
    KNIGHT: "Knight"
};

document.addEventListener("DOMContentLoaded", () => {
    cacheUi();
    bindEvents();
    renderPalette();
    refreshState();
});

function cacheUi() {
    ui.statusPill = document.getElementById("status-pill");
    ui.refreshButton = document.getElementById("refresh-button");
    ui.newGameButton = document.getElementById("new-game-button");
    ui.clearBoardButton = document.getElementById("clear-board-button");
    ui.undoButton = document.getElementById("undo-button");
    ui.redoButton = document.getElementById("redo-button");
    ui.flipButton = document.getElementById("flip-button");
    ui.playModeButton = document.getElementById("play-mode-button");
    ui.setupModeButton = document.getElementById("setup-mode-button");
    ui.sideToMove = document.getElementById("side-to-move");
    ui.positionHealth = document.getElementById("position-health");
    ui.bestLineSummary = document.getElementById("best-line-summary");
    ui.boardCaptionText = document.getElementById("board-caption-text");
    ui.board = document.getElementById("board");
    ui.boardArrow = document.getElementById("board-arrow");
    ui.evalFill = document.getElementById("eval-fill");
    ui.evalLabel = document.getElementById("eval-label");
    ui.analysisBadge = document.getElementById("analysis-badge");
    ui.analyzeButton = document.getElementById("analyze-button");
    ui.playBestButton = document.getElementById("play-best-button");
    ui.analysisNote = document.getElementById("analysis-note");
    ui.analysisLines = document.getElementById("analysis-lines");
    ui.setupBadge = document.getElementById("setup-badge");
    ui.piecePalette = document.getElementById("piece-palette");
    ui.metadataForm = document.getElementById("metadata-form");
    ui.metadataSide = document.getElementById("metadata-side");
    ui.castleWk = document.getElementById("castle-wk");
    ui.castleWq = document.getElementById("castle-wq");
    ui.castleBk = document.getElementById("castle-bk");
    ui.castleBq = document.getElementById("castle-bq");
    ui.epSquare = document.getElementById("ep-square");
    ui.halfmoveClock = document.getElementById("halfmove-clock");
    ui.fullmoveNumber = document.getElementById("fullmove-number");
    ui.engineMode = document.getElementById("engine-mode");
    ui.enginePath = document.getElementById("engine-path");
    ui.detectEngineButton = document.getElementById("detect-engine-button");
    ui.thinkTime = document.getElementById("think-time");
    ui.depth = document.getElementById("depth");
    ui.multiPv = document.getElementById("multi-pv");
    ui.threads = document.getElementById("threads");
    ui.hashMb = document.getElementById("hash-mb");
    ui.fenInput = document.getElementById("fen-input");
    ui.loadFenButton = document.getElementById("load-fen-button");
    ui.copyFenButton = document.getElementById("copy-fen-button");
    ui.historyGrid = document.getElementById("history-grid");
    ui.toast = document.getElementById("toast");
    ui.promotionModal = document.getElementById("promotion-modal");
    ui.promotionGrid = document.getElementById("promotion-grid");
    ui.promotionCancelButton = document.getElementById("promotion-cancel-button");
}

function bindEvents() {
    ui.refreshButton.addEventListener("click", refreshState);
    ui.newGameButton.addEventListener("click", () => postAction("/api/new-game"));
    ui.clearBoardButton.addEventListener("click", () => postAction("/api/clear-board"));
    ui.undoButton.addEventListener("click", () => postAction("/api/undo"));
    ui.redoButton.addEventListener("click", () => postAction("/api/redo"));
    ui.flipButton.addEventListener("click", () => {
        orientationWhiteBottom = !orientationWhiteBottom;
        render();
    });

    ui.playModeButton.addEventListener("click", () => setMode("play"));
    ui.setupModeButton.addEventListener("click", () => setMode("setup"));

    ui.analyzeButton.addEventListener("click", () => postAction("/api/analyze", collectEngineForm()));
    ui.playBestButton.addEventListener("click", () => postAction("/api/play-best"));
    ui.detectEngineButton.addEventListener("click", () => postAction("/api/engine/detect"));
    ui.engineMode.addEventListener("change", updateEnginePathState);

    ui.metadataForm.addEventListener("submit", event => {
        event.preventDefault();
        postAction("/api/setup/metadata", collectMetadataForm());
    });

    ui.loadFenButton.addEventListener("click", () => postAction("/api/load-fen", {fen: ui.fenInput.value}));
    ui.fenInput.addEventListener("keydown", event => {
        if (event.key === "Enter") {
            event.preventDefault();
            postAction("/api/load-fen", {fen: ui.fenInput.value});
        }
    });
    ui.copyFenButton.addEventListener("click", copyFen);

    ui.piecePalette.addEventListener("click", event => {
        const button = event.target.closest(".palette-button");
        if (!button) {
            return;
        }
        eraseMode = button.dataset.erase === "true";
        setupPieceFen = button.dataset.piece || setupPieceFen;
        activeMode = "setup";
        selectedSquare = "";
        renderPalette();
        render();
    });

    ui.board.addEventListener("click", event => {
        const squareButton = event.target.closest(".square");
        if (!squareButton) {
            return;
        }
        handleBoardClick(squareButton.dataset.square);
    });

    ui.analysisLines.addEventListener("click", event => {
        const lineButton = event.target.closest(".line");
        if (!lineButton || lineButton.disabled) {
            return;
        }
        postAction("/api/move", {uci: lineButton.dataset.move});
    });

    ui.promotionGrid.addEventListener("click", event => {
        const option = event.target.closest(".promotion-option");
        if (!option) {
            return;
        }
        const choice = promotionChoices.find(entry => entry.uci === option.dataset.uci);
        closePromotionModal();
        if (choice) {
            postAction("/api/move", {uci: choice.uci});
        }
    });
    ui.promotionCancelButton.addEventListener("click", closePromotionModal);
    ui.promotionModal.addEventListener("click", event => {
        if (event.target instanceof HTMLElement && event.target.dataset.closeModal === "true") {
            closePromotionModal();
        }
    });
    window.addEventListener("keydown", event => {
        if (event.key === "Escape") {
            closePromotionModal();
            selectedSquare = "";
            render();
        }
    });
    window.addEventListener("resize", () => {
        if (state) {
            renderEval();
        }
    });
}

async function refreshState() {
    await request("/api/state", "GET");
}

async function postAction(path, values = {}) {
    await request(path, "POST", values);
}

async function request(path, method, values = {}) {
    if (busy) {
        return;
    }
    setBusy(true);
    try {
        const options = {method, headers: {"Accept": "application/json"}};
        if (method === "POST") {
            options.headers["Content-Type"] = "application/x-www-form-urlencoded;charset=UTF-8";
            options.body = new URLSearchParams(values).toString();
        }
        const response = await fetch(path, options);
        const payload = await response.json();
        if (payload.state) {
            state = payload.state;
            syncInputsFromState();
            normalizeSelection();
        }
        if (!response.ok || !payload.ok) {
            showToast(payload.error || "Request failed.", true);
        }
    } catch (error) {
        showToast(error instanceof Error ? error.message : "Unable to reach the local server.", true);
    } finally {
        setBusy(false);
        if (state) {
            render();
        }
    }
}

function setMode(mode) {
    activeMode = mode;
    selectedSquare = "";
    render();
}

function collectEngineForm() {
    return {
        mode: ui.engineMode.value,
        enginePath: ui.enginePath.value,
        thinkTimeSeconds: ui.thinkTime.value,
        depth: ui.depth.value,
        multiPv: ui.multiPv.value,
        threads: ui.threads.value,
        hashMb: ui.hashMb.value
    };
}

function collectMetadataForm() {
    return {
        sideToMove: ui.metadataSide.value,
        whiteCastleKing: String(ui.castleWk.checked),
        whiteCastleQueen: String(ui.castleWq.checked),
        blackCastleKing: String(ui.castleBk.checked),
        blackCastleQueen: String(ui.castleBq.checked),
        enPassantSquare: ui.epSquare.value.trim() || "-",
        halfmoveClock: ui.halfmoveClock.value || "0",
        fullmoveNumber: ui.fullmoveNumber.value || "1"
    };
}

function syncInputsFromState() {
    if (!state) {
        return;
    }
    ui.engineMode.value = state.engine.mode;
    ui.enginePath.value = state.engine.enginePath;
    ui.thinkTime.value = state.engine.thinkTimeSeconds;
    ui.depth.value = state.engine.depth;
    ui.multiPv.value = state.engine.multiPv;
    ui.threads.value = state.engine.threads;
    ui.hashMb.value = state.engine.hashMb;
    ui.fenInput.value = state.fen;
    updateEnginePathState();

    const metadata = state.metadata || {};
    ui.metadataSide.value = metadata.sideToMove || state.sideToMove;
    ui.castleWk.checked = !!metadata.whiteCastleKing;
    ui.castleWq.checked = !!metadata.whiteCastleQueen;
    ui.castleBk.checked = !!metadata.blackCastleKing;
    ui.castleBq.checked = !!metadata.blackCastleQueen;
    ui.epSquare.value = metadata.enPassantSquare || "-";
    ui.halfmoveClock.value = metadata.halfmoveClock ?? 0;
    ui.fullmoveNumber.value = metadata.fullmoveNumber ?? 1;
}

function normalizeSelection() {
    if (!state || !selectedSquare) {
        return;
    }
    if (activeMode === "setup") {
        selectedSquare = "";
        return;
    }
    const square = boardMap().get(selectedSquare);
    const moves = legalMovesByFrom().get(selectedSquare) || [];
    if (!square || square.pieceSide !== state.sideToMove || moves.length === 0) {
        selectedSquare = "";
    }
}

function render() {
    if (!state) {
        return;
    }

    ui.playModeButton.classList.toggle("is-active", activeMode === "play");
    ui.setupModeButton.classList.toggle("is-active", activeMode === "setup");
    ui.setupBadge.textContent = activeMode === "setup" ? "Setup" : "Play";

    ui.statusPill.textContent = state.status || "Ready";
    ui.sideToMove.textContent = titleCase(state.sideToMove);
    ui.positionHealth.textContent = state.analyzable ? "Ready" : "Invalid";

    const analysis = state.analysis;
    ui.bestLineSummary.textContent = analysis && analysis.bestMoveSan && !analysis.stale
        ? `${analysis.bestMoveSan} ${analysis.bestEval}`
        : "No line";
    ui.analysisBadge.textContent = analysis
        ? `${analysis.engineName}${analysis.stale ? " stale" : ""}`
        : "Idle";
    ui.analysisNote.textContent = analysis
        ? [analysis.modeUsed === "UCI" ? "External engine" : "Built-in mini engine", analysis.note, analysis.stale ? "Stale" : ""]
            .filter(Boolean)
            .join(" | ")
        : "No analysis";

    ui.undoButton.disabled = !state.canUndo || busy;
    ui.redoButton.disabled = !state.canRedo || busy;
    ui.analyzeButton.disabled = !state.analyzable || busy;
    ui.playBestButton.disabled = !analysis || !analysis.bestMoveUci || analysis.stale || busy;
    ui.clearBoardButton.disabled = busy;
    ui.newGameButton.disabled = busy;

    renderPalette();
    renderBoard();
    renderAnalysis();
    renderHistory();
    renderEval();
}

function renderPalette() {
    if (!ui.piecePalette) {
        return;
    }
    const pieceButtons = setupPieces.map(piece => `
        <button
            class="palette-button ${setupPieceFen === piece.fen && !eraseMode ? "is-selected" : ""}"
            data-piece="${piece.fen}"
            type="button"
            title="${piece.label}"
            aria-label="${piece.label}">
            ${pieceSvg(piece.fen, `palette-piece ${piece.fen === piece.fen.toUpperCase() ? "square__piece--white" : "square__piece--black"}`)}
        </button>
    `).join("");
    ui.piecePalette.innerHTML = `${pieceButtons}
        <button
            class="palette-button is-erase ${eraseMode ? "is-selected" : ""}"
            data-erase="true"
            type="button"
            title="Erase"
            aria-label="Erase">X</button>`;
}

function renderBoard() {
    const map = boardMap();
    const targets = activeMode === "play" && selectedSquare ? targetMoves(selectedSquare) : [];
    const targetSquares = new Map();
    for (const move of targets) {
        const entries = targetSquares.get(move.to) || [];
        entries.push(move);
        targetSquares.set(move.to, entries);
    }

    const bestMove = state.analysis && !state.analysis.stale ? state.analysis.bestMoveUci : "";
    const bestFrom = bestMove ? bestMove.slice(0, 2) : "";
    const bestTo = bestMove ? bestMove.slice(2, 4) : "";
    const lastMove = state.lastMoveUci || "";
    const lastFrom = lastMove ? lastMove.slice(0, 2) : "";
    const lastTo = lastMove ? lastMove.slice(2, 4) : "";

    const orderedSquares = [...state.board].sort((left, right) => squareOrder(left.square) - squareOrder(right.square));
    ui.board.innerHTML = orderedSquares.map(square => {
        const file = fileIndex(square.square);
        const rank = rankNumber(square.square);
        const isLight = ((file + rank - 1) & 1) === 0;
        const isTarget = targetSquares.has(square.square);
        const hasCapture = isTarget && !!square.pieceFen;
        const fileLabel = showFileLabel(file, rank) ? String.fromCharCode(97 + file) : "";
        const rankLabel = showRankLabel(file, rank) ? String(rank) : "";
        const aria = `${square.square}${square.pieceFen ? ` ${pieceName(square.pieceFen)}` : ""}`;

        return `
            <button
                class="square ${isLight ? "square--light" : "square--dark"}${selectedSquare === square.square ? " is-selected" : ""}${square.square === state.checkSquare ? " is-check" : ""}${square.square === lastFrom || square.square === lastTo ? " is-last" : ""}${square.square === bestFrom || square.square === bestTo ? " is-best" : ""}"
                data-square="${square.square}"
                type="button"
                aria-label="${escapeHtml(aria)}">
                <span class="square__rank">${rankLabel}</span>
                <span class="square__file">${fileLabel}</span>
                <span class="square__hint ${isTarget ? "is-target" : ""} ${hasCapture ? "is-capture" : ""}"></span>
                ${square.pieceFen ? pieceSvg(square.pieceFen, `square__piece ${pieceClass(square)}`) : ""}
            </button>
        `;
    }).join("");

    renderArrow(bestMove);

    if (activeMode === "setup") {
        ui.boardCaptionText.textContent = eraseMode
            ? "Setup: erase"
            : `Setup: ${pieceName(setupPieceFen)}`;
        return;
    }

    if (selectedSquare) {
        const moves = legalMovesByFrom().get(selectedSquare) || [];
        ui.boardCaptionText.textContent = `${selectedSquare.toUpperCase()} selected - ${moves.length} destination${moves.length === 1 ? "" : "s"}`;
    } else {
        ui.boardCaptionText.textContent = state.analyzable
            ? `${titleCase(state.sideToMove)} to move`
            : state.validityMessage;
    }
}

function renderArrow(bestMoveUci) {
    if (!bestMoveUci) {
        ui.boardArrow.innerHTML = "";
        return;
    }
    const from = squareCenter(bestMoveUci.slice(0, 2));
    const to = squareCenter(bestMoveUci.slice(2, 4));
    const dx = to.x - from.x;
    const dy = to.y - from.y;
    const distance = Math.max(1, Math.hypot(dx, dy));
    const startInset = 20;
    const endInset = 34;
    const x1 = from.x + (dx * startInset) / distance;
    const y1 = from.y + (dy * startInset) / distance;
    const x2 = to.x - (dx * endInset) / distance;
    const y2 = to.y - (dy * endInset) / distance;
    ui.boardArrow.innerHTML = `
        <defs>
            <marker id="arrowhead" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto">
                <polygon points="0 0, 10 5, 0 10" fill="rgba(184, 137, 47, 0.88)"></polygon>
            </marker>
        </defs>
        <line
            x1="${x1}"
            y1="${y1}"
            x2="${x2}"
            y2="${y2}"
            stroke="rgba(184, 137, 47, 0.88)"
            stroke-width="11"
            stroke-linecap="round"
            marker-end="url(#arrowhead)">
        </line>
    `;
}

function renderAnalysis() {
    const analysis = state.analysis;
    if (!analysis || analysis.lines.length === 0) {
        ui.analysisLines.innerHTML = '<p class="empty-state">No candidate lines.</p>';
        return;
    }
    ui.analysisLines.innerHTML = analysis.lines.map((line, index) => `
        <button class="line" data-move="${line.moveUci}" type="button" ${analysis.stale ? "disabled" : ""}>
            <div class="line__top">
                <span class="line__move">${index + 1}. ${escapeHtml(line.sanMove)}</span>
                <span class="line__eval">${escapeHtml(line.evalText)}</span>
            </div>
            <div class="line__pv">${escapeHtml(line.pvSan || "No principal variation.")}</div>
            <div class="line__meta">Depth ${line.depth}${line.nodes ? ` | ${formatCompact(line.nodes)} nodes` : ""}${line.nps ? ` | ${formatCompact(line.nps)} nps` : ""}</div>
        </button>
    `).join("");
}

function renderHistory() {
    if (!state.history.length) {
        ui.historyGrid.innerHTML = '<p class="empty-state">No moves.</p>';
        return;
    }
    const rows = [];
    for (let i = 0; i < state.history.length; i += 2) {
        rows.push(`
            <div class="history-row">
                <div class="history-row__ply">${Math.floor(i / 2) + 1}.</div>
                <div class="history-row__move">${escapeHtml(state.history[i] || "")}</div>
                <div class="history-row__move">${escapeHtml(state.history[i + 1] || "")}</div>
            </div>
        `);
    }
    ui.historyGrid.innerHTML = rows.join("");
}

function renderEval() {
    const analysis = state.analysis;
    const percentage = analysis
        ? `${Math.max(0, Math.min(100, analysis.evalFraction * 100))}%`
        : "50%";
    ui.evalLabel.textContent = analysis ? analysis.bestEval || "0.00" : "0.00";
    if (window.matchMedia("(max-width: 760px)").matches) {
        ui.evalFill.style.width = percentage;
        ui.evalFill.style.height = "100%";
    } else {
        ui.evalFill.style.height = percentage;
        ui.evalFill.style.width = "100%";
    }
}

function handleBoardClick(square) {
    if (!state || busy) {
        return;
    }
    if (activeMode === "setup") {
        selectedSquare = "";
        postAction("/api/setup/piece", {square, pieceFen: eraseMode ? "" : setupPieceFen});
        return;
    }

    const piece = boardMap().get(square);
    const activeMoves = selectedSquare ? targetMoves(selectedSquare) : [];
    const matchingTargets = activeMoves.filter(move => move.to === square);

    if (selectedSquare && matchingTargets.length) {
        if (matchingTargets.length === 1) {
            selectedSquare = "";
            postAction("/api/move", {uci: matchingTargets[0].uci});
            return;
        }
        openPromotionModal(matchingTargets);
        return;
    }

    const movesFromSquare = legalMovesByFrom().get(square) || [];
    if (piece && piece.pieceSide === state.sideToMove && movesFromSquare.length) {
        selectedSquare = selectedSquare === square ? "" : square;
    } else {
        selectedSquare = "";
    }
    render();
}

function openPromotionModal(choices) {
    promotionChoices = choices;
    const side = state.sideToMove;
    ui.promotionGrid.innerHTML = choices.map(choice => {
        const fen = pieceFenForPromotion(side, choice.promotion);
        return `
            <button class="promotion-option" data-uci="${choice.uci}" type="button">
                ${pieceSvg(fen, fen === fen.toUpperCase() ? "square__piece--white" : "square__piece--black")}
                <span>${promotionLabels[choice.promotion]}</span>
            </button>
        `;
    }).join("");
    ui.promotionModal.hidden = false;
}

function closePromotionModal() {
    promotionChoices = [];
    ui.promotionModal.hidden = true;
}

function updateEnginePathState() {
    ui.enginePath.disabled = ui.engineMode.value !== "UCI";
}

async function copyFen() {
    if (!state) {
        return;
    }
    try {
        if (navigator.clipboard && navigator.clipboard.writeText) {
            await navigator.clipboard.writeText(state.fen);
            showToast("FEN copied.", false);
            return;
        }
    } catch (error) {
        // Fall through to manual selection.
    }
    ui.fenInput.focus();
    ui.fenInput.select();
    showToast("FEN selected.", false);
}

function setBusy(nextBusy) {
    busy = nextBusy;
    document.body.classList.toggle("is-busy", busy);
}

function boardMap() {
    return new Map((state?.board || []).map(square => [square.square, square]));
}

function legalMovesByFrom() {
    const grouped = new Map();
    for (const move of state?.legalMoves || []) {
        const entries = grouped.get(move.from) || [];
        entries.push(move);
        grouped.set(move.from, entries);
    }
    return grouped;
}

function targetMoves(square) {
    return legalMovesByFrom().get(square) || [];
}

function squareOrder(square) {
    const file = fileIndex(square);
    const rank = rankNumber(square);
    const boardFile = orientationWhiteBottom ? file : 7 - file;
    const boardRank = orientationWhiteBottom ? 8 - rank : rank - 1;
    return boardRank * 8 + boardFile;
}

function squareCenter(square) {
    const file = fileIndex(square);
    const rank = rankNumber(square);
    const boardFile = orientationWhiteBottom ? file : 7 - file;
    const boardRank = orientationWhiteBottom ? 8 - rank : rank - 1;
    return {
        x: boardFile * 100 + 50,
        y: boardRank * 100 + 50
    };
}

function showFileLabel(file, rank) {
    return orientationWhiteBottom ? rank === 1 : rank === 8;
}

function showRankLabel(file, rank) {
    return orientationWhiteBottom ? file === 0 : file === 7;
}

function fileIndex(square) {
    return square.charCodeAt(0) - 97;
}

function rankNumber(square) {
    return Number(square.slice(1));
}

function pieceName(fen) {
    const names = {
        p: "black pawn",
        n: "black knight",
        b: "black bishop",
        r: "black rook",
        q: "black queen",
        k: "black king",
        P: "white pawn",
        N: "white knight",
        B: "white bishop",
        R: "white rook",
        Q: "white queen",
        K: "white king"
    };
    return names[fen] || "piece";
}

function pieceClass(square) {
    if (!square.pieceFen) {
        return "";
    }
    return square.pieceSide === "WHITE" ? "square__piece--white" : "square__piece--black";
}

function pieceSvg(fen, className = "") {
    const symbol = pieceSymbol(fen);
    if (!symbol) {
        return "";
    }
    return `<svg class="${className}" viewBox="0 0 100 100" aria-hidden="true" focusable="false"><use href="#piece-${symbol}"></use></svg>`;
}

function pieceSymbol(fen) {
    const symbols = {
        p: "pawn",
        n: "knight",
        b: "bishop",
        r: "rook",
        q: "queen",
        k: "king"
    };
    return fen ? symbols[fen.toLowerCase()] || "" : "";
}

function pieceFenForPromotion(side, promotion) {
    const upper = {
        QUEEN: "Q",
        ROOK: "R",
        BISHOP: "B",
        KNIGHT: "N"
    }[promotion] || "Q";
    return side === "WHITE" ? upper : upper.toLowerCase();
}

function formatCompact(value) {
    return new Intl.NumberFormat("en-US", {notation: "compact", maximumFractionDigits: 1}).format(value);
}

function titleCase(text) {
    return text
        .toLowerCase()
        .replace(/(^|\s)\S/g, char => char.toUpperCase());
}

function showToast(message, isError) {
    window.clearTimeout(toastTimer);
    ui.toast.textContent = message;
    ui.toast.classList.toggle("is-error", isError);
    ui.toast.hidden = false;
    toastTimer = window.setTimeout(() => {
        ui.toast.hidden = true;
    }, 3400);
}

function escapeHtml(text) {
    return String(text)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
}
