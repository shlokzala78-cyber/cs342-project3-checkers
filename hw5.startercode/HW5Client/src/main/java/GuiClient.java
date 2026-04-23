import java.util.ArrayList;
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
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

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
	LoginView loginView;

	// Waiting Room UI Elements
	ListView<String> usersList;
	ListView<String> friendsList;
	Label statsLabel;
	VBox friendRequestsBox;

	// Game UI Elements
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

		loginView = new LoginView();
		loginScene = loginView.createScene();

		ipInput = loginView.ipInput;
		usernameInput = loginView.usernameInput;
		loginStatus = loginView.loginStatus;

		loginView.guestBtn.setOnAction(e -> {
			usernameInput.setText("Guest_" + (int) (Math.random() * 10000));
			loginView.connectBtn.fire();
		});

		loginView.connectBtn.setOnAction(e -> {

			try {
				if (clientConnection != null && clientConnection.socketClient != null && !clientConnection.socketClient.isClosed()) {
					clientConnection.socketClient.close();
				}
			} catch (Exception ex) {
				// ignore
			}

			clientConnection = new Client(data -> {
				Platform.runLater(() -> handleIncomingMessage(data, primaryStage));
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

	// --- SCENE 2: WAITING / MATCHMAKING SCREEN ---
	private Scene createWaitingGui(Stage stage) {
		StackPane root = new StackPane();
		root.getStyleClass().add("waiting-root");

		VBox boardStrip = new VBox();
		boardStrip.getStyleClass().add("board-strip");
		boardStrip.setPrefWidth(220);
		boardStrip.setMaxHeight(Double.MAX_VALUE);
		StackPane.setAlignment(boardStrip, Pos.CENTER_LEFT);

		VBox card = new VBox(16);
		card.getStyleClass().add("waiting-card");
		card.setAlignment(Pos.TOP_CENTER);
		card.setMaxWidth(780);
		card.setPadding(new Insets(28, 34, 28, 34));

		HBox topPieces = new HBox(16);
		topPieces.setAlignment(Pos.CENTER);

		Circle redPiece = new Circle(10);
		redPiece.getStyleClass().add("red-piece");

		Circle blackPiece = new Circle(10);
		blackPiece.getStyleClass().add("black-piece");

		topPieces.getChildren().addAll(redPiece, blackPiece);

		Label title = new Label("WAITING ROOM");
		title.getStyleClass().add("waiting-title");

		Label subtitle = new Label("Challenge players, manage friends, or start a single-player match");
		subtitle.getStyleClass().add("waiting-subtitle");
		subtitle.setWrapText(true);
		subtitle.setTextAlignment(TextAlignment.CENTER);

		statsLabel = new Label("Wins: 0 | Losses: 0");
		statsLabel.getStyleClass().add("waiting-section-label");

		Button logoutBtn = new Button("BACK TO LOGIN");
		logoutBtn.getStyleClass().addAll("waiting-btn", "secondary-btn");
		logoutBtn.setOnAction(e -> {
			Message req = new Message();
			req.type = Message.MessageType.LOGOUT;
			req.sender = username;
			clientConnection.send(req);

			try {
				if (clientConnection != null && clientConnection.socketClient != null) {
					clientConnection.socketClient.close();
				}
			} catch (Exception ex) {
				// ignore
			}

			clientConnection = null;
			loginStatus.setText("Status: Disconnected");

			stage.setScene(loginScene);
			stage.setTitle("Checkers - Login");
		});

		HBox headerButtons = new HBox(12, statsLabel, logoutBtn);
		headerButtons.setAlignment(Pos.CENTER);

		HBox listsRow = new HBox(18);
		listsRow.setAlignment(Pos.TOP_CENTER);

		// Global Lobby Column
		VBox globalLobby = new VBox(10);
		globalLobby.setAlignment(Pos.TOP_CENTER);
		globalLobby.setPrefWidth(210);

		Label onlineLabel = new Label("Global Lobby");
		onlineLabel.getStyleClass().add("waiting-section-label");

		usersList = new ListView<>();
		usersList.getStyleClass().add("players-list");
		usersList.setPrefHeight(220);
		usersList.setPrefWidth(210);

		Button challengeBtn = new Button("CHALLENGE PLAYER");
		challengeBtn.getStyleClass().addAll("waiting-btn", "primary-btn");
		challengeBtn.setMaxWidth(Double.MAX_VALUE);
		challengeBtn.setPrefHeight(44);
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

		Button addFriendBtn = new Button("ADD AS FRIEND");
		addFriendBtn.getStyleClass().addAll("waiting-btn", "secondary-btn");
		addFriendBtn.setMaxWidth(Double.MAX_VALUE);
		addFriendBtn.setPrefHeight(40);
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

		globalLobby.getChildren().addAll(onlineLabel, usersList, challengeBtn, addFriendBtn);

		// Friends Column
		VBox friendLobby = new VBox(10);
		friendLobby.setAlignment(Pos.TOP_CENTER);
		friendLobby.setPrefWidth(210);

		Label friendsLabel = new Label("Online Friends");
		friendsLabel.getStyleClass().add("waiting-section-label");

		friendsList = new ListView<>();
		friendsList.getStyleClass().add("players-list");
		friendsList.setPrefHeight(220);
		friendsList.setPrefWidth(210);

		Button challengeFriendBtn = new Button("CHALLENGE FRIEND");
		challengeFriendBtn.getStyleClass().addAll("waiting-btn", "primary-btn");
		challengeFriendBtn.setMaxWidth(Double.MAX_VALUE);
		challengeFriendBtn.setPrefHeight(44);
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

		friendLobby.getChildren().addAll(friendsLabel, friendsList, challengeFriendBtn);

		// Friend Requests Column
		VBox requestsLobby = new VBox(10);
		requestsLobby.setAlignment(Pos.TOP_CENTER);
		requestsLobby.setPrefWidth(210);

		Label requestsLabel = new Label("Friend Requests");
		requestsLabel.getStyleClass().add("waiting-section-label");

		friendRequestsBox = new VBox(10);
		friendRequestsBox.setPadding(new Insets(8));
		friendRequestsBox.setFillWidth(true);

		ScrollPane requestScroll = new ScrollPane(friendRequestsBox);
		requestScroll.setFitToWidth(true);
		requestScroll.setPrefHeight(220);
		requestScroll.getStyleClass().add("players-list");

		requestsLobby.getChildren().addAll(requestsLabel, requestScroll);

		listsRow.getChildren().addAll(globalLobby, friendLobby, requestsLobby);

		Label dividerText = new Label("OR");
		dividerText.getStyleClass().add("waiting-divider");

		Button aiBtn = new Button("PLAY SINGLE PLAYER");
		aiBtn.getStyleClass().addAll("waiting-btn", "ai-btn");
		aiBtn.setMaxWidth(280);
		aiBtn.setPrefHeight(44);
		aiBtn.setOnAction(e -> {
			Message req = new Message();
			req.type = Message.MessageType.PLAY_AI;
			req.sender = username;
			clientConnection.send(req);
		});

		Label tipLabel = new Label("Tip: You can challenge players from the lobby or your online friends list.");
		tipLabel.getStyleClass().add("waiting-tip");

		card.getChildren().addAll(
				topPieces,
				title,
				subtitle,
				headerButtons,
				listsRow,
				dividerText,
				aiBtn,
				tipLabel
		);

		root.getChildren().addAll(boardStrip, card);

		Scene scene = new Scene(root, 1000, 650);
		scene.getStylesheets().add(getClass().getResource("/login.css").toExternalForm());
		return scene;
	}

	// --- SCENE 3: GAMEBOARD SCREEN ---
	private Scene createGameGui(Stage stage) {
		BorderPane gameLayout = new BorderPane();
		gameLayout.getStyleClass().add("game-root");
		gameLayout.setPadding(new Insets(18));

		// TOP INFO BAR
		VBox topBox = new VBox(8);
		topBox.getStyleClass().add("top-info-box");
		topBox.setAlignment(Pos.CENTER);

		playerInfoLabel = new Label("You are: Waiting...");
		playerInfoLabel.getStyleClass().add("player-info-label");

		turnIndicator = new Label("Whose Turn: Waiting for Server...");
		turnIndicator.getStyleClass().add("turn-indicator-label");

		topBox.getChildren().addAll(playerInfoLabel, turnIndicator);
		gameLayout.setTop(topBox);

		// LEFT MENU
		leftMenu = new VBox(12);
		leftMenu.getStyleClass().add("side-panel");
		leftMenu.setPrefWidth(150);
		leftMenu.setAlignment(Pos.TOP_CENTER);

		Label diffLabel = new Label("AI Difficulty");
		diffLabel.getStyleClass().add("panel-title");

		easyBtn = new Button("Easy");
		medBtn = new Button("Medium");
		hardBtn = new Button("Hard");

		easyBtn.getStyleClass().addAll("game-btn", "secondary-game-btn");
		medBtn.getStyleClass().addAll("game-btn", "secondary-game-btn");
		hardBtn.getStyleClass().addAll("game-btn", "secondary-game-btn");

		easyBtn.setMaxWidth(Double.MAX_VALUE);
		medBtn.setMaxWidth(Double.MAX_VALUE);
		hardBtn.setMaxWidth(Double.MAX_VALUE);

		javafx.event.EventHandler<javafx.event.ActionEvent> setDiff = e -> {
			Button clicked = (Button) e.getSource();
			String diff = clicked.getText();

			Message msg = new Message();
			msg.type = Message.MessageType.SET_DIFFICULTY;
			msg.sender = username;
			msg.content = diff;
			clientConnection.send(msg);

			easyBtn.setDisable(true);
			medBtn.setDisable(true);
			hardBtn.setDisable(true);

			hintBtn.setVisible(!diff.equals("Hard"));
			turnIndicator.setText("Whose Turn: Black (AI is thinking...)");
		};

		easyBtn.setOnAction(setDiff);
		medBtn.setOnAction(setDiff);
		hardBtn.setOnAction(setDiff);

		leftMenu.getChildren().addAll(diffLabel, easyBtn, medBtn, hardBtn);
		leftMenu.setVisible(false);
		gameLayout.setLeft(leftMenu);

		// CENTER BOARD WRAPPER
		StackPane boardWrapper = new StackPane();
		boardWrapper.getStyleClass().add("board-wrapper");
		boardWrapper.setPadding(new Insets(10));

		checkerBoard = new GridPane();
		checkerBoard.getStyleClass().add("board");
		checkerBoard.setAlignment(Pos.CENTER);
		checkerBoard.setHgap(0);
		checkerBoard.setVgap(0);
		checkerBoard.setMaxSize(480, 480);

		for (int row = 0; row < 8; row++) {
			for (int col = 0; col < 8; col++) {
				StackPane square = new StackPane();
				square.setPrefSize(58, 58);

				if ((row + col) % 2 == 0) {
					square.getStyleClass().addAll("light-square", "square-hover");
				} else {
					square.getStyleClass().addAll("dark-square", "square-hover");
				}

				final int r = row;
				final int c = col;
				square.setOnMouseClicked(e -> handleSquareClick(r, c));

				boardSquares[row][col] = square;
				checkerBoard.add(square, col, row);
			}
		}

		boardWrapper.getChildren().add(checkerBoard);
		gameLayout.setCenter(boardWrapper);

		// RIGHT PANEL
		VBox rightMenu = new VBox(12);
		rightMenu.getStyleClass().add("side-panel");
		rightMenu.setPrefWidth(260);

		Label chatTitle = new Label("Match Chat");
		chatTitle.getStyleClass().add("panel-title");

		gameChat = new ListView<>();
		gameChat.getStyleClass().add("game-chat-list");
		gameChat.setPrefHeight(260);

		chatInput = new TextField();
		chatInput.setPromptText("Type a message...");
		chatInput.getStyleClass().add("game-chat-input");

		Button sendBtn = new Button("Send");
		sendBtn.getStyleClass().addAll("game-btn", "primary-game-btn");
		sendBtn.setOnAction(e -> sendChatMessage());

		HBox chatControls = new HBox(8, chatInput, sendBtn);
		chatControls.setAlignment(Pos.CENTER);
		HBox.setHgrow(chatInput, Priority.ALWAYS);

		Button drawBtn = new Button("Offer Draw");
		drawBtn.getStyleClass().addAll("game-btn", "secondary-game-btn");
		drawBtn.setMaxWidth(Double.MAX_VALUE);
		drawBtn.setOnAction(e -> {
			Message req = new Message();
			req.type = Message.MessageType.OFFER_DRAW;
			req.sender = username;
			clientConnection.send(req);
		});

		Button quitBtn = new Button("Quit Game");
		quitBtn.getStyleClass().addAll("game-btn", "danger-game-btn");
		quitBtn.setMaxWidth(Double.MAX_VALUE);
		quitBtn.setOnAction(e -> {
			Message req = new Message();
			req.type = Message.MessageType.QUIT;
			req.sender = username;
			clientConnection.send(req);
			stage.setScene(waitingScene);
			stage.setTitle("Checkers - Waiting Room (" + username + ")");
		});

		hintBtn = new Button("Give Me a Hint");
		hintBtn.getStyleClass().addAll("game-btn", "hint-game-btn");
		hintBtn.setMaxWidth(Double.MAX_VALUE);
		hintBtn.setOnAction(e -> {
			Message req = new Message();
			req.type = Message.MessageType.REQUEST_HINT;
			req.sender = username;
			clientConnection.send(req);
		});

		rightMenu.getChildren().addAll(
				chatTitle,
				gameChat,
				chatControls,
				drawBtn,
				quitBtn,
				hintBtn
		);

		gameLayout.setRight(rightMenu);

		Scene scene = new Scene(gameLayout, 980, 680);
		scene.getStylesheets().add(getClass().getResource("/game.css").toExternalForm());
		return scene;
	}

	// --- SCENE 4: GAME OVER SCREEN ---
	private Scene createGameOverGui(Stage stage) {
		VBox gameOverBox = new VBox(20);
		gameOverBox.getStyleClass().add("gameover-root");
		gameOverBox.setAlignment(Pos.CENTER);
		gameOverBox.setPadding(new Insets(30));

		Label title = new Label("GAME OVER");
		title.getStyleClass().add("gameover-title");

		gameOverResultLabel = new Label("Result: ");
		gameOverResultLabel.getStyleClass().add("gameover-result");

		gameOverOpponentLabel = new Label("Opponent: ");
		gameOverOpponentLabel.getStyleClass().add("gameover-opponent");

		playAgainBtn = new Button("Play Again");
		playAgainBtn.getStyleClass().addAll("game-btn", "primary-game-btn");

		quitMatchBtn = new Button("Return to Lobby");
		quitMatchBtn.getStyleClass().addAll("game-btn", "secondary-game-btn");

		playAgainBtn.setOnAction(e -> {
			Message req = new Message();
			req.type = Message.MessageType.REMATCH_REQUEST;
			req.sender = username;
			clientConnection.send(req);

			playAgainBtn.setText("Waiting...");
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

		gameOverBox.getChildren().addAll(
				title,
				gameOverResultLabel,
				gameOverOpponentLabel,
				playAgainBtn,
				quitMatchBtn
		);

		Scene scene = new Scene(gameOverBox, 500, 400);
		scene.getStylesheets().add(getClass().getResource("/game.css").toExternalForm());
		return scene;
	}

	// --- CLIENT GAME LOGIC ---
	private void initializeBoard() {
		for (int row = 0; row < 8; row++) {
			for (int col = 0; col < 8; col++) {
				boardSquares[row][col].getChildren().clear();
				resetSquareStyle(row, col);

				if ((row + col) % 2 != 0) {
					if (row < 3) {
						addPieceToSquare(row, col, Color.BLACK);
					} else if (row > 4) {
						addPieceToSquare(row, col, Color.RED);
					}
				}
			}
		}
		clearHighlights();
	}

	private void addPieceToSquare(int row, int col, Color color) {
		StackPane pieceHolder = new StackPane();

		Circle outer = new Circle(20);
		Circle inner = new Circle(13);

		if (color == Color.RED) {
			outer.getStyleClass().add("game-red-piece");
			inner.getStyleClass().add("game-red-piece-inner");
		} else {
			outer.getStyleClass().add("game-black-piece");
			inner.getStyleClass().add("game-black-piece-inner");
		}

		pieceHolder.getChildren().addAll(outer, inner);
		boardSquares[row][col].getChildren().add(pieceHolder);
	}

	private void resetSquareStyle(int row, int col) {
		StackPane square = boardSquares[row][col];
		square.getStyleClass().removeAll("selected-square", "valid-square", "hint-square");

		if ((row + col) % 2 == 0) {
			square.getStyleClass().remove("dark-square");
			if (!square.getStyleClass().contains("light-square")) {
				square.getStyleClass().add("light-square");
			}
		} else {
			square.getStyleClass().remove("light-square");
			if (!square.getStyleClass().contains("dark-square")) {
				square.getStyleClass().add("dark-square");
			}
		}

		if (!square.getStyleClass().contains("square-hover")) {
			square.getStyleClass().add("square-hover");
		}
	}

	private void clearHighlights() {
		for (int r = 0; r < 8; r++) {
			for (int c = 0; c < 8; c++) {
				resetSquareStyle(r, c);
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
				boardSquares[sr][sc].getStyleClass().add("selected-square");

				for (String dest : currentValidMoves.get(startKey)) {
					int er = Integer.parseInt(dest.split(",")[0]);
					int ec = Integer.parseInt(dest.split(",")[1]);
					boardSquares[er][ec].getStyleClass().add("valid-square");
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

				boardSquares[row][col].getStyleClass().add("selected-square");

				for (String dest : currentValidMoves.get(key)) {
					String[] parts = dest.split(",");
					int dRow = Integer.parseInt(parts[0]);
					int dCol = Integer.parseInt(parts[1]);
					boardSquares[dRow][dCol].getStyleClass().add("valid-square");
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
				if (msg.activeUsers != null) {
					for (String u : msg.activeUsers) {
						boolean onlineFriend = msg.onlineFriends != null && msg.onlineFriends.contains(u);
						if (!u.equals(username) && !onlineFriend) {
							usersList.getItems().add(u);
						}
					}
				}

				if (friendsList != null) {
					friendsList.getItems().clear();
					if (msg.onlineFriends != null) {
						friendsList.getItems().addAll(msg.onlineFriends);
					}
				}
				break;

			case STATS_UPDATE:
				if (statsLabel != null) {
					statsLabel.setText("Wins: " + msg.wins + " | Losses: " + msg.losses);
				}
				break;

			case FRIEND_REQUEST:
				HBox requestEntry = new HBox(5);
				requestEntry.setAlignment(Pos.CENTER_LEFT);

				Label reqLabel = new Label(msg.sender);
				reqLabel.setPrefWidth(90);

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
					easyBtn.setDisable(false);
					medBtn.setDisable(false);
					hardBtn.setDisable(false);
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
					if (!boardSquares[msg.endRow][msg.endCol].getChildren().isEmpty()) {
						StackPane promotedHolder = (StackPane) boardSquares[msg.endRow][msg.endCol].getChildren().get(0);
						Label crown = new Label("♛");
						crown.getStyleClass().add("king-crown");
						promotedHolder.getChildren().add(crown);
					}
				}
				break;

			case HINT_RESPONSE:
				clearHighlights();
				boardSquares[msg.startRow][msg.startCol].getStyleClass().add("hint-square");
				boardSquares[msg.endRow][msg.endCol].getStyleClass().add("hint-square");
				break;

			case OFFER_DRAW:
				Alert drawAlert = new Alert(Alert.AlertType.CONFIRMATION,
						"Your opponent has offered a draw. Do you accept?",
						ButtonType.YES, ButtonType.NO);
				drawAlert.showAndWait().ifPresent(response -> {
					Message res = new Message();
					res.sender = username;
					res.type = (response == ButtonType.YES)
							? Message.MessageType.DRAW_ACCEPTED
							: Message.MessageType.DRAW_REJECTED;
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