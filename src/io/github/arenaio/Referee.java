package io.github.arenaio;


import io.github.arenaio.base.AbstractReferee;
import io.github.arenaio.base.MultiReferee;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author daniel.laeppchen on 30.06.17.
 */
public class Referee extends MultiReferee {

    public Referee(InputStream is, PrintStream out, PrintStream err) throws IOException {
        super(is, out, err);
    }

    @Override
    protected void populateMessages(Properties p) {
    }

    @Override
    protected String[] getInitDataForView() {
        List<String> lines = new ArrayList<>();
        lines.add("_________");
        return lines.toArray(new String[lines.size()]);
    }

    @Override
    protected String[] getFrameDataForView(int round, int frame, boolean keyFrame) {
        List<String> lines = new ArrayList<>();
        lines.add(this.gameState.getGridAsString());
        return lines.toArray(new String[lines.size()]);
    }

    @Override
    protected int getExpectedOutputLineCountForPlayer(int playerIdx) {
        return 1;
    }

    @Override
    protected String getGameName() {
        return "TIC TAC TOE";
    }

    private final static String EXPECTED = "expected number 0 to 8 on not used field";
    private final static int[][] VALID_WIN = {
            {0,1,2},
            {3,4,5},
            {6,7,8},
            {0,3,6},
            {1,4,7},
            {2,5,8},
            {0,4,8},
            {2,4,6}
    };
    @Override
    protected void handlePlayerOutput(int frame, int round, int playerIdx, String[] output) throws WinException, LostException, InvalidInputException {
        String line = output[0];
        PlayerData player = players.get(playerIdx);

        try {
            if (line.length() != 5) throw new InvalidInputException(EXPECTED, line);
            int input = Integer.parseInt(line.substring(4, 5));
            if (input < 0 || input > 8) throw new InvalidInputException(EXPECTED, line);
            char charAtPosition = this.gameState.getGrid()[input];
            if (charAtPosition != '_') throw new InvalidInputException(EXPECTED, line);

            this.gameState.getGrid()[input] = (playerIdx == 0) ? 'o' : 'x';

            char currentPlayerSign = (playerIdx==0)?'o':'x';
            for(int i=0; i<VALID_WIN.length; ++i){
                int[] currentLine = VALID_WIN[i];
                boolean win = true;
                for(int j=0; j<currentLine.length; ++j){
                    if(currentLine[j]!=currentPlayerSign){
                        win=false;
                        break;
                    }
                }
                if(win){
                    throw new WinException("one in row", "one in row");
                }
            }

        } catch (WinException e){
            throw e;
        } catch (InvalidInputException e) {
            player.die(round);
            throw e;
        } catch (Exception e) {
            player.die(round);
            throw new InvalidInputException(EXPECTED, line);
        }
    }

    @Override
    protected String[] getInitInputForPlayer(int playerIdx) {
        List<String> lines = new ArrayList<>();
        if(playerIdx==0)
            lines.add("o");
        else
            lines.add("x");

        return lines.toArray(new String[lines.size()]);
    }

    @Override
    protected String[] getInputForPlayer(int round, int playerIdx) {
        List<String> lines = new ArrayList<>();

        lines.add(this.gameState.getGridAsString());

        return lines.toArray(new String[lines.size()]);
    }

    @Override
    protected String getHeadlineAtGameStartForConsole() {
        return null;
    }

    @Override
    protected int getMinimumPlayerCount() {
        return 2;
    }

    @Override
    protected boolean showTooltips() {
        return true;
    }

    @Override
    protected void updateGame(int round) throws GameOverException {
    }

    @Override
    protected void prepare(int round) {
    }

    @Override
    protected boolean isPlayerDead(int playerIdx) {
        return players.get(playerIdx).dead;
    }

    @Override
    protected String getDeathReason(int playerIdx) {
        return "$" + playerIdx + ": Eliminated!";
    }

    @Override
    protected int getScore(int playerIdx) {
        PlayerData player = players.get(playerIdx);
        return player.score;
    }

    @Override
    protected String[] getGameSummary(int round) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < players.size(); ++i) {
            lines.addAll(getPlayerSummary(i, round));
        }
        return lines.toArray(new String[lines.size()]);
    }
    protected List<String> getPlayerSummary(int playerIdx, int round) {
        List<String> lines = new ArrayList<>();
        PlayerData player = players.get(playerIdx);

        if (player.dead) {
            if (player.deadAt == round) {
                lines.add(getDeathReason(playerIdx));
            }
        }
        return lines;
    }

    @Override
    protected String[] getPlayerActions(int playerIdx, int round) {
        return new String[0];
    }

    @Override
    protected void setPlayerTimeout(int frame, int round, int playerIdx) {
        PlayerData player = players.get(playerIdx);
        player.die(round);
    }

    @Override
    protected int getMaxRoundCount(int playerCount) {
        return 9;
    }

    private List<PlayerData> players;
    private GameState gameState;

    @Override
    protected void initReferee(int playerCount, Properties prop) throws InvalidFormatException {
        //init gamestate
        this.gameState = new GameState();

        //init players
        this.players = new ArrayList<PlayerData>();
        for(int i=0; i<playerCount; ++i){
            this.players.add(i, new PlayerData(i));
        }
    }

    @Override
    protected Properties getConfiguration() {
        Properties prop = new Properties();
        return prop;
    }

    static class PlayerData {
        boolean dead;
        int deadAt, index, score;
        String message;

        public PlayerData(int index){
            this.index = index;
            this.score = 0;
        }

        public void die(int round){
            if(!dead){
                dead = true;
                deadAt = round;
                score = -1;
            }
        }
        public void reset(){
            message = null;
        }
        public void setMessage(String message) {
            this.message = message;
            if(message!=null && message.length()>19){
                this.message = message.substring(0,17) + "...";
            }
        }
    }
    static class GameState {
        char[] grid;
        GameState(){
            this.grid = new char[9];
            Arrays.fill(this.grid, '_');
        }
        void reset(){
            Arrays.fill(this.grid, '_');
        }
        String getGridAsString(){
            return new String(this.grid);
        }
        char[] getGrid(){
            return this.grid;
        }
    }

    public static void main(String... args) throws IOException {
        new Referee(System.in, System.out, System.err).start();
    }
}
