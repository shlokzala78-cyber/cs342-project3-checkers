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
			try (ServerSocket my_socket = new ServerSocket(5555)) {
				callback.accept("Server is waiting for clients on port 5555...");
				while (true) {
					ClientThread c = new ClientThread(my_socket.accept(), count);
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
		boolean isAI = false;
		String aiDifficulty = "Medium";

		// 0=Empty, 1=Red, 2=Black, 3=RedKing, 4=BlackKing
		int[][] board = new int[8][8];

		boolean redTurn = false; // Rule: Black moves first
		int activeJumpRow = -1;
		int activeJumpCol = -1;

		boolean isGameOver = false;
		boolean redWantsRematch = false;
		boolean blackWantsRematch = false;

		GameSession(ClientThread p1, ClientThread p2, boolean isAI, String diff) {
			this.redPlayer = p1;
			this.blackPlayer = p2;
			this.isAI = isAI;
			if (diff != null) this.aiDifficulty = diff;
			setupBoard();
		}

		GameSession(ClientThread p1, ClientThread p2) {
			this(p1, p2, false, "Medium");
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

			startMsg.sender = isAI ? "Computer (AI)" : blackPlayer.username;
			try {
				redPlayer.out.writeObject(startMsg);
			} catch (Exception e) {
			}

			if (!isAI) {
				startMsg.sender = redPlayer.username;
				try {
					blackPlayer.out.writeObject(startMsg);
				} catch (Exception e) {
				}
			}

			callback.accept("Match Started: " + redPlayer.username + " vs " + startMsg.sender);
		}

		public synchronized void handleSetDifficulty(String diff) {
			this.aiDifficulty = diff;
			callback.accept("AI Difficulty set to: " + diff);

			if (isAI && !redTurn && !isGameOver) {
				triggerAITurn();
			}
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

			if (isAI && !redTurn && !isGameOver) {
				triggerAITurn();
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

			try { redPlayer.out.writeObject(overMsg); } catch (Exception e) {}
			// Only send to black if it's a real human player
			if (!isAI) {
				try { blackPlayer.out.writeObject(overMsg); } catch (Exception e) {}
			}
			callback.accept("Game Over. " + winnerName);
		}

		public synchronized void handleRematchRequest(ClientThread sender) {
			if (sender == redPlayer) redWantsRematch = true;
			else if (sender == blackPlayer) blackWantsRematch = true;

			// If playing AI, automatically accept the rematch!
			if (isAI && redWantsRematch) blackWantsRematch = true;

			if (redWantsRematch && blackWantsRematch) {
				isGameOver = false;
				redWantsRematch = false;
				blackWantsRematch = false;
				redTurn = false;

				setupBoard();
				startGame();
			}
		}

		public synchronized void handleQuit(ClientThread sender) {
			if (!isAI) {
				ClientThread opponent = (sender == redPlayer) ? blackPlayer : redPlayer;
				Message rejectMsg = new Message();
				rejectMsg.type = Message.MessageType.REMATCH_REJECTED;
				try { opponent.out.writeObject(rejectMsg); } catch (Exception e) {}
				activeGames.remove(blackPlayer.username);
			}

			activeGames.remove(redPlayer.username);
			redPlayer.broadcastClientList();
		}

		private void triggerAITurn() {
			new Thread(() -> {
				try { Thread.sleep(800); } catch (Exception e) {}

				ArrayList<Message> legalMoves = generateAllLegalMoves(false);

				if (legalMoves.isEmpty()) {
					handleGameOver(redPlayer.username + " Wins!");
					return;
				}

				Message bestMove = legalMoves.get(0);

				if (aiDifficulty.equals("Easy")) {
					// EASY: Pick a completely random legal move
					bestMove = legalMoves.get((int)(Math.random() * legalMoves.size()));
				}
				else {
					// MEDIUM & HARD: Evaluate board states
					int bestScore = Integer.MIN_VALUE;

					for (Message move : legalMoves) {
						int score = simulateAndScoreMove(move);
						if (score > bestScore) {
							bestScore = score;
							bestMove = move;
						}
					}
				}

				handleMove(bestMove, null);
			}).start();
		}

		// Generates every possible legal move for the AI
		private ArrayList<Message> generateAllLegalMoves(boolean isRed) {
			ArrayList<Message> moves = new ArrayList<>();
			boolean mustJump = canPlayerJump(isRed);
			int[] dRow = {-1, -1, 1, 1, -2, -2, 2, 2};
			int[] dCol = {-1, 1, -1, 1, -2, 2, -2, 2};

			for (int r = 0; r < 8; r++) {
				for (int c = 0; c < 8; c++) {
					int piece = board[r][c];
					if (!isRed && (piece == 2 || piece == 4)) {
						if (activeJumpRow != -1 && (r != activeJumpRow || c != activeJumpCol)) continue;

						for (int i = 0; i < 8; i++) {
							int er = r + dRow[i];
							int ec = c + dCol[i];
							boolean isJump = Math.abs(dRow[i]) == 2;

							if (mustJump && !isJump) continue;

							if (isValidMove(r, c, er, ec, isRed, piece, isJump)) {
								Message m = new Message();
								m.type = Message.MessageType.MOVE;
								m.startRow = r; m.startCol = c;
								m.endRow = er; m.endCol = ec;
								moves.add(m);
							}
						}
					}
				}
			}
			return moves;
		}

		// Scores a hypothetical move
		private int simulateAndScoreMove(Message m) {
			int[][] tempBoard = new int[8][8];
			for (int i = 0; i < 8; i++) System.arraycopy(board[i], 0, tempBoard[i], 0, 8);

			// Execute hypothetical move
			int piece = tempBoard[m.startRow][m.startCol];
			tempBoard[m.endRow][m.endCol] = piece;
			tempBoard[m.startRow][m.startCol] = 0;

			if (Math.abs(m.startRow - m.endRow) == 2) {
				tempBoard[(m.startRow + m.endRow) / 2][(m.startCol + m.endCol) / 2] = 0;
			}
			if (piece == 2 && m.endRow == 7) tempBoard[m.endRow][m.endCol] = 4;

			int score = 0;
			for (int r = 0; r < 8; r++) {
				for (int c = 0; c < 8; c++) {
					if (tempBoard[r][c] == 2) score += (10 + r);
					else if (tempBoard[r][c] == 4) score += 30;
					else if (tempBoard[r][c] == 1) score -= 10;
					else if (tempBoard[r][c] == 3) score -= 30;
				}
			}

			// HARD MODE UPGRADE: Tactical Look-Ahead
			// Penalize moves that leave the AI vulnerable to being jumped by the human next turn
			if (aiDifficulty.equals("Hard") && humanCanJump(tempBoard)) {
				score -= 50;
			}

			return score;
		}

		// Helper for Hard Mode: Checks if the Human (Red) has a valid jump on the hypothetical board
		private boolean humanCanJump(int[][] tempBoard) {
			int[] dRow = {-2, -2};
			int[] dCol = {-2, 2};

			for (int r = 0; r < 8; r++) {
				for (int c = 0; c < 8; c++) {
					int piece = tempBoard[r][c];

					if (piece == 1 || piece == 3) {
						for(int i=0; i<2; i++) {
							int jumpRow = r + dRow[i];
							int jumpCol = c + dCol[i];
							int midRow = r + (dRow[i]/2);
							int midCol = c + (dCol[i]/2);

							if (jumpRow >= 0 && jumpRow < 8 && jumpCol >= 0 && jumpCol < 8) {
								if (tempBoard[jumpRow][jumpCol] == 0) {
									int midPiece = tempBoard[midRow][midCol];
									if (midPiece == 2 || midPiece == 4) return true; // Human can capture!
								}
							}
						}
						// Check backward jumps if Human has a King
						if (piece == 3) {
							int[] dRowK = {2, 2};
							for(int i=0; i<2; i++) {
								int jumpRow = r + dRowK[i];
								int jumpCol = c + dCol[i];
								int midRow = r + (dRowK[i]/2);
								int midCol = c + (dCol[i]/2);

								if (jumpRow >= 0 && jumpRow < 8 && jumpCol >= 0 && jumpCol < 8 && tempBoard[jumpRow][jumpCol] == 0) {
									int midPiece = tempBoard[midRow][midCol];
									if (midPiece == 2 || midPiece == 4) return true;
								}
							}
						}
					}
				}
			}
			return false;
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

						case PLAY_AI:
							if (!activeGames.containsKey(this.username)) {
								// Default to Medium, it will be overwritten shortly
								GameSession newGame = new GameSession(this, null, true, "Medium");
								activeGames.put(this.username, newGame);
								newGame.startGame();
								broadcastClientList();
							}
							break;

						case SET_DIFFICULTY:
							if (activeGames.containsKey(this.username)) {
								activeGames.get(this.username).handleSetDifficulty(data.content);
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


	
	

	
