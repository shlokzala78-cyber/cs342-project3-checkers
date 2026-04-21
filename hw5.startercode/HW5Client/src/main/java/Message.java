import java.io.Serializable;
import java.util.ArrayList;

public class Message implements Serializable {
    static final long serialVersionUID = 42L;

    public enum MessageType {
        CONNECT,            // Client requesting a username
        CONNECT_SUCCESS,    // Server approving username
        CONNECT_FAIL,       // Server rejecting username
        CLIENT_LIST,        // Server updating clients with active users/groups
        BROADCAST,          // Message to everyone
        PRIVATE,            // Message to a specific user
        CREATE_GROUP,       // Request to create a new group
        GROUP_MESSAGE,       // Message to a specific group
        MATCH_FOUND,
    }

    public MessageType type;
    public String sender;
    public String recipient;
    public String content;
    public ArrayList<String> activeUsers;
    public ArrayList<String> activeGroups;

    public Message() {
        this.activeUsers = new ArrayList<>();
        this.activeGroups = new ArrayList<>();
    }
}
