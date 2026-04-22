import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.scene.control.ListView;

// Main class for server
// Creates thread for each connected client and manages them simultaneously
public class Server{

	int count = 1; // Tracks the total number of connection attempts
	ConcurrentHashMap<String, ClientThread> clients = new ConcurrentHashMap<>();
	ConcurrentHashMap<String, GameSession> activeGames = new ConcurrentHashMap<>();
	TheServer server;
	private Consumer<Serializable> callback;
	
	
	Server(Consumer<Serializable> call){
	
		callback = call;
		server = new TheServer();
		server.start();
	}


	public class TheServer extends Thread {
		public void run() {
			try (ServerSocket mysocket = new ServerSocket(5555)) {
				callback.accept("Server is waiting for clients on port 5555...");
				while (true) {
					ClientThread c = new ClientThread(mysocket.accept(), count);
					c.start();
					count++;
				}
			} catch (Exception e) {
				callback.accept("Server socket did not launch");
			}
		}
	}

	// Managing Game Session
	class GameSession {
		ClientThread redPlayer;
		ClientThread blackPlayer;

		// 0=Empty, 1=Red, 2=Black, 3=RedKing, 4=BlackKing
		int[][] board = new int[8][8];

		boolean redTurn = false; // Rule: Black moves first
		int activeJumpRow = -1;
		int activeJumpCol = -1;

		boolean isGameOver = false;
		boolean redWantsRematch = false;
		boolean blackWantsRematch = false;

		GameSession(ClientThread p1, ClientThread p2) {
			this.redPlayer = p1;
			this.blackPlayer = p2;
			setupBoard();
		}

		private void setupBoard() {
			for (int r = 0; r < 8; r++) {
				for (int c = 0; c < 8; c++) {
					if ((r + c) % 2 != 0) {
						if (r < 3) board[r][c] = 2; // Black
						else if (r > 4) board[r][c] = 1; // Red
						else board[r][c] = 0;
					}
				}
			}
		}

		public void startGame() {
			Message startMsg = new Message();
			startMsg.type = Message.MessageType.GAME_START;

			startMsg.sender = blackPlayer.username;
			try { redPlayer.out.writeObject(startMsg); } catch (Exception e) {}

			startMsg.sender = redPlayer.username;
			try { blackPlayer.out.writeObject(startMsg); } catch (Exception e) {}

			callback.accept("Match Started: " + redPlayer.username + " (Red) vs " + blackPlayer.username + " (Black)");
		}

		public synchronized void handleMove(Message moveMsg, ClientThread sender) {
			boolean isRed = sender == redPlayer;

			// 1. Turn Check
			if (isRed != redTurn) return;

			int pieceType = board[moveMsg.startRow][moveMsg.startCol];

			// 2. Ownership Check
			if ((isRed && pieceType != 1 && pieceType != 3) || (!isRed && pieceType != 2 && pieceType != 4)) return;

			// 3. Multi-Jump Constraint Check (Must use the active piece if mid-jump sequence)
			if (activeJumpRow != -1 && (moveMsg.startRow != activeJumpRow || moveMsg.startCol != activeJumpCol)) return;

			// 4. Forced Jump Check (Rule: Must jump if one presents itself)
			boolean jumpAvailable = canPlayerJump(isRed);
			boolean isAttemptingJump = Math.abs(moveMsg.startRow - moveMsg.endRow) == 2;
			if (jumpAvailable && !isAttemptingJump) return; // Reject normal move if a jump exists

			// 5. Specific Move Validation
			if (!isValidMove(moveMsg.startRow, moveMsg.startCol, moveMsg.endRow, moveMsg.endCol, isRed, pieceType, isAttemptingJump)) return;

			board[moveMsg.endRow][moveMsg.endCol] = pieceType;
			board[moveMsg.startRow][moveMsg.startCol] = 0;

			if (isAttemptingJump) {
				int jumpedRow = (moveMsg.startRow + moveMsg.endRow) / 2;
				int jumpedCol = (moveMsg.startCol + moveMsg.endCol) / 2;
				board[jumpedRow][jumpedCol] = 0; // Remove captured piece
			}

			// KING PROMOTION
			boolean promotedThisTurn = false;
			if (pieceType == 1 && moveMsg.endRow == 0) {
				board[moveMsg.endRow][moveMsg.endCol] = 3; // Red King
				promotedThisTurn = true;
			} else if (pieceType == 2 && moveMsg.endRow == 7) {
				board[moveMsg.endRow][moveMsg.endCol] = 4; // Black King
				promotedThisTurn = true;
			}


			// JUMP & TURN LOGIC
			// If they jumped, didn't get promoted, and can jump again with the same piece -> turn continues
			if (isAttemptingJump && !promotedThisTurn && canPieceJump(moveMsg.endRow, moveMsg.endCol, isRed, board[moveMsg.endRow][moveMsg.endCol])) {
				activeJumpRow = moveMsg.endRow;
				activeJumpCol = moveMsg.endCol;
				moveMsg.content = (redTurn ? "Red" : "Black") + " (Must Jump Again!)," + promotedThisTurn;
			} else {

				activeJumpRow = -1;
				activeJumpCol = -1;
				redTurn = !redTurn;
				moveMsg.content = (redTurn ? "Red" : "Black") + "," + promotedThisTurn;
			}


			try {
				redPlayer.out.writeObject(moveMsg);
				blackPlayer.out.writeObject(moveMsg);
			} catch (Exception e) {}

			// CHECK WIN CONDITION
			if (!playerHasAnyMoves(redTurn)) {

				handleGameOver(redTurn ? blackPlayer.username : redPlayer.username);
			}
		}

		// HELPER METHODS

		private boolean isValidMove(int sr, int sc, int er, int ec, boolean isRed, int piece, boolean isJump) {
			if (er < 0 || er > 7 || ec < 0 || ec > 7) return false; // Out of bounds
			if (board[er][ec] != 0) return false; // Destination must be empty

			int rowDiff = er - sr;
			int colDiff = ec - sc;

			boolean movingForward = (isRed && rowDiff < 0) || (!isRed && rowDiff > 0);
			boolean isKing = (piece == 3 || piece == 4);

			if (!isKing && !movingForward) return false; // Regular pieces cannot move backward

			if (isJump) {
				if (Math.abs(rowDiff) != 2 || Math.abs(colDiff) != 2) return false;
				int jumpedPiece = board[(sr + er) / 2][(sc + ec) / 2];
				if (jumpedPiece == 0) return false;

				// Must jump opponent
				return (isRed && (jumpedPiece == 2 || jumpedPiece == 4)) ||
						(!isRed && (jumpedPiece == 1 || jumpedPiece == 3));
			} else {
				return Math.abs(rowDiff) == 1 && Math.abs(colDiff) == 1; // Standard move length
			}
		}

		private boolean canPlayerJump(boolean isRed) {
			for (int r = 0; r < 8; r++) {
				for (int c = 0; c < 8; c++) {
					int piece = board[r][c];
					if ((isRed && (piece == 1 || piece == 3)) || (!isRed && (piece == 2 || piece == 4))) {
						if (canPieceJump(r, c, isRed, piece)) return true;
					}
				}
			}
			return false;
		}

		private boolean canPieceJump(int r, int c, boolean isRed, int piece) {
			int[] dRow = {-2, -2, 2, 2};
			int[] dCol = {-2, 2, -2, 2};
			for (int i = 0; i < 4; i++) {
				if (isValidMove(r, c, r + dRow[i], c + dCol[i], isRed, piece, true)) return true;
			}
			return false;
		}

		private boolean playerHasAnyMoves(boolean isRed) {
			if (canPlayerJump(isRed)) return true;

			// Check for any valid standard moves
			int[] dRow = {-1, -1, 1, 1};
			int[] dCol = {-1, 1, -1, 1};
			for (int r = 0; r < 8; r++) {
				for (int c = 0; c < 8; c++) {
					int piece = board[r][c];
					if ((isRed && (piece == 1 || piece == 3)) || (!isRed && (piece == 2 || piece == 4))) {
						for (int i = 0; i < 4; i++) {
							if (isValidMove(r, c, r + dRow[i], c + dCol[i], isRed, piece, false)) return true;
						}
					}
				}
			}
			return false;
		}

		public void handleGameOver(String winnerName) {
			isGameOver = true;
			redWantsRematch = false;
			blackWantsRematch = false;

			Message overMsg = new Message();
			overMsg.type = Message.MessageType.GAME_OVER;
			overMsg.content = winnerName;

			try {
				redPlayer.out.writeObject(overMsg);
				blackPlayer.out.writeObject(overMsg);
			} catch (Exception e) {}

			callback.accept("Game Over. " + winnerName);
			// Notice we do NOT remove them from activeGames yet. We wait for their button clicks!
		}

		public synchronized void handleRematchRequest(ClientThread sender) {
			if (sender == redPlayer) redWantsRematch = true;
			else if (sender == blackPlayer) blackWantsRematch = true;

			// If BOTH players clicked Play Again, restart the game!
			if (redWantsRematch && blackWantsRematch) {
				isGameOver = false;
				redWantsRematch = false;
				blackWantsRematch = false;
				redTurn = false; // Black always moves first

				setupBoard();
				startGame();
			}
		}

		public synchronized void handleQuit(ClientThread sender) {
			// Figure out who the opponent is
			ClientThread opponent = (sender == redPlayer) ? blackPlayer : redPlayer;

			// Tell the opponent the match was cancelled
			Message rejectMsg = new Message();
			rejectMsg.type = Message.MessageType.REMATCH_REJECTED;
			try { opponent.out.writeObject(rejectMsg); } catch (Exception e) {}

			// Now we remove them from the active games list
			activeGames.remove(redPlayer.username);
			activeGames.remove(blackPlayer.username);

			redPlayer.broadcastClientList();
		}
	}


	class ClientThread extends Thread {
		Socket connection;
		int count;
		ObjectInputStream in;
		ObjectOutputStream out;
		String username = "";

		ClientThread(Socket s, int count) {
			this.connection = s;
			this.count = count;
		}

		public void broadcastClientList() {
			Message updateMsg = new Message();
			updateMsg.type = Message.MessageType.CLIENT_LIST;
			updateMsg.activeUsers = new ArrayList<>();

			// Only add users who are NOT currently in a game to the waiting room list
			for (String user : clients.keySet()) {
				if (!activeGames.containsKey(user)) {
					updateMsg.activeUsers.add(user);
				}
			}

			for (ClientThread t : clients.values()) {
				try { t.out.writeObject(updateMsg); } catch (Exception e) {}
			}
		}

		public void run() {
			try {
				out = new ObjectOutputStream(connection.getOutputStream());
				in = new ObjectInputStream(connection.getInputStream());
				connection.setTcpNoDelay(true);
			} catch (Exception e) {
				callback.accept("Streams not open for client #" + count);
				return;
			}

			while (true) {
				try {
					Message data = (Message) in.readObject();

					switch (data.type) {
						case CONNECT:
							if (clients.containsKey(data.sender) || data.sender.trim().isEmpty()) {
								Message failMsg = new Message();
								failMsg.type = Message.MessageType.CONNECT_FAIL;
								out.writeObject(failMsg);
							} else {
								this.username = data.sender;
								clients.put(this.username, this);

								Message successMsg = new Message();
								successMsg.type = Message.MessageType.CONNECT_SUCCESS;
								out.writeObject(successMsg);

								callback.accept(username + " joined the server.");
								broadcastClientList();
							}
							break;

						case CHALLENGE:
							if (clients.containsKey(data.recipient) && !activeGames.containsKey(data.recipient)) {
								ClientThread opponent = clients.get(data.recipient);
								GameSession newGame = new GameSession(this, opponent);

								activeGames.put(this.username, newGame);
								activeGames.put(opponent.username, newGame);

								newGame.startGame();
								broadcastClientList(); // Remove these two from waiting list
							}
							break;

						case MOVE:
							if (activeGames.containsKey(this.username)) {
								activeGames.get(this.username).handleMove(data, this);
							}
							break;

						case CHAT:
							if (activeGames.containsKey(this.username)) {
								GameSession game = activeGames.get(this.username);
								ClientThread opponent = (game.redPlayer == this) ? game.blackPlayer : game.redPlayer;

								this.out.writeObject(data);
								opponent.out.writeObject(data);
							}
							break;

						case REMATCH_REQUEST:
							if (activeGames.containsKey(this.username)) {
								activeGames.get(this.username).handleRematchRequest(this);
							}
							break;

						case QUIT:
							if (activeGames.containsKey(this.username)) {
								GameSession game = activeGames.get(this.username);

								if (game.isGameOver) {
									// They are quitting from the Game Over screen
									game.handleQuit(this);
								} else {
									// They clicked quit in the middle of an active game (Forfeit)
									String opponentName = (game.redPlayer == this) ? game.blackPlayer.username : game.redPlayer.username;
									game.handleGameOver(opponentName + " Wins! (Opponent Forfeit)");
									game.handleQuit(this);
								}
							}
							break;
					}
				} catch (Exception e) {
					if (!this.username.isEmpty()) {
						if (activeGames.containsKey(this.username)) {
							GameSession game = activeGames.get(this.username);
							String opponentName = (game.redPlayer == this) ? game.blackPlayer.username : game.redPlayer.username;
							game.handleGameOver(opponentName + " (Opponent Disconnected)");
						}

						callback.accept(this.username + " has left the server.");
						clients.remove(this.username);
						broadcastClientList();
					}
					break;
				}
			}
		}
	}
}


	
	

	
