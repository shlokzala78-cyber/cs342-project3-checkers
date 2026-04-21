
import java.util.HashMap;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class GuiClient extends Application{
	Client clientConnection;
	String username;

	Scene loginScene;
	Scene mainScene;

	// Login Screen UI
	TextField usernameInput;
	Label loginStatus;

	// Main Screen UI
	ListView<String> messageList;
	ListView<String> usersList;
	ComboBox<String> recipientBox;
	TextField messageInput;
	TextField groupInput;
	
	
	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		// Initialize network client
		clientConnection = new Client(data -> {
			Platform.runLater(() -> handleIncomingMessage(data, primaryStage));
		});
		clientConnection.start();

		// Build the scenes using helper methods
		loginScene = createLoginGui();
		mainScene = createMainGui();

		// Setup primary stage
		primaryStage.setOnCloseRequest(t -> {
			Platform.exit();
			System.exit(0);
		});

		primaryStage.setScene(loginScene);
		primaryStage.setTitle("Client Chat App - Login");
		primaryStage.show();
	}

	private Scene createLoginGui() {
		VBox loginBox = new VBox(10);
		loginBox.setPadding(new Insets(20));

		usernameInput = new TextField();
		usernameInput.setPromptText("Enter a unique username...");

		Button loginBtn = new Button("Join Server");
		loginStatus = new Label();
		loginStatus.setStyle("-fx-text-fill: blue;"); // Login Screen color blue

		loginBtn.setOnAction(e -> {
			Message req = new Message();
			req.type = Message.MessageType.CONNECT;
			req.sender = usernameInput.getText();
			clientConnection.send(req);
		});

		loginBox.getChildren().addAll(new Label("Username:"), usernameInput, loginBtn, loginStatus);

		// Retaining your original styling preference for the background
		loginBox.setStyle("-fx-background-color: lightblue; -fx-font-family: 'serif';"); // BG color lightblue

		return new Scene(loginBox, 300, 200);
	}

	private Scene createMainGui() {
		messageList = new ListView<>();
		usersList = new ListView<>();

		recipientBox = new ComboBox<>();
		recipientBox.getItems().add("All (Broadcast)");
		recipientBox.getSelectionModel().selectFirst();

		messageInput = new TextField();
		messageInput.setPromptText("Type your message...");
		Button sendBtn = new Button("Send");
		sendBtn.setOnAction(e -> sendMessage());

		// Group creation controls
		groupInput = new TextField();
		groupInput.setPromptText("Group Name");
		Button createGroupBtn = new Button("Create Group");
		createGroupBtn.setOnAction(e -> createGroup());

		// Layout construction
		HBox sendBox = new HBox(10, recipientBox, messageInput, sendBtn);
		HBox groupBox = new HBox(10, groupInput, createGroupBtn);

		VBox leftPane = new VBox(10, new Label("Online Users & Groups:"), usersList, groupBox);
		leftPane.setPrefWidth(200);

		BorderPane mainLayout = new BorderPane();
		mainLayout.setPadding(new Insets(10));
		mainLayout.setLeft(leftPane);
		mainLayout.setCenter(messageList);
		mainLayout.setBottom(sendBox);
		BorderPane.setMargin(messageList, new Insets(0, 10, 10, 10));

		mainLayout.setStyle("-fx-font-family: 'serif';");

		return new Scene(mainLayout, 700, 500);
	}


	// Handling and managing actions by clients
	private void handleIncomingMessage(Message msg, Stage stage) {
		switch (msg.type) {
			case CONNECT_SUCCESS:
				username = usernameInput.getText();
				stage.setScene(mainScene);
				stage.setTitle("Chat App - User: " + username);
				break;

			case CONNECT_FAIL:
				loginStatus.setText("Username taken or invalid. Try again.");
				break;

			case CLIENT_LIST: // Update ListView
				usersList.getItems().clear();
				recipientBox.getItems().clear();
				recipientBox.getItems().add("All (Broadcast)");

				usersList.getItems().addAll("--- USERS ---");
				for (String u : msg.activeUsers) {
					if (!u.equals(username)) {
						usersList.getItems().add(u);
						recipientBox.getItems().add(u);
					}
				}
				usersList.getItems().addAll("--- GROUPS ---");
				for (String g : msg.activeGroups) {
					usersList.getItems().add(g);
					recipientBox.getItems().add(g);
				}
				recipientBox.getSelectionModel().selectFirst();
				break;

			case BROADCAST: // Broadcast Message
				messageList.getItems().add("[Broadcast] " + msg.sender + ": " + msg.content);
				break;

			case PRIVATE:
				messageList.getItems().add("[Private] " + msg.sender + ": " + msg.content);
				break;

			case GROUP_MESSAGE: // Group Message
				messageList.getItems().add("[Group: " + msg.recipient + "] " + msg.sender + ": " + msg.content);
				break;
			case MATCH_FOUND:
				messageList.getItems().add("[System] " + msg.content);
				break;
		}
	}

	// Method to implement sendMessage action
	private void sendMessage() {
		String content = messageInput.getText();
		if (content.trim().isEmpty()) return;

		String recipient = recipientBox.getValue();
		Message msg = new Message();
		msg.sender = username;
		msg.content = content;

		if (recipient.equals("All (Broadcast)")) {
			msg.type = Message.MessageType.BROADCAST;
		} else if (usersList.getItems().contains(recipient) && !recipient.startsWith("---")) {
			boolean isGroup = false;
			for(String s : usersList.getItems()) {
				if(s.equals("--- GROUPS ---")) isGroup = true;
				if(s.equals(recipient) && isGroup) break;
				if(s.equals(recipient) && !isGroup) break;
			}
			msg.type = isGroup ? Message.MessageType.GROUP_MESSAGE : Message.MessageType.PRIVATE;
			msg.recipient = recipient;
		}

		clientConnection.send(msg);
		messageInput.clear();
	}

	// Method to create Group
	private void createGroup() {
		String gName = groupInput.getText().trim();
		if(!gName.isEmpty()) {
			Message msg = new Message();
			msg.type = Message.MessageType.CREATE_GROUP;
			msg.sender = username;
			msg.recipient = gName;
			clientConnection.send(msg);
			groupInput.clear();
		}
	}

}
