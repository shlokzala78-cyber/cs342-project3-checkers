import java.io.Serializable;
import java.util.ArrayList;

public class Message implements Serializable {
    static final long serialVersionUID = 42L;

    public enum MessageType {
        CONNECT,            // Client requests a username
        CONNECT_SUCCESS,    // Server approves username
        CONNECT_FAIL,       // Server rejects username
        CLIENT_LIST,        // Server updates waiting room list

        // --- NEW CHECKERS COMMANDS ---
        CHALLENGE,          // Request to play a specific user
        GAME_START,         // Server tells clients the game is starting
        MOVE,               // A player makes a move on the board
        CHAT,               // In-game text messaging
        OFFER_DRAW,         // A player offers a draw
        QUIT,               // A player quits the match
        GAME_OVER           // Server announces the winner
    }

    public MessageType type;
    public String sender;
    public String recipient;
    public String content;
    public ArrayList<String> activeUsers;

    // Variables specifically for Checkers moves (e.g., moving from row 2, col 3 to row 3, col 4)
    public int startRow;
    public int startCol;
    public int endRow;
    public int endCol;

    public Message() {
        this.activeUsers = new ArrayList<>();
    }
}
