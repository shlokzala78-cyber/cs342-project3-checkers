import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

public class GuiClient extends Application {
	Client clientConnection;
	String username;
	String playerColor = "";   // NEW: "Red" or "Black"

	// Scenes
	Scene loginScene;
	Scene waitingScene;
	Scene gameScene;

	// UI Elements
	TextField ipInput;
	TextField usernameInput;
	Label loginStatus;
	ListView<String> usersList;

	// Game Specific UI Elements
	GridPane checkerBoard;
	ListView<String> gameChat;
	TextField chatInput;
	Label turnIndicator;
	Label capturedInfo;

	// Board State
	private StackPane[][] boardSquares = new StackPane[8][8];
	private int selectedRow = -1;
	private int selectedCol = -1;

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		clientConnection = new Client(data -> {
			Platform.runLater(() -> handleIncomingMessage(data, primaryStage));
		});
		clientConnection.start();

		loginScene = createLoginGui();
		waitingScene = createWaitingGui();
		gameScene = createGameGui();

		primaryStage.setOnCloseRequest(t -> {
			Platform.exit();
			System.exit(0);
		});

		primaryStage.setScene(loginScene);
		primaryStage.setTitle("Checkers - Login");
		primaryStage.show();
	}

	// --- SCENE 1: LOGIN SCREEN ---
	private Scene createLoginGui() {
		VBox loginBox = new VBox(15);
		loginBox.setAlignment(Pos.CENTER);
		loginBox.setPadding(new Insets(20));

		Label title = new Label("Checkers Login");
		title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

		ipInput = new TextField("127.0.0.1");
		ipInput.setPromptText("Server IP Address");

		usernameInput = new TextField();
		usernameInput.setPromptText("Username");

		Button connectBtn = new Button("Connect to Server");
		loginStatus = new Label("Status: Disconnected");

		connectBtn.setOnAction(e -> {
			Message req = new Message();
			req.type = Message.MessageType.CONNECT;
			req.sender = usernameInput.getText();
			clientConnection.send(req);
		});

		loginBox.getChildren().addAll(
				title,
				new Label("Server IP Address:"),
				ipInput,
				new Label("Username:"),
				usernameInput,
				connectBtn,
				loginStatus
		);
		loginBox.setStyle("-fx-background-color: cyan;");

		return new Scene(loginBox, 400, 300);
	}

	// --- SCENE 2: WAITING / MATCHMAKING SCREEN ---
	private Scene createWaitingGui() {
		VBox waitingBox = new VBox(10);
		waitingBox.setPadding(new Insets(20));

		Label welcomeLabel = new Label("Online Players:");
		usersList = new ListView<>();

		Button challengeBtn = new Button("Challenge Selected Player");
		challengeBtn.setOnAction(e -> {
			String selected = usersList.getSelectionModel().getSelectedItem();
			if (selected != null) {
				Message req = new Message();
				req.type = Message.MessageType.CHALLENGE;
				req.sender = username;
				req.recipient = selected;
				clientConnection.send(req);
			}
		});

		waitingBox.getChildren().addAll(welcomeLabel, usersList, challengeBtn);
		waitingBox.setStyle("-fx-background-color: cyan;");

		return new Scene(waitingBox, 400, 400);
	}

	// --- SCENE 3: GAMEBOARD SCREEN ---
	private Scene createGameGui() {
		BorderPane gameLayout = new BorderPane();
		gameLayout.setPadding(new Insets(10));
		gameLayout.setStyle("-fx-background-color: cyan;");

		VBox topBox = new VBox(5);
		capturedInfo = new Label("Captured Pieces: 0");
		turnIndicator = new Label("Whose Turn: Waiting for Server...");
		topBox.getChildren().addAll(capturedInfo, turnIndicator);
		gameLayout.setTop(topBox);

		checkerBoard = new GridPane();
		checkerBoard.setStyle("-fx-border-color: black; -fx-border-width: 2;");
		checkerBoard.setMaxSize(400, 400);

		for (int row = 0; row < 8; row++) {
			for (int col = 0; col < 8; col++) {
				StackPane square = new StackPane();
				square.setPrefSize(50, 50);

				boardSquares[row][col] = square;
				checkerBoard.add(square, col, row);

				resetSquareColor(row, col);

				final int r = row;
				final int c = col;
				square.setOnMouseClicked(e -> handleSquareClick(r, c));

			}
		}
		gameLayout.setCenter(checkerBoard);

		VBox rightMenu = new VBox(10);
		rightMenu.setPadding(new Insets(10));
		rightMenu.setPrefWidth(200);

		gameChat = new ListView<>();
		chatInput = new TextField();
		Button sendBtn = new Button("Send");
		sendBtn.setOnAction(e -> sendChatMessage());
		HBox chatControls = new HBox(5, chatInput, sendBtn);

		Button drawBtn = new Button("Offer Draw");
		Button quitBtn = new Button("Quit Game");

		rightMenu.getChildren().addAll(new Label("Text Messaging"), gameChat, chatControls, drawBtn, quitBtn);
		gameLayout.setRight(rightMenu);

		return new Scene(gameLayout, 700, 500);
	}

	// --- CLIENT GAME LOGIC ---

	private void initializeBoard() {
		for (int row = 0; row < 8; row++) {
			for (int col = 0; col < 8; col++) {
				boardSquares[row][col].getChildren().clear();
				resetSquareColor(row, col);

				if ((row + col) % 2 != 0) {
					if (row < 3) {
						addPieceToSquare(row, col, Color.BLACK);
					} else if (row > 4) {
						addPieceToSquare(row, col, Color.RED);
					}
				}
			}
		}
	}

	private void addPieceToSquare(int row, int col, Color color) {
		Circle piece = new Circle(20, color);
		piece.setStroke(Color.BLACK);
		piece.setStrokeWidth(2);
		boardSquares[row][col].getChildren().add(piece);
	}

	// NEW: restores the original checkerboard color correctly
	private void resetSquareColor(int row, int col) {
		if ((row + col) % 2 == 0) {
			boardSquares[row][col].setStyle("-fx-background-color: #ffce9e;");
		} else {
			boardSquares[row][col].setStyle("-fx-background-color: #d18b47;");
		}
	}

	private void handleSquareClick(int row, int col) {
		// First click: select one of YOUR own pieces only
		if (selectedRow == -1 && selectedCol == -1) {
			if (!boardSquares[row][col].getChildren().isEmpty()) {
				Circle piece = (Circle) boardSquares[row][col].getChildren().get(0);

				boolean isMyPiece =
						(playerColor.equals("Red") && piece.getFill().equals(Color.RED)) ||
								(playerColor.equals("Black") && piece.getFill().equals(Color.BLACK));

				if (!isMyPiece) {
					return;
				}

				selectedRow = row;
				selectedCol = col;
				boardSquares[row][col].setStyle("-fx-background-color: yellow; -fx-border-color: red; -fx-border-width: 2;");
			}
		}
		// Second click: send move request
		else {
			Message moveMsg = new Message();
			moveMsg.type = Message.MessageType.MOVE;
			moveMsg.sender = username;
			moveMsg.startRow = selectedRow;
			moveMsg.startCol = selectedCol;
			moveMsg.endRow = row;
			moveMsg.endCol = col;

			clientConnection.send(moveMsg);

			resetSquareColor(selectedRow, selectedCol);
			selectedRow = -1;
			selectedCol = -1;
		}
	}

	// --- NETWORK MESSAGE HANDLER ---
	private void handleIncomingMessage(Message msg, Stage stage) {
		switch (msg.type) {
			case CONNECT_SUCCESS:
				username = usernameInput.getText();
				stage.setScene(waitingScene);
				stage.setTitle("Checkers - Waiting Room (" + username + ")");
				break;

			case CONNECT_FAIL:
				loginStatus.setText("Username Error: Already in use!");
				break;

			case CLIENT_LIST:
				usersList.getItems().clear();
				for (String u : msg.activeUsers) {
					if (!u.equals(username)) {
						usersList.getItems().add(u);
					}
				}
				break;

			case GAME_START:
				stage.setScene(gameScene);
				stage.setTitle("Checkers Game: " + username + " vs " + msg.sender);
				initializeBoard();

				// Expected format: "myColor,currentTurn"
				String[] startInfo = msg.content.split(",");
				if (startInfo.length >= 2) {
					playerColor = startInfo[0];
					turnIndicator.setText("Whose Turn: " + startInfo[1]);
				} else {
					// fallback if server only sends one value
					turnIndicator.setText("Whose Turn: " + msg.content);
				}
				break;

			case CHAT:
				gameChat.getItems().add(msg.sender + ": " + msg.content);
				break;

			case MOVE:
				if (!boardSquares[msg.startRow][msg.startCol].getChildren().isEmpty()) {
					javafx.scene.Node piece = boardSquares[msg.startRow][msg.startCol].getChildren().remove(0);
					boardSquares[msg.endRow][msg.endCol].getChildren().add(piece);
				}

				if (Math.abs(msg.startRow - msg.endRow) == 2) {
					int jumpedRow = (msg.startRow + msg.endRow) / 2;
					int jumpedCol = (msg.startCol + msg.endCol) / 2;
					boardSquares[jumpedRow][jumpedCol].getChildren().clear();
				}

				String[] payload = msg.content.split(",");
				turnIndicator.setText("Whose Turn: " + payload[0]);

				if (payload.length > 1 && payload[1].equals("true")) {
					Circle promotedPiece = (Circle) boardSquares[msg.endRow][msg.endCol].getChildren().get(0);
					promotedPiece.setStroke(Color.GOLD);
					promotedPiece.setStrokeWidth(4);
				}
				break;

			case GAME_OVER:
				turnIndicator.setText("GAME OVER: " + msg.content);
				break;
		}
	}

	private void sendChatMessage() {
		String content = chatInput.getText();
		if (content.trim().isEmpty()) return;

		Message msg = new Message();
		msg.sender = username;
		msg.content = content;
		msg.type = Message.MessageType.CHAT;

		clientConnection.send(msg);
		chatInput.clear();
	}
}