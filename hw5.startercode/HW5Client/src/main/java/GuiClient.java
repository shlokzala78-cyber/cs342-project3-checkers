
import java.util.HashMap;
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
import java.util.ArrayList;


public class GuiClient extends Application {
	Client clientConnection;
	String username;

	Scene loginScene;
	Scene waitingScene;
	Scene gameScene;
	Scene gameOverScene;

	TextField ipInput;
	TextField usernameInput;
	Label loginStatus;

	// Waiting Room UI Elements
	ListView<String> usersList;
	ListView<String> friendsList;
	Label statsLabel;
	VBox friendRequestsBox; // --- NEW: Holds incoming friend requests ---

	GridPane checkerBoard;
	ListView<String> gameChat;
	TextField chatInput;
	Label turnIndicator;
	Label playerInfoLabel;

	Label gameOverResultLabel;
	Label gameOverOpponentLabel;
	Button playAgainBtn;
	Button quitMatchBtn;
	String currentOpponent = "";

	VBox leftMenu;
	Button easyBtn;
	Button medBtn;
	Button hardBtn;
	Button hintBtn;

	private StackPane[][] boardSquares = new StackPane[8][8];
	private int selectedRow = -1;
	private int selectedCol = -1;

	private HashMap<String, ArrayList<String>> currentValidMoves = new HashMap<>();
	private int myColor = 0;

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		loginScene = createLoginGui(primaryStage);
		waitingScene = createWaitingGui(primaryStage);
		gameScene = createGameGui(primaryStage);
		gameOverScene = createGameOverGui(primaryStage);

		primaryStage.setOnCloseRequest(t -> {
			Platform.exit();
			System.exit(0);
		});

		primaryStage.setScene(loginScene);
		primaryStage.setTitle("Checkers - Login");
		primaryStage.show();
	}

	// --- SCENE 1: LOGIN SCREEN ---
	private Scene createLoginGui(Stage stage) {
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
		Button guestBtn = new Button("Play as Guest");
		guestBtn.setOnAction(e -> {
			usernameInput.setText("Guest_" + (int)(Math.random() * 10000));
			connectBtn.fire();
		});

		loginStatus = new Label("Status: Disconnected");

		connectBtn.setOnAction(e -> {
			clientConnection = new Client(data -> {
				Platform.runLater(() -> handleIncomingMessage(data, stage));
			});
			clientConnection.start();

			new Thread(() -> {
				try { Thread.sleep(200); } catch (Exception ex) {}
				Platform.runLater(() -> {
					Message req = new Message();
					req.type = Message.MessageType.CONNECT;
					req.sender = usernameInput.getText();
					clientConnection.send(req);
				});
			}).start();
		});

		loginBox.getChildren().addAll(title, new Label("Server IP Address:"), ipInput, new Label("Username:"), usernameInput, connectBtn, guestBtn, loginStatus);
		loginBox.setStyle("-fx-background-color: cyan;");

		return new Scene(loginBox, 400, 350);
	}

	// --- SCENE 2: WAITING / MATCHMAKING SCREEN ---
	private Scene createWaitingGui(Stage stage) {
		BorderPane waitingLayout = new BorderPane();
		waitingLayout.setPadding(new Insets(20));
		waitingLayout.setStyle("-fx-background-color: cyan;");

		// Top: Stats & Logout
		VBox topBox = new VBox(10);
		topBox.setAlignment(Pos.CENTER);
		statsLabel = new Label("Wins: 0 | Losses: 0");
		statsLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

		Button logoutBtn = new Button("Back to Login");
		logoutBtn.setStyle("-fx-background-color: #ff6666;");
		logoutBtn.setOnAction(e -> {
			Message req = new Message();
			req.type = Message.MessageType.LOGOUT;
			req.sender = username;
			clientConnection.send(req);

			try { clientConnection.socketClient.close(); } catch(Exception ex) {}
			stage.setScene(loginScene);
			stage.setTitle("Checkers - Login");
		});
		topBox.getChildren().addAll(statsLabel, logoutBtn);
		waitingLayout.setTop(topBox);

		// Center: Lobby Lists
		HBox listsBox = new HBox(20);
		listsBox.setAlignment(Pos.CENTER);

		// 1. Global Lobby Column
		VBox globalLobby = new VBox(5);
		globalLobby.getChildren().add(new Label("Global Lobby:"));
		usersList = new ListView<>();
		usersList.setPrefWidth(180);
		Button challengeBtn = new Button("Challenge Player");
		challengeBtn.setMaxWidth(Double.MAX_VALUE);
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
		Button addFriendBtn = new Button("Add as Friend");
		addFriendBtn.setMaxWidth(Double.MAX_VALUE);
		addFriendBtn.setOnAction(e -> {
			String selected = usersList.getSelectionModel().getSelectedItem();
			if (selected != null) {
				Message req = new Message();
				req.type = Message.MessageType.ADD_FRIEND;
				req.sender = username;
				req.recipient = selected;
				clientConnection.send(req);
			}
		});
		globalLobby.getChildren().addAll(usersList, challengeBtn, addFriendBtn);

		// 2. Online Friends Column
		VBox friendLobby = new VBox(5);
		friendLobby.getChildren().add(new Label("Online Friends:"));
		friendsList = new ListView<>();
		friendsList.setPrefWidth(180);
		Button challengeFriendBtn = new Button("Challenge Friend");
		challengeFriendBtn.setMaxWidth(Double.MAX_VALUE);
		challengeFriendBtn.setOnAction(e -> {
			String selected = friendsList.getSelectionModel().getSelectedItem();
			if (selected != null) {
				Message req = new Message();
				req.type = Message.MessageType.CHALLENGE;
				req.sender = username;
				req.recipient = selected;
				clientConnection.send(req);
			}
		});
		friendLobby.getChildren().addAll(friendsList, challengeFriendBtn);

		// 3. --- NEW: Friend Requests Column ---
		VBox requestsLobby = new VBox(5);
		requestsLobby.setPrefWidth(180);
		requestsLobby.getChildren().add(new Label("Friend Requests:"));

		ScrollPane requestScroll = new ScrollPane();
		requestScroll.setFitToWidth(true);
		requestScroll.setPrefHeight(200); // Matches the visual height of the ListViews

		friendRequestsBox = new VBox(10);
		friendRequestsBox.setPadding(new Insets(5));
		friendRequestsBox.setStyle("-fx-background-color: white;");

		requestScroll.setContent(friendRequestsBox);
		requestsLobby.getChildren().add(requestScroll);

		// Add all 3 columns to the center
		listsBox.getChildren().addAll(globalLobby, friendLobby, requestsLobby);
		waitingLayout.setCenter(listsBox);

		// Bottom: AI Matchmaking
		VBox botBox = new VBox(5);
		botBox.setAlignment(Pos.CENTER);
		botBox.setPadding(new Insets(10, 0, 0, 0));
		Button aiBtn = new Button("Play Single Player vs AI");
		aiBtn.setStyle("-fx-background-color: gold; -fx-font-weight: bold;");
		aiBtn.setOnAction(e -> {
			Message req = new Message();
			req.type = Message.MessageType.PLAY_AI;
			req.sender = username;
			clientConnection.send(req);
		});
		botBox.getChildren().addAll(new Label("--- OR ---"), aiBtn);
		waitingLayout.setBottom(botBox);

		// Increased the width to 750 to comfortably fit all 3 columns
		return new Scene(waitingLayout, 750, 500);
	}

	// --- SCENE 3: GAMEBOARD SCREEN ---
	private Scene createGameGui(Stage stage) {
		BorderPane gameLayout = new BorderPane();
		gameLayout.setPadding(new Insets(10));
		gameLayout.setStyle("-fx-background-color: cyan;");

		VBox topBox = new VBox(5);
		playerInfoLabel = new Label("You are: Waiting...");
		playerInfoLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: darkblue;");
		turnIndicator = new Label("Whose Turn: Waiting for Server...");
		turnIndicator.setStyle("-fx-font-size: 16px;");
		topBox.getChildren().addAll(playerInfoLabel, turnIndicator);
		gameLayout.setTop(topBox);

		leftMenu = new VBox(10);
		leftMenu.setPadding(new Insets(10));
		leftMenu.setPrefWidth(120);
		Label diffLabel = new Label("AI Difficulty:");
		diffLabel.setStyle("-fx-font-weight: bold;");
		easyBtn = new Button("Easy");
		medBtn = new Button("Medium");
		hardBtn = new Button("Hard");

		javafx.event.EventHandler<javafx.event.ActionEvent> setDiff = e -> {
			Button clicked = (Button) e.getSource();
			String diff = clicked.getText();
			Message msg = new Message();
			msg.type = Message.MessageType.SET_DIFFICULTY;
			msg.sender = username;
			msg.content = diff;
			clientConnection.send(msg);

			easyBtn.setDisable(true); medBtn.setDisable(true); hardBtn.setDisable(true);
			hintBtn.setVisible(!diff.equals("Hard"));
			turnIndicator.setText("Whose Turn: Black (AI is thinking...)");
		};

		easyBtn.setOnAction(setDiff); medBtn.setOnAction(setDiff); hardBtn.setOnAction(setDiff);
		leftMenu.getChildren().addAll(diffLabel, easyBtn, medBtn, hardBtn);
		leftMenu.setVisible(false);
		gameLayout.setLeft(leftMenu);

		checkerBoard = new GridPane();
		checkerBoard.setStyle("-fx-border-color: black; -fx-border-width: 2;");
		checkerBoard.setMaxSize(400, 400);
		for (int row = 0; row < 8; row++) {
			for (int col = 0; col < 8; col++) {
				StackPane square = new StackPane();
				square.setPrefSize(50, 50);
				if ((row + col) % 2 == 0) square.setStyle("-fx-background-color: #ffce9e;");
				else square.setStyle("-fx-background-color: #d18b47;");

				final int r = row;
				final int c = col;
				square.setOnMouseClicked(e -> handleSquareClick(r, c));

				boardSquares[row][col] = square;
				checkerBoard.add(square, col, row);
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
		drawBtn.setOnAction(e -> {
			Message req = new Message();
			req.type = Message.MessageType.OFFER_DRAW;
			req.sender = username;
			clientConnection.send(req);
		});

		Button quitBtn = new Button("Quit Game");
		quitBtn.setOnAction(e -> {
			Message req = new Message();
			req.type = Message.MessageType.QUIT;
			req.sender = username;
			clientConnection.send(req);
			stage.setScene(waitingScene);
			stage.setTitle("Checkers - Waiting Room (" + username + ")");
		});

		hintBtn = new Button("Give Me a Hint");
		hintBtn.setStyle("-fx-background-color: lightgreen;");
		hintBtn.setOnAction(e -> {
			Message req = new Message();
			req.type = Message.MessageType.REQUEST_HINT;
			req.sender = username;
			clientConnection.send(req);
		});

		rightMenu.getChildren().addAll(new Label("Text Messaging"), gameChat, chatControls, drawBtn, quitBtn, hintBtn);
		gameLayout.setRight(rightMenu);

		return new Scene(gameLayout, 700, 550);
	}

	// --- SCENE 4: GAME OVER SCREEN ---
	private Scene createGameOverGui(Stage stage) {
		VBox gameOverBox = new VBox(20);
		gameOverBox.setAlignment(Pos.CENTER);
		gameOverBox.setPadding(new Insets(30));
		gameOverBox.setStyle("-fx-background-color: cyan;");

		Label title = new Label("GAME OVER");
		title.setStyle("-fx-font-size: 36px; -fx-font-weight: bold;");

		gameOverResultLabel = new Label("Result: ");
		gameOverResultLabel.setStyle("-fx-font-size: 24px;");

		gameOverOpponentLabel = new Label("Opponent: ");
		gameOverOpponentLabel.setStyle("-fx-font-size: 18px;");

		playAgainBtn = new Button("Play Again");
		playAgainBtn.setStyle("-fx-font-size: 16px;");

		quitMatchBtn = new Button("Return to Lobby");
		quitMatchBtn.setStyle("-fx-font-size: 16px;");

		playAgainBtn.setOnAction(e -> {
			Message req = new Message();
			req.type = Message.MessageType.REMATCH_REQUEST;
			req.sender = username;
			clientConnection.send(req);

			playAgainBtn.setText("Waiting for opponent...");
			playAgainBtn.setDisable(true);
		});

		quitMatchBtn.setOnAction(e -> {
			Message req = new Message();
			req.type = Message.MessageType.QUIT;
			req.sender = username;
			clientConnection.send(req);
			stage.setScene(waitingScene);
			stage.setTitle("Checkers - Waiting Room (" + username + ")");
		});

		gameOverBox.getChildren().addAll(title, gameOverResultLabel, gameOverOpponentLabel, playAgainBtn, quitMatchBtn);
		return new Scene(gameOverBox, 500, 400);
	}

	// --- CLIENT GAME LOGIC ---
	private void initializeBoard() {
		for (int row = 0; row < 8; row++) {
			for (int col = 0; col < 8; col++) {
				boardSquares[row][col].getChildren().clear();
				if ((row + col) % 2 != 0) {
					if (row < 3) addPieceToSquare(row, col, Color.BLACK);
					else if (row > 4) addPieceToSquare(row, col, Color.RED);
				}
			}
		}
		clearHighlights();
	}

	private void addPieceToSquare(int row, int col, Color color) {
		Circle piece = new Circle(20, color);
		piece.setStroke(Color.BLACK);
		piece.setStrokeWidth(2);
		boardSquares[row][col].getChildren().add(piece);
	}

	private void clearHighlights() {
		for (int r = 0; r < 8; r++) {
			for (int c = 0; c < 8; c++) {
				boardSquares[r][c].setStyle(((r + c) % 2 == 0) ? "-fx-background-color: #ffce9e;" : "-fx-background-color: #d18b47;");
			}
		}
	}

	private void autoHighlightJumps() {
		if (currentValidMoves == null || currentValidMoves.isEmpty()) return;

		boolean isJumpTurn = false;
		for (String startKey : currentValidMoves.keySet()) {
			String dest = currentValidMoves.get(startKey).get(0);
			int sr = Integer.parseInt(startKey.split(",")[0]);
			int er = Integer.parseInt(dest.split(",")[0]);
			if (Math.abs(sr - er) == 2) {
				isJumpTurn = true;
				break;
			}
		}

		if (isJumpTurn) {
			for (String startKey : currentValidMoves.keySet()) {
				int sr = Integer.parseInt(startKey.split(",")[0]);
				int sc = Integer.parseInt(startKey.split(",")[1]);
				boardSquares[sr][sc].setStyle("-fx-background-color: yellow; -fx-border-color: red; -fx-border-width: 2;");

				for (String dest : currentValidMoves.get(startKey)) {
					int er = Integer.parseInt(dest.split(",")[0]);
					int ec = Integer.parseInt(dest.split(",")[1]);
					boardSquares[er][ec].setStyle("-fx-background-color: lightgreen; -fx-border-color: green; -fx-border-width: 2;");
				}
			}
		}
	}

	private void handleSquareClick(int row, int col) {
		clearHighlights();

		if (selectedRow == -1 && selectedCol == -1) {
			String key = row + "," + col;
			if (currentValidMoves.containsKey(key)) {
				selectedRow = row;
				selectedCol = col;

				boardSquares[row][col].setStyle("-fx-background-color: yellow; -fx-border-color: red; -fx-border-width: 2;");
				for (String dest : currentValidMoves.get(key)) {
					String[] parts = dest.split(",");
					int dRow = Integer.parseInt(parts[0]);
					int dCol = Integer.parseInt(parts[1]);
					boardSquares[dRow][dCol].setStyle("-fx-background-color: lightgreen; -fx-border-color: green; -fx-border-width: 2;");
				}
			} else {
				autoHighlightJumps();
			}
		} else {
			String startKey = selectedRow + "," + selectedCol;
			String targetKey = row + "," + col;

			if (currentValidMoves.containsKey(startKey) && currentValidMoves.get(startKey).contains(targetKey)) {
				Message moveMsg = new Message();
				moveMsg.type = Message.MessageType.MOVE;
				moveMsg.sender = username;
				moveMsg.startRow = selectedRow;
				moveMsg.startCol = selectedCol;
				moveMsg.endRow = row;
				moveMsg.endCol = col;
				clientConnection.send(moveMsg);
			} else {
				autoHighlightJumps();
			}
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
					if (!u.equals(username) && !msg.onlineFriends.contains(u)) {
						usersList.getItems().add(u);
					}
				}
				friendsList.getItems().clear();
				for (String f : msg.onlineFriends) {
					friendsList.getItems().add(f);
				}
				break;

			case STATS_UPDATE:
				statsLabel.setText("Wins: " + msg.wins + " | Losses: " + msg.losses);
				break;

			// --- NEW: In-UI Friend Requests ---
			case FRIEND_REQUEST:
				HBox requestEntry = new HBox(5);
				requestEntry.setAlignment(Pos.CENTER_LEFT);

				Label reqLabel = new Label(msg.sender);
				reqLabel.setPrefWidth(90); // Keep it neat so buttons align

				Button acceptBtn = new Button("✓");
				acceptBtn.setStyle("-fx-background-color: lightgreen; -fx-padding: 2 5; -fx-font-weight: bold;");

				Button declineBtn = new Button("✗");
				declineBtn.setStyle("-fx-background-color: #ff6666; -fx-padding: 2 5; -fx-font-weight: bold;");

				acceptBtn.setOnAction(e -> {
					Message res = new Message();
					res.sender = username;
					res.recipient = msg.sender;
					res.type = Message.MessageType.FRIEND_ACCEPTED;
					clientConnection.send(res);
					friendRequestsBox.getChildren().remove(requestEntry);
				});

				declineBtn.setOnAction(e -> {
					Message res = new Message();
					res.sender = username;
					res.recipient = msg.sender;
					res.type = Message.MessageType.FRIEND_DECLINED;
					clientConnection.send(res);
					friendRequestsBox.getChildren().remove(requestEntry);
				});

				requestEntry.getChildren().addAll(reqLabel, acceptBtn, declineBtn);
				friendRequestsBox.getChildren().add(requestEntry);
				break;

			case FRIEND_DECLINED:
				// We'll leave this as a small info alert, since it only pops for the sender
				new Alert(Alert.AlertType.INFORMATION, msg.sender + " declined your friend request.").showAndWait();
				break;

			case GAME_START:
				stage.setScene(gameScene);
				currentOpponent = msg.sender;
				stage.setTitle("Checkers Game: " + username + " vs " + currentOpponent);
				initializeBoard();

				myColor = msg.playerColor;
				if (myColor == 1) {
					playerInfoLabel.setText("You are: RED (Moving UP ↑)");
					playerInfoLabel.setTextFill(Color.RED);
				} else {
					playerInfoLabel.setText("You are: BLACK (Moving DOWN ↓)");
					playerInfoLabel.setTextFill(Color.BLACK);
				}

				currentValidMoves = msg.validMoves;
				autoHighlightJumps();

				hintBtn.setVisible(true);
				playAgainBtn.setText("Play Again");
				playAgainBtn.setDisable(false);

				if (msg.sender.equals("Computer (AI)")) {
					leftMenu.setVisible(true);
					easyBtn.setDisable(false); medBtn.setDisable(false); hardBtn.setDisable(false);
					turnIndicator.setText("Select Difficulty to Begin!");
				} else {
					leftMenu.setVisible(false);
					turnIndicator.setText("Whose Turn: Black");
				}
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

				currentValidMoves = msg.validMoves;
				autoHighlightJumps();

				String[] payload = msg.content.split(",");
				turnIndicator.setText("Whose Turn: " + payload[0]);

				if (payload.length > 1 && payload[1].equals("true")) {
					Circle promotedPiece = (Circle) boardSquares[msg.endRow][msg.endCol].getChildren().get(0);
					promotedPiece.setStroke(Color.GOLD);
					promotedPiece.setStrokeWidth(4);
				}
				break;

			case HINT_RESPONSE:
				clearHighlights();
				boardSquares[msg.startRow][msg.startCol].setStyle("-fx-background-color: cyan; -fx-border-color: blue; -fx-border-width: 2;");
				boardSquares[msg.endRow][msg.endCol].setStyle("-fx-background-color: cyan; -fx-border-color: blue; -fx-border-width: 2;");
				break;

			case OFFER_DRAW:
				Alert drawAlert = new Alert(Alert.AlertType.CONFIRMATION, "Your opponent has offered a draw. Do you accept?", ButtonType.YES, ButtonType.NO);
				drawAlert.showAndWait().ifPresent(response -> {
					Message res = new Message();
					res.sender = username;
					res.type = (response == ButtonType.YES) ? Message.MessageType.DRAW_ACCEPTED : Message.MessageType.DRAW_REJECTED;
					clientConnection.send(res);
				});
				break;

			case DRAW_REJECTED:
				new Alert(Alert.AlertType.INFORMATION, "Your opponent declined the draw.").showAndWait();
				break;

			case CHAT:
				gameChat.getItems().add(msg.sender + ": " + msg.content);
				break;

			case GAME_OVER:
				currentValidMoves.clear();
				turnIndicator.setText("GAME OVER: " + msg.content);

				gameOverResultLabel.setText("Result: " + msg.content);
				gameOverOpponentLabel.setText("Opponent: " + currentOpponent);
				stage.setScene(gameOverScene);
				break;

			case REMATCH_REJECTED:
				new Alert(Alert.AlertType.INFORMATION, "Your opponent has left the match.").showAndWait();
				stage.setScene(waitingScene);
				stage.setTitle("Checkers - Waiting Room (" + username + ")");
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
