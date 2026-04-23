import java.util.ArrayList;
import java.util.HashMap;

import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.media.AudioClip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.geometry.Bounds;


public class GuiClient extends Application {
	Client clientConnection;
	String username;

	Scene loginScene;
	Scene waitingScene;
	Scene gameScene;
	Scene gameOverScene;
	Scene difficultyScene;
	String selectedDifficulty = "";
	Label difficultyLabel;

	TextField ipInput;
	TextField usernameInput;
	Label loginStatus;
	LoginView loginView;
	Label waitingStatusLabel;

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

    VBox incomingChallengeBox;
    Label incomingChallengeLabel;
    Button acceptChallengeBtn;
    Button rejectChallengeBtn;
    String pendingChallenger;

    Button hintBtn;

	private StackPane[][] boardSquares = new StackPane[8][8];
	private int selectedRow = -1;
	private int selectedCol = -1;

	private AudioClip clickSound;
	private AudioClip moveSound;
	private AudioClip captureSound;

	private HashMap<String, ArrayList<String>> currentValidMoves = new HashMap<>();
	private int myColor = 0;

	public static void main(String[] args) {
		launch(args);
	}

	private Scene createDifficultyGui(Stage stage) {
		VBox root = new VBox(20);
		root.setAlignment(Pos.CENTER);
		root.setPadding(new Insets(30));
		root.getStyleClass().add("waiting-root");

		Label title = new Label("SELECT AI DIFFICULTY");
		title.getStyleClass().add("waiting-title");

		Label subtitle = new Label("Choose a difficulty level before starting your single-player match");
		subtitle.getStyleClass().add("waiting-subtitle");
		subtitle.setWrapText(true);
		subtitle.setTextAlignment(TextAlignment.CENTER);

		Button easyBtn = new Button("Easy");
		Button mediumBtn = new Button("Medium");
		Button hardBtn = new Button("Hard");
		Button backBtn = new Button("Back");

		easyBtn.getStyleClass().addAll("waiting-btn", "primary-btn");
		mediumBtn.getStyleClass().addAll("waiting-btn", "primary-btn");
		hardBtn.getStyleClass().addAll("waiting-btn", "primary-btn");
		backBtn.getStyleClass().addAll("waiting-btn", "secondary-btn");

		easyBtn.setPrefWidth(240);
		mediumBtn.setPrefWidth(240);
		hardBtn.setPrefWidth(240);
		backBtn.setPrefWidth(240);

		easyBtn.setOnAction(e -> startSinglePlayerGame("Easy"));
		mediumBtn.setOnAction(e -> startSinglePlayerGame("Medium"));
		hardBtn.setOnAction(e -> startSinglePlayerGame("Hard"));

		backBtn.setOnAction(e -> {
			playSound(clickSound);
			stage.setScene(waitingScene);
			stage.setTitle("Checkers - Waiting Room (" + username + ")");
		});

		root.getChildren().addAll(title, subtitle, easyBtn, mediumBtn, hardBtn, backBtn);

		Scene scene = new Scene(root, 700, 500);
		scene.getStylesheets().add(getClass().getResource("/login.css").toExternalForm());
		return scene;
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		loginView = new LoginView();
		loginScene = loginView.createScene();

		ipInput = loginView.ipInput;
		usernameInput = loginView.usernameInput;
		loginStatus = loginView.loginStatus;

		clickSound = loadSound("/sounds/click.mp3");
		moveSound = loadSound("/sounds/move.mp3");
		captureSound = loadSound("/sounds/capture.mp3");

		if (clickSound != null) {
			clickSound.setVolume(0);
			clickSound.play();
		}
		if (moveSound != null) {
			moveSound.setVolume(0);
			moveSound.play();
		}
		if (captureSound != null) {
			captureSound.setVolume(0);
			captureSound.play();
		}

		loginView.guestBtn.setOnAction(e -> {
			usernameInput.setText("Guest_" + (int) (Math.random() * 10000));
			loginView.connectBtn.fire();
		});

		loginView.connectBtn.setOnAction(e -> {
			playSound(clickSound);

			try {
				if (clientConnection != null &&
						clientConnection.socketClient != null &&
						!clientConnection.socketClient.isClosed()) {
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
				try {
					Thread.sleep(200);
				} catch (Exception ex) {
					// ignore
				}

				Platform.runLater(() -> {
					Message req = new Message();
					req.type = Message.MessageType.CONNECT;
					req.sender = usernameInput.getText();
					clientConnection.send(req);
				});
			}).start();
		});

		waitingScene = createWaitingGui(primaryStage);
		difficultyScene = createDifficultyGui(primaryStage);
		gameScene = createGameGui(primaryStage);
		gameOverScene = createGameOverGui(primaryStage);

		primaryStage.setOnCloseRequest(t -> {
			Platform.exit();
			System.exit(0);
		});

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down client...");
            Platform.exit();
        }));

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

		VBox card = new VBox(18);
		card.getStyleClass().add("waiting-card");
		card.setAlignment(Pos.TOP_CENTER);
		card.setMaxWidth(900);
		card.setPadding(new Insets(30));

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
			playSound(clickSound);

			Message req = new Message();
			req.type = Message.MessageType.LOGOUT;
			req.sender = username;
			clientConnection.send(req);

			stage.setScene(loginScene);
			stage.setTitle("Checkers - Login");
		});

		HBox header = new HBox(20, statsLabel, logoutBtn);
		header.setAlignment(Pos.CENTER);

		HBox listsRow = new HBox(25);
		listsRow.setAlignment(Pos.TOP_CENTER);

		// ===== GLOBAL LOBBY =====
		VBox globalLobby = new VBox(10);
		globalLobby.setAlignment(Pos.TOP_CENTER);
		globalLobby.setPrefWidth(240);

		Label onlineLabel = new Label("Global Lobby");
		onlineLabel.getStyleClass().add("waiting-section-label");

		usersList = new ListView<>();
		usersList.getStyleClass().add("players-list");
		usersList.setPrefHeight(270);
		usersList.setMinHeight(270);
		usersList.setPrefWidth(240);

		Button challengeBtn = new Button("CHALLENGE PLAYER");
		challengeBtn.getStyleClass().addAll("waiting-btn", "primary-btn");
		challengeBtn.setMaxWidth(Double.MAX_VALUE);
		challengeBtn.setPrefHeight(44);
		challengeBtn.setOnAction(e -> {
			playSound(clickSound);

			String selected = usersList.getSelectionModel().getSelectedItem();
			if (selected != null) {
				Message req = new Message();
				req.type = Message.MessageType.CHALLENGE;
				req.sender = username;
				req.recipient = selected;
				clientConnection.send(req);

				waitingStatusLabel.setText("Challenge sent to " + selected + ". Waiting for response...");
			} else {
				waitingStatusLabel.setText("Please select a player first.");
			}
		});

		Button addFriendBtn = new Button("ADD AS FRIEND");
		addFriendBtn.getStyleClass().addAll("waiting-btn", "secondary-btn");
		addFriendBtn.setMaxWidth(Double.MAX_VALUE);
		addFriendBtn.setPrefHeight(40);
		addFriendBtn.setOnAction(e -> {
			playSound(clickSound);

			String selected = usersList.getSelectionModel().getSelectedItem();
			if (selected != null) {
				Message req = new Message();
				req.type = Message.MessageType.ADD_FRIEND;
				req.sender = username;
				req.recipient = selected;
				clientConnection.send(req);

				waitingStatusLabel.setText("Friend request sent to " + selected + ".");
			} else {
				waitingStatusLabel.setText("Please select a player first.");
			}
		});

		globalLobby.getChildren().addAll(onlineLabel, usersList, challengeBtn, addFriendBtn);

		// ===== FRIENDS =====
		VBox friendLobby = new VBox(10);
		friendLobby.setAlignment(Pos.TOP_CENTER);
		friendLobby.setPrefWidth(240);

		Label friendsLabel = new Label("Online Friends");
		friendsLabel.getStyleClass().add("waiting-section-label");

		friendsList = new ListView<>();
		friendsList.getStyleClass().add("players-list");
		friendsList.setPrefHeight(270);
		friendsList.setMinHeight(270);
		friendsList.setPrefWidth(240);

		Button challengeFriendBtn = new Button("CHALLENGE FRIEND");
		challengeFriendBtn.getStyleClass().addAll("waiting-btn", "primary-btn");
		challengeFriendBtn.setMaxWidth(Double.MAX_VALUE);
		challengeFriendBtn.setPrefHeight(44);
		challengeFriendBtn.setOnAction(e -> {
			playSound(clickSound);

			String selected = friendsList.getSelectionModel().getSelectedItem();
			if (selected != null) {
				Message req = new Message();
				req.type = Message.MessageType.CHALLENGE;
				req.sender = username;
				req.recipient = selected;
				clientConnection.send(req);

				waitingStatusLabel.setText("Challenge sent to " + selected + ". Waiting for response...");
			} else {
				waitingStatusLabel.setText("Please select a friend first.");
			}
		});

		friendLobby.getChildren().addAll(friendsLabel, friendsList, challengeFriendBtn);

		// ===== FRIEND REQUESTS =====
		VBox requestsLobby = new VBox(10);
		requestsLobby.setAlignment(Pos.TOP_CENTER);
		requestsLobby.setPrefWidth(240);

		Label requestsLabel = new Label("Friend Requests");
		requestsLabel.getStyleClass().add("waiting-section-label");

		friendRequestsBox = new VBox(10);
		friendRequestsBox.setPadding(new Insets(8));

		ScrollPane requestScroll = new ScrollPane(friendRequestsBox);
		requestScroll.getStyleClass().add("players-list");
		requestScroll.setFitToWidth(true);
		requestScroll.setPrefHeight(270);
		requestScroll.setMinHeight(270);
		requestScroll.setPrefWidth(240);

		requestsLobby.getChildren().addAll(requestsLabel, requestScroll);

		listsRow.getChildren().addAll(globalLobby, friendLobby, requestsLobby);

		// ===== CHALLENGE BOX =====
		incomingChallengeBox = new VBox(12);
		incomingChallengeBox.setAlignment(Pos.CENTER);
		incomingChallengeBox.setVisible(false);
		incomingChallengeBox.setManaged(false);
		incomingChallengeBox.getStyleClass().add("waiting-card");
		incomingChallengeBox.setPadding(new Insets(15));
		incomingChallengeBox.setMaxWidth(520);

		incomingChallengeLabel = new Label("Incoming Challenge");
		incomingChallengeLabel.getStyleClass().add("waiting-section-label");

		acceptChallengeBtn = new Button("ACCEPT");
		acceptChallengeBtn.getStyleClass().addAll("waiting-btn", "primary-btn");
		acceptChallengeBtn.setPrefWidth(140);

		rejectChallengeBtn = new Button("REJECT");
		rejectChallengeBtn.getStyleClass().addAll("waiting-btn", "secondary-btn");
		rejectChallengeBtn.setPrefWidth(140);

		acceptChallengeBtn.setOnAction(e -> {
			playSound(clickSound);

			Message res = new Message();
			res.type = Message.MessageType.CHALLENGE_ACCEPTED;
			res.sender = username;
			res.recipient = pendingChallenger;
			clientConnection.send(res);

			incomingChallengeBox.setVisible(false);
			incomingChallengeBox.setManaged(false);
			acceptChallengeBtn.setVisible(true);
			rejectChallengeBtn.setText("REJECT");

			waitingStatusLabel.setText("Challenge accepted.");
		});

		rejectChallengeBtn.setOnAction(e -> {
			playSound(clickSound);

			Message res = new Message();
			res.type = Message.MessageType.CHALLENGE_REJECTED;
			res.sender = username;
			res.recipient = pendingChallenger;
			clientConnection.send(res);

			incomingChallengeBox.setVisible(false);
			incomingChallengeBox.setManaged(false);
			acceptChallengeBtn.setVisible(true);
			rejectChallengeBtn.setText("REJECT");

			waitingStatusLabel.setText("Challenge rejected.");
		});

		HBox challengeButtons = new HBox(12, acceptChallengeBtn, rejectChallengeBtn);
		challengeButtons.setAlignment(Pos.CENTER);

		incomingChallengeBox.getChildren().addAll(incomingChallengeLabel, challengeButtons);

		waitingStatusLabel = new Label(" ");
		waitingStatusLabel.getStyleClass().add("waiting-tip");
		waitingStatusLabel.setWrapText(true);
		waitingStatusLabel.setTextAlignment(TextAlignment.CENTER);
		waitingStatusLabel.setMaxWidth(650);

		Label dividerText = new Label("OR");
		dividerText.getStyleClass().add("waiting-divider");

		Button aiBtn = new Button("PLAY SINGLE PLAYER");
		aiBtn.getStyleClass().addAll("waiting-btn", "ai-btn");
		aiBtn.setMaxWidth(320);
		aiBtn.setPrefHeight(44);
		aiBtn.setOnAction(e -> {
			playSound(clickSound);
			stage.setScene(difficultyScene);
			stage.setTitle("Checkers - Select Difficulty");
		});

		Label tipLabel = new Label("Tip: You can challenge players from the lobby or your online friends list.");
		tipLabel.getStyleClass().add("waiting-tip");

		card.getChildren().addAll(
				topPieces,
				title,
				subtitle,
				header,
				listsRow,
				incomingChallengeBox,
				waitingStatusLabel,
				dividerText,
				aiBtn,
				tipLabel
		);

		VBox scrollWrapper = new VBox(card);
		scrollWrapper.setAlignment(Pos.TOP_CENTER);
		scrollWrapper.setFillWidth(true);

		ScrollPane outerScroll = new ScrollPane(scrollWrapper);
		outerScroll.setFitToWidth(true);
		outerScroll.setFitToHeight(false);
		outerScroll.setPannable(true);
		outerScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		outerScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

		// important → make background transparent
		outerScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

		StackPane.setAlignment(outerScroll, Pos.CENTER);
		root.getChildren().addAll(boardStrip, outerScroll);

		Scene scene = new Scene(root, 1100, 750);
		scene.getStylesheets().add(getClass().getResource("/login.css").toExternalForm());
		return scene;
	}

	private void startSinglePlayerGame(String difficulty) {
		playSound(clickSound);

		selectedDifficulty = difficulty;

		Message req = new Message();
		req.type = Message.MessageType.PLAY_AI;
		req.sender = username;
		req.difficulty = difficulty;   // use the field already present in Message.java
		clientConnection.send(req);
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

		difficultyLabel = new Label("Difficulty: --");
		difficultyLabel.getStyleClass().add("player-info-label");

        topBox.getChildren().addAll(playerInfoLabel, turnIndicator, difficultyLabel);
        gameLayout.setTop(topBox);

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
				square.setPrefSize(64, 64);

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

		animationLayer = new Pane();
		animationLayer.setPickOnBounds(false);
		animationLayer.setMouseTransparent(true);

		boardWrapper.getChildren().addAll(checkerBoard, animationLayer);
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
		sendBtn.setOnAction(e -> {
			playSound(clickSound);
			sendChatMessage();
		});

		HBox chatControls = new HBox(8, chatInput, sendBtn);
		chatControls.setAlignment(Pos.CENTER);
		HBox.setHgrow(chatInput, Priority.ALWAYS);

		Button drawBtn = new Button("Offer Draw");
		drawBtn.getStyleClass().addAll("game-btn", "secondary-game-btn");
		drawBtn.setMaxWidth(Double.MAX_VALUE);
		drawBtn.setOnAction(e -> {
			playSound(clickSound);

			Message req = new Message();
			req.type = Message.MessageType.OFFER_DRAW;
			req.sender = username;
			clientConnection.send(req);
		});

		Button quitBtn = new Button("Quit Game");
		quitBtn.getStyleClass().addAll("game-btn", "danger-game-btn");
		quitBtn.setMaxWidth(Double.MAX_VALUE);
		quitBtn.setOnAction(e -> {
			playSound(clickSound);

			Message req = new Message();
			req.type = Message.MessageType.QUIT;
			req.sender = username;
			clientConnection.send(req);

            selectedDifficulty = "";
            if (difficultyLabel != null) {
                difficultyLabel.setText("Difficulty: --");
            }

			stage.setScene(waitingScene);
			stage.setTitle("Checkers - Waiting Room (" + username + ")");
		});

		hintBtn = new Button("Give Me a Hint");
		hintBtn.getStyleClass().addAll("game-btn", "hint-game-btn");
		hintBtn.setMaxWidth(Double.MAX_VALUE);
		hintBtn.setOnAction(e -> {
			playSound(clickSound);

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
	private Pane animationLayer;

	private void animatePieceMove(
			int startRow, int startCol,
			int endRow, int endCol,
			boolean wasCapture,
			Message msg
	) {
		if (boardSquares[startRow][startCol].getChildren().isEmpty()) {
			return;
		}

		Node piece = boardSquares[startRow][startCol].getChildren().remove(0);

		Bounds startBoundsScene = boardSquares[startRow][startCol].localToScene(
				boardSquares[startRow][startCol].getBoundsInLocal()
		);
		Bounds endBoundsScene = boardSquares[endRow][endCol].localToScene(
				boardSquares[endRow][endCol].getBoundsInLocal()
		);
		Bounds layerBoundsScene = animationLayer.localToScene(
				animationLayer.getBoundsInLocal()
		);

		double startX = startBoundsScene.getMinX() - layerBoundsScene.getMinX();
		double startY = startBoundsScene.getMinY() - layerBoundsScene.getMinY();

		double endX = endBoundsScene.getMinX() - layerBoundsScene.getMinX();
		double endY = endBoundsScene.getMinY() - layerBoundsScene.getMinY();

		animationLayer.getChildren().add(piece);
		piece.setTranslateX(0);
		piece.setTranslateY(0);
		piece.relocate(startX, startY);

		Node capturedPiece = null;
		int jumpedRow = -1;
		int jumpedCol = -1;

		if (wasCapture) {
			jumpedRow = (startRow + endRow) / 2;
			jumpedCol = (startCol + endCol) / 2;

			if (!boardSquares[jumpedRow][jumpedCol].getChildren().isEmpty()) {
				capturedPiece = boardSquares[jumpedRow][jumpedCol].getChildren().get(0);

				FadeTransition fade = new FadeTransition(Duration.millis(220), capturedPiece);
				fade.setFromValue(1.0);
				fade.setToValue(0.15);
				fade.play();
			}
		}

		TranslateTransition transition =
				new TranslateTransition(Duration.millis(wasCapture ? 240 : 180), piece);
		transition.setToX(endX - startX);
		transition.setToY(endY - startY);

		final Node finalCapturedPiece = capturedPiece;
		final int finalJumpedRow = jumpedRow;
		final int finalJumpedCol = jumpedCol;

		transition.setOnFinished(e -> {
			animationLayer.getChildren().remove(piece);
			piece.setTranslateX(0);
			piece.setTranslateY(0);
			piece.relocate(0, 0);

			boardSquares[endRow][endCol].getChildren().add(piece);

			if (wasCapture && finalJumpedRow != -1 && finalJumpedCol != -1) {
				boardSquares[finalJumpedRow][finalJumpedCol].getChildren().clear();
			}

			if (finalCapturedPiece != null) {
				finalCapturedPiece.setOpacity(1.0);
			}

			currentValidMoves = msg.validMoves;
			autoHighlightJumps();

			String[] payload = msg.content.split(",");
			turnIndicator.setText("Whose Turn: " + payload[0]);

			if (payload.length > 1 && payload[1].equals("true")) {
				if (!boardSquares[endRow][endCol].getChildren().isEmpty()) {
					StackPane promotedHolder = (StackPane) boardSquares[endRow][endCol].getChildren().get(0);
					Label crown = new Label("♛");
					crown.getStyleClass().add("king-crown");
					promotedHolder.getChildren().add(crown);
				}
			}
		});

		transition.play();
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
			playSound(clickSound);

			if (currentOpponent != null && currentOpponent.contains("Computer")) {
				Message req = new Message();
				req.type = Message.MessageType.PLAY_AI;
				req.sender = username;
				req.difficulty = selectedDifficulty;
				clientConnection.send(req);

				playAgainBtn.setText("Starting...");
				playAgainBtn.setDisable(true);
			}
			else if ("Accept Rematch".equals(playAgainBtn.getText())) {
				Message req = new Message();
				req.type = Message.MessageType.REMATCH_REQUEST;
				req.sender = username;
				clientConnection.send(req);

				playAgainBtn.setText("Waiting...");
				playAgainBtn.setDisable(true);
			}
			else {
				Message req = new Message();
				req.type = Message.MessageType.REMATCH_REQUEST;
				req.sender = username;
				clientConnection.send(req);

				playAgainBtn.setText("Request Sent");
				playAgainBtn.setDisable(true);
			}
		});

		quitMatchBtn.setOnAction(e -> {
			playSound(clickSound);

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
				HBox requestEntry = new HBox(8);
				requestEntry.setAlignment(Pos.CENTER_LEFT);
				requestEntry.setPadding(new Insets(4, 6, 4, 6));

				Label reqLabel = new Label(msg.sender);
				reqLabel.setPrefWidth(110);
				reqLabel.setStyle("-fx-text-fill: #3a2718; -fx-font-weight: bold; -fx-font-size: 14px;");

				Button acceptBtn = new Button("✓");
				acceptBtn.setStyle("-fx-background-color: lightgreen; -fx-padding: 2 8; -fx-font-weight: bold;");

				Button declineBtn = new Button("✗");
				declineBtn.setStyle("-fx-background-color: #ff6666; -fx-padding: 2 8; -fx-font-weight: bold;");

				acceptBtn.setOnAction(e -> {
					playSound(clickSound);

					Message res = new Message();
					res.sender = username;
					res.recipient = msg.sender;
					res.type = Message.MessageType.FRIEND_ACCEPTED;
					clientConnection.send(res);
					friendRequestsBox.getChildren().remove(requestEntry);
				});

				declineBtn.setOnAction(e -> {
					playSound(clickSound);

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

			case CHALLENGE_REQUEST:
				pendingChallenger = msg.sender;
				incomingChallengeLabel.setText(msg.sender + " challenged you!");
				acceptChallengeBtn.setVisible(true);
				rejectChallengeBtn.setText("REJECT");
				incomingChallengeBox.setManaged(true);
				incomingChallengeBox.setVisible(true);
				waitingStatusLabel.setText("Incoming challenge from " + msg.sender + ".");
				break;

			case CHALLENGE_REJECTED:
				incomingChallengeLabel.setText(msg.sender + " rejected your challenge.");
				acceptChallengeBtn.setVisible(false);
				rejectChallengeBtn.setText("OK");
				incomingChallengeBox.setManaged(true);
				incomingChallengeBox.setVisible(true);
				waitingStatusLabel.setText(msg.sender + " rejected your challenge.");

				rejectChallengeBtn.setOnAction(e -> {
					playSound(clickSound);
					incomingChallengeBox.setVisible(false);
					incomingChallengeBox.setManaged(false);
					acceptChallengeBtn.setVisible(true);
					rejectChallengeBtn.setText("REJECT");
					waitingStatusLabel.setText(" ");

					rejectChallengeBtn.setOnAction(ev -> {
						playSound(clickSound);

						Message res = new Message();
						res.type = Message.MessageType.CHALLENGE_REJECTED;
						res.sender = username;
						res.recipient = pendingChallenger;
						clientConnection.send(res);

						incomingChallengeBox.setVisible(false);
						incomingChallengeBox.setManaged(false);
						acceptChallengeBtn.setVisible(true);
						rejectChallengeBtn.setText("REJECT");
					});
				});
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

				currentValidMoves = (msg.validMoves != null) ? msg.validMoves : new HashMap<>();
				autoHighlightJumps();

				hintBtn.setVisible(true);
				playAgainBtn.setText("Play Again");
				playAgainBtn.setDisable(false);

                if (msg.sender.equals("Computer (AI)")) {
                    difficultyLabel.setText("Difficulty: " + selectedDifficulty);
                    turnIndicator.setText("Whose Turn: Black");
                    hintBtn.setVisible(!selectedDifficulty.equals("Hard"));
                } else {
                    difficultyLabel.setText("Difficulty: --");
                    turnIndicator.setText("Whose Turn: Black");
                    hintBtn.setVisible(true);
                }
				break;

			case MOVE:
				boolean wasCapture = Math.abs(msg.startRow - msg.endRow) == 2;

				if (wasCapture) {
					playCaptureSound();
				} else {
					playMoveSound();
				}

				animatePieceMove(
						msg.startRow,
						msg.startCol,
						msg.endRow,
						msg.endCol,
						wasCapture,
						msg
				);
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

				playAgainBtn.setText("Play Again");
				playAgainBtn.setDisable(false);
				quitMatchBtn.setText("Return to Lobby");

				stage.setScene(gameOverScene);
				break;

			case REMATCH_PENDING:
				gameOverResultLabel.setText(msg.sender + " wants to play again!");
				playAgainBtn.setText("Accept Rematch");
				playAgainBtn.setDisable(false);
				quitMatchBtn.setText("Decline / Return to Lobby");
				stage.setScene(gameOverScene);
				break;

			case REMATCH_REJECTED:
				gameOverResultLabel.setText("Opponent left the match.");
				gameOverOpponentLabel.setText("Opponent: " + currentOpponent);
				playAgainBtn.setText("Unavailable");
				playAgainBtn.setDisable(true);
				quitMatchBtn.setText("Return to Lobby");
				stage.setScene(gameOverScene);
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

	private AudioClip loadSound(String path) {
		try {
			var url = GuiClient.class.getResource(path);
			if (url == null) {
				return null;
			}
			return new AudioClip(url.toExternalForm());
		} catch (Exception e) {
			return null;
		}
	}

	private void playClickSound() {
		if (clickSound != null) {
			clickSound.play(0.65);
		}
	}

	private void playMoveSound() {
		if (moveSound != null) {
			moveSound.play(0.45);
		}
	}

	private void playCaptureSound() {
		if (captureSound != null) {
			captureSound.play(0.9);
		}
	}

	private void playSound(AudioClip sound) {
		if (sound != null) {
			sound.play(0.65);
		}
	}
}