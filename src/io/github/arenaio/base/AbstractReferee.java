package io.github.arenaio.base;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author daniel.laeppchen on 30.06.17.
 */
public abstract class AbstractReferee {
    private static final Pattern HEADER_PATTERN = Pattern.compile("\\[\\[(?<cmd>.+)\\] ?(?<lineCount>[0-9]+)\\]");
    private static final String LOST_PARSING_REASON_CODE = "INPUT";
    private static final String LOST_PARSING_REASON = "Failure: invalid input";

    protected static class PlayerStatus {
        private int id;
        private int score;
        private boolean lost, win;
        private String info;
        private String reasonCode;
        private String[] nextInput;

        public PlayerStatus(int id) {
            this.id = id;
            lost = false;
            info = null;
        }

        public int getScore() {
            return score;
        }

        public boolean isLost() {
            return lost;
        }

        public String getInfo() {
            return info;
        }

        public int getId() {
            return id;
        }

        public String getReasonCode() {
            return reasonCode;
        }

        public String[] getNextInput() {
            return nextInput;
        }
    }

    private Properties messages = new Properties();

    @SuppressWarnings("serial")
    public final class InvalidFormatException extends Exception {
        public InvalidFormatException(String message) {
            super(message);
        }
    }

    @SuppressWarnings("serial")
    abstract class GameException extends Exception {
        private String reasonCode, tooltipCode;
        private Object[] values;

        public GameException(String reasonCode, Object... values) {
            this.reasonCode = reasonCode;
            this.values = values;
        }

        public void setTooltipCode(String tooltipCode) {
            this.tooltipCode = tooltipCode;
        }

        public String getReason() {
            if (reasonCode != null) {
                return translate(reasonCode, values);
            } else {
                return null;
            }
        }

        public String getReasonCode() {
            return reasonCode;
        }

        public String getTooltipCode() {
            if (tooltipCode != null) {
                return tooltipCode;
            }
            return getReasonCode();
        }
    }

    @SuppressWarnings("serial")
    public class LostException extends GameException {
        public LostException(String reasonCode, Object... values) {
            super(reasonCode, values);
        }
    }

    @SuppressWarnings("serial")
    public class WinException extends GameException {
        public WinException(String reasonCode, Object... values) {
            super(reasonCode, values);
        }
    }

    @SuppressWarnings("serial")
    public class InvalidInputException extends GameException {
        public InvalidInputException(String expected, String found) {
            super("InvalidInput", expected, found);
        }
    }

    @SuppressWarnings("serial")
    public class GameOverException extends GameException {
        public GameOverException(String reasonCode, Object... values) {
            super(reasonCode, values);
        }
    }

    @SuppressWarnings("serial")
    public class GameErrorException extends Exception {
        public GameErrorException(Throwable cause) {
            super(cause);
        }
    }

    public static enum InputCommand

    {
        INIT, GET_GAME_INFO, SET_PLAYER_OUTPUT, SET_PLAYER_TIMEOUT
    }

    public static enum OutputCommand

    {
        VIEW, INFOS, NEXT_PLAYER_INPUT, NEXT_PLAYER_INFO, SCORES, UINPUT, TOOLTIP, SUMMARY;

        public String format(int lineCount) {
            return String.format("[[%s] %d]", this.name(), lineCount);
        }
    }

    @SuppressWarnings("serial")
    public static class OutputData extends LinkedList<String> {
        private OutputCommand command;

        public OutputData(OutputCommand command) {
            this.command = command;
        }

        public boolean add(String s) {
            if (s != null)
                return super.add(s);
            return false;
        }

        public void addAll(String[] data) {
            if (data != null)
                super.addAll(Arrays.asList(data));
        }

        @Override
        public String toString() {
            StringWriter writer = new StringWriter();
            PrintWriter out = new PrintWriter(writer);
            out.println(this.command.format(this.size()));
            for (String line : this) {
                out.println(line);
            }
            return writer.toString().trim();
        }
    }

    private static class Tooltip {
        int player;
        String message;

        public Tooltip(int player, String message) {
            this.player = player;
            this.message = message;
        }

    }

    private Set<Tooltip> tooltips;
    private int playerCount, alivePlayerCount;
    private int currentPlayer, nextPlayer;
    private PlayerStatus lastPlayer, playerStatus;
    private int frame, round;
    private PlayerStatus[] players;
    private String[] initLines;
    private boolean newRound;
    private String reasonCode, reason;

    private InputStream is;
    private PrintStream out;
    private PrintStream err;

    public AbstractReferee(InputStream is, PrintStream out, PrintStream err) throws IOException {
        tooltips = new HashSet<>();
        this.is = is;
        this.out = out;
        this.err = err;
        start();
    }

    @SuppressWarnings("resource")
    public void start() throws IOException {
        this.messages.put("InvalidInput", "invalid input. Expected '%s' but found '%s'");
        this.messages.put("playerTimeoutMulti", "Timeout: the program did not provide %d input lines in due time... $%d will no longer be active in this game.");
        this.messages.put("playerTimeoutSolo", "Timeout: the program did not provide %d input lines in due time...");
        this.messages.put("maxRoundsCountReached", "Max rounds reached");
        this.messages.put("notEnoughPlayers", "Not enough players (expected > %d, found %d)");
        this.messages.put("InvalidActionTooltip", "$%d: invalid action");
        this.messages.put("TimeoutTooltip", "$%d: timeout!");
        this.messages.put("WinTooltip", "$%d: victory!");
        this.messages.put("LostTooltip", "$%d lost: %s");

        populateMessages(this.messages);
        Scanner s = new Scanner(is);
        int i;
        try {
            while (true) {
                String line = s.nextLine();
                Matcher m = HEADER_PATTERN.matcher(line);
                if (!m.matches())
                    throw new RuntimeException("Error in data sent to referee");
                String cmd = m.group("cmd");
                int lineCount = Integer.parseInt(m.group("lineCount"));

                switch (InputCommand.valueOf(cmd)) {
                    case INIT:
                        playerCount = alivePlayerCount = s.nextInt();
                        players = new PlayerStatus[playerCount];
                        for (i = 0; i < playerCount; ++i)
                            players[i] = new PlayerStatus(i);

                        playerStatus = players[0];

                        currentPlayer = nextPlayer = playerCount - 1;
                        frame = 0;
                        round = -1;
                        newRound = true;
                        s.nextLine();
                        i = 0;

                        initLines = null;
                        if (lineCount > 0) {
                            initLines = new String[lineCount - 1];
                            for (i = 0; i < (lineCount - 1); i++) {
                                initLines[i] = s.nextLine();
                            }
                        }
                        try {
                            handleInitInputForReferee(playerCount, initLines);
                        } catch (RuntimeException | InvalidFormatException e) {
                            reason = "Init error: " + e.getMessage();
                            OutputData viewData = new OutputData(OutputCommand.VIEW);
                            viewData.add(e.toString());
                            viewData.add(String.valueOf(-1));
                            viewData.add(LOST_PARSING_REASON_CODE);
                            out.println(viewData);
                            OutputData infoData = new OutputData(OutputCommand.INFOS);
                            infoData.add(getColoredReason(true, LOST_PARSING_REASON));
                            infoData.add(getColoredReason(true, e.getMessage()));
                            infoData.addAll(initLines);
                            out.println(infoData);
                            dumpView();
                            dumpInfos();
                            throw new GameErrorException(e);
                        }

                        if (playerCount < getMinimumPlayerCount()) {
                            throw new GameOverException("notEnoughPlayers", getMinimumPlayerCount(), playerCount);
                        }
                        break;
                    case GET_GAME_INFO:
                        lastPlayer = playerStatus;
                        playerStatus = nextPlayer();
                        if (this.round >= getMaxRoundCount(this.playerCount)) {
                            throw new GameOverException("maxRoundsCountReached");
                        }
                        dumpView();
                        dumpInfos();
                        if (newRound) {
                            prepare(round);
                            if (!this.isTurnBasedGame()) {
                                for (PlayerStatus player : this.players) {
                                    if (!player.lost) {
                                        player.nextInput = getInputForPlayer(round, player.id);
                                    } else {
                                        player.nextInput = null;
                                    }
                                }
                            }
                        }
                        dumpNextPlayerInput();
                        dumpNextPlayerInfos();
                        break;
                    case SET_PLAYER_OUTPUT:
                        ++frame;
                        String[] output = new String[lineCount];
                        for (i = 0; i < lineCount; i++) {
                            output[i] = s.nextLine();
                        }
                        try {
                            handlePlayerOutput(frame, round, nextPlayer, output);
                        } catch (LostException | InvalidInputException e) {
                            playerStatus.score = getScore(nextPlayer);
                            playerStatus.lost = true;
                            playerStatus.info = e.getReason();
                            playerStatus.reasonCode = e.getReasonCode();
                            if (e instanceof InvalidInputException) {
                                addToolTip(nextPlayer, translate("InvalidActionTooltip", nextPlayer));
                            } else {
                                addToolTip(nextPlayer, translate("LostTooltip", nextPlayer, translate(e.getTooltipCode(), e.values)));
                            }
                            if (--alivePlayerCount < getMinimumPlayerCount() && isTurnBasedGame()) {
                                lastPlayer = playerStatus;
                                throw new GameOverException(null);
                            }
                        } catch (WinException e) {
                            playerStatus.score = getScore(nextPlayer);
                            playerStatus.win = true;
                            playerStatus.info = e.getReason();
                            playerStatus.reasonCode = e.getReasonCode();
                            addToolTip(nextPlayer, translate("WinTooltip", nextPlayer));
                            if (--alivePlayerCount < getMinimumPlayerCount()) {
                                lastPlayer = playerStatus;
                                throw new GameOverException(null);
                            }
                        }
                        break;
                    case SET_PLAYER_TIMEOUT:
                        ++frame;
                        int count = getExpectedOutputLineCountForPlayer(nextPlayer);
                        setPlayerTimeout(frame, round, nextPlayer);
                        playerStatus.lost = true;
                        if (playerCount <= 1) {
                            playerStatus.info = translate("playerTimeoutSolo", count);
                        } else {
                            playerStatus.info = translate("playerTimeoutMulti", count, nextPlayer);
                        }
                        addToolTip(nextPlayer, translate("TimeoutTooltip", nextPlayer));
                        if (--alivePlayerCount < getMinimumPlayerCount() && isTurnBasedGame()) {
                            lastPlayer = playerStatus;
                            throw new GameOverException(null);
                        }
                        break;
                }
            }
        } catch (GameOverException e) {
            newRound = true;
            reasonCode = e.getReasonCode();
            reason = e.getReason();

            dumpView();
            dumpInfos();
            prepare(round);
            updateScores();
        } catch (GameErrorException e) {
            // e.printStackTrace();
        } catch (Throwable t) {
            t.printStackTrace();
            s.close();
            return;
        }

        String[] playerScores = new String[playerCount];
        for (i = 0; i < playerCount; ++i) {
            playerScores[i] = i + " " + players[i].score;
        }
        appendDataToEnd(out);
        OutputData data = new OutputData(OutputCommand.SCORES);
        data.addAll(playerScores);
        out.println(data);
        s.close();
    }

    private PlayerStatus nextPlayer() throws GameOverException {
        currentPlayer = nextPlayer;
        newRound = false;
        do {
            ++nextPlayer;
            if (nextPlayer >= playerCount) {
                nextRound();
                nextPlayer = 0;
            }
        } while (this.players[nextPlayer].lost || this.players[nextPlayer].win);
        return players[nextPlayer];
    }

    protected String getColoredReason(boolean error, String reason) {
        if (error) {
            return String.format("¤RED¤%s§RED§", reason);
        } else {
            return String.format("¤GREEN¤%s§GREEN§", reason);
        }
    }

    private void dumpView() {
        OutputData data = new OutputData(OutputCommand.VIEW);
        String reasonCode = this.reasonCode;
        if (reasonCode == null && playerStatus != null)
            reasonCode = playerStatus.reasonCode;

        if (newRound) {
            if (reasonCode != null) {
                data.add(String.format("KEY_FRAME %d %s", this.frame, reasonCode));
            } else {
                data.add(String.format("KEY_FRAME %d", this.frame));
            }
            if (frame == 0) {
                data.add(getGameName());
                data.addAll(getInitDataForView());
            }
        } else {
            if (reasonCode != null) {
                data.add(String.format("INTERMEDIATE_FRAME %d %s", this.frame, reasonCode));
            } else {
                data.add(String.format("INTERMEDIATE_FRAME %d", frame));
            }
        }
        if (newRound || isTurnBasedGame()) {
            data.addAll(getFrameDataForView(round, frame, newRound));
        }

        out.println(data);
    }

    private void dumpInfos() {
        OutputData data = new OutputData(OutputCommand.INFOS);
        if (reason != null && isTurnBasedGame()) {
            data.add(getColoredReason(true, reason));
        } else {
            if (lastPlayer != null) {
                String head = lastPlayer.info;
                if (head != null) {
                    data.add(getColoredReason(lastPlayer.lost, head));
                } else {
                    if (frame > 0) {
                        data.addAll(getPlayerActions(this.currentPlayer, newRound ? this.round - 1 : this.round));
                    }
                }
            }
        }
        out.println(data);
        if (newRound && round >= -1 && playerCount > 1) {
            OutputData summary = new OutputData(OutputCommand.SUMMARY);
            if (frame == 0) {
                String head = getHeadlineAtGameStartForConsole();
                if (head != null) {
                    summary.add(head);
                }
            }
            if (round >= 0) {
                summary.addAll(getGameSummary(round));
            }
            if (!isTurnBasedGame() && reason != null) {
                summary.add(getColoredReason(true, reason));
            }
            out.println(summary);
        }

        if (!tooltips.isEmpty() && (newRound || isTurnBasedGame())) {
            data = new OutputData(OutputCommand.TOOLTIP);
            for (Tooltip t : tooltips) {
                data.add(t.message);
                data.add(String.valueOf(t.player));
            }
            tooltips.clear();
            out.println(data);
        }
    }

    private void dumpNextPlayerInfos() {
        OutputData data = new OutputData(OutputCommand.NEXT_PLAYER_INFO);
        data.add(String.valueOf(nextPlayer));
        data.add(String.valueOf(getExpectedOutputLineCountForPlayer(nextPlayer)));
        if (this.round == 0) {
            data.add(String.valueOf(getMillisTimeForFirstRound()));
        } else {
            data.add(String.valueOf(getMillisTimeForRound()));
        }
        out.println(data);
    }

    private void dumpNextPlayerInput() {
        OutputData data = new OutputData(OutputCommand.NEXT_PLAYER_INPUT);
        if (this.round == 0) {
            data.addAll(getInitInputForPlayer(nextPlayer));
        }
        if (this.isTurnBasedGame()) {
            this.players[nextPlayer].nextInput = getInputForPlayer(round, nextPlayer);
        }
        data.addAll(this.players[nextPlayer].nextInput);
        out.println(data);
    }

    protected final String translate(String code, Object... values) {
        try {
            return String.format((String) messages.get(code), values);
        } catch (NullPointerException e) {
            return code;
        }
    }

    protected final void printError(Object message) {
        err.println(message);
    }

    protected int getMillisTimeForFirstRound() {
        return 1000;
    }

    protected int getMillisTimeForRound() {
        return 150;
    }

    protected int getMaxRoundCount(int playerCount) {
        return 400;
    }

    private void nextRound() throws GameOverException {
        newRound = true;
        if (++round > 0) {
            updateGame(round);
        }
        if (gameOver()) {
            throw new GameOverException(null);
        }
    }

    protected boolean gameOver() {
        return alivePlayerCount < getMinimumPlayerCount();
    }

    private void updateScores() {
        for (int i = 0; i < playerCount; ++i) {
            if (!players[i].lost && isPlayerDead(i)) {
                alivePlayerCount--;
                players[i].lost = true;
                players[i].info = getDeathReason(i);
                addToolTip(i, players[i].info);
            }
            players[i].score = getScore(i);
        }
    }

    protected void addToolTip(int player, String message) {
        if (showTooltips())
            tooltips.add(new Tooltip(player, message));
    }

    /**
     * Add message (key = reasonCode, value = reason)
     *
     * @param p
     */
    protected abstract void populateMessages(Properties p);

    protected boolean isTurnBasedGame() {
        return false;
    }

    protected abstract void handleInitInputForReferee(int playerCount, String[] init) throws InvalidFormatException;

    protected abstract String[] getInitDataForView();

    protected abstract String[] getFrameDataForView(int round, int frame, boolean keyFrame);

    protected abstract int getExpectedOutputLineCountForPlayer(int playerIdx);

    protected abstract String getGameName();

    protected abstract void appendDataToEnd(PrintStream stream) throws IOException;

    /**
     * //@param player player id
     * @param output
     * @return score of the player
     */
    protected abstract void handlePlayerOutput(int frame, int round, int playerIdx, String[] output) throws WinException, LostException, InvalidInputException;

    protected abstract String[] getInitInputForPlayer(int playerIdx);

    protected abstract String[] getInputForPlayer(int round, int playerIdx);

    protected abstract String getHeadlineAtGameStartForConsole();

    protected abstract int getMinimumPlayerCount();

    protected abstract boolean showTooltips();

    /**
     * @param round
     * @return scores of all players
     * @throws GameOverException
     */
    protected abstract void updateGame(int round) throws GameOverException;

    protected abstract void prepare(int round);

    protected abstract boolean isPlayerDead(int playerIdx);

    protected abstract String getDeathReason(int playerIdx);

    protected abstract int getScore(int playerIdx);

    protected abstract String[] getGameSummary(int round);

    protected abstract String[] getPlayerActions(int playerIdx, int round);

    protected abstract void setPlayerTimeout(int frame, int round, int playerIdx);

}
