import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;


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

		private HashMap<String, ArrayList<String>> getValidMovesMap(boolean isRed) {
			HashMap<String, ArrayList<String>> map = new HashMap<>();
			ArrayList<Message> moves = generateAllLegalMoves(isRed);
			for (Message m : moves) {
				String start = m.startRow + "," + m.startCol;
				String end = m.endRow + "," + m.endCol;
				map.putIfAbsent(start, new ArrayList<>());
				map.get(start).add(end);
			}
			return map;
		}

		public void startGame() {
			Message startMsgRed = new Message();
			startMsgRed.type = Message.MessageType.GAME_START;
			startMsgRed.sender = isAI ? "Computer (AI)" : blackPlayer.username;
			startMsgRed.playerColor = 1; // Tell Red they are Red
			startMsgRed.validMoves = getValidMovesMap(false); // Black goes first, so UI highlights black moves

			Message startMsgBlack = new Message();
			if (!isAI) {
				startMsgBlack.type = Message.MessageType.GAME_START;
				startMsgBlack.sender = redPlayer.username;
				startMsgBlack.playerColor = 2; // Tell Black they are Black
				startMsgBlack.validMoves = getValidMovesMap(false);
			}

			try {
				redPlayer.out.writeObject(startMsgRed);
				if (!isAI) blackPlayer.out.writeObject(startMsgBlack);
			} catch (Exception e) {}

			callback.accept("Match Started: " + redPlayer.username + " vs " + startMsgRed.sender);
		}

		public synchronized void handleSetDifficulty(String diff) {
			this.aiDifficulty = diff;
			callback.accept("AI Difficulty set to: " + diff);
			if (isAI && !redTurn && !isGameOver) triggerAITurn();
		}

		public synchronized void handleMove(Message moveMsg, ClientThread sender) {
			boolean isRed = sender == redPlayer;
			if (isRed != redTurn) return;

			int pieceType = board[moveMsg.startRow][moveMsg.startCol];
			if ((isRed && pieceType != 1 && pieceType != 3) || (!isRed && pieceType != 2 && pieceType != 4)) return;
			if (activeJumpRow != -1 && (moveMsg.startRow != activeJumpRow || moveMsg.startCol != activeJumpCol)) return;

			boolean jumpAvailable = canPlayerJump(isRed);
			boolean isAttemptingJump = Math.abs(moveMsg.startRow - moveMsg.endRow) == 2;
			if (jumpAvailable && !isAttemptingJump) return;
			if (!isValidMove(moveMsg.startRow, moveMsg.startCol, moveMsg.endRow, moveMsg.endCol, isRed, pieceType, isAttemptingJump)) return;

			// Execute Move
			board[moveMsg.endRow][moveMsg.endCol] = pieceType;
			board[moveMsg.startRow][moveMsg.startCol] = 0;

			if (isAttemptingJump) {
				int jumpedRow = (moveMsg.startRow + moveMsg.endRow) / 2;
				int jumpedCol = (moveMsg.startCol + moveMsg.endCol) / 2;
				board[jumpedRow][jumpedCol] = 0;
			}

			boolean promotedThisTurn = false;
			if (pieceType == 1 && moveMsg.endRow == 0) {
				board[moveMsg.endRow][moveMsg.endCol] = 3;
				promotedThisTurn = true;
			} else if (pieceType == 2 && moveMsg.endRow == 7) {
				board[moveMsg.endRow][moveMsg.endCol] = 4;
				promotedThisTurn = true;
			}

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

			// --- Attach Valid Moves for the NEXT player to the message! ---
			moveMsg.validMoves = getValidMovesMap(redTurn);

			try {
				redPlayer.out.writeObject(moveMsg);
				if (!isAI) blackPlayer.out.writeObject(moveMsg);
			} catch (Exception e) {}

			if (!playerHasAnyMoves(redTurn)) {
				handleGameOver(redTurn ? (isAI ? "Computer (AI)" : blackPlayer.username) : redPlayer.username);
			} else if (isAI && !redTurn && !isGameOver) {
				triggerAITurn();
			}
		}

		// --- RULE ENGINE (Unchanged, handles multi jumps natively) ---
		private boolean isValidMove(int sr, int sc, int er, int ec, boolean isRed, int piece, boolean isJump) {
			if (er < 0 || er > 7 || ec < 0 || ec > 7) return false;
			if (board[er][ec] != 0) return false;

			int rowDiff = er - sr;
			int colDiff = ec - sc;
			boolean movingForward = (isRed && rowDiff < 0) || (!isRed && rowDiff > 0);
			boolean isKing = (piece == 3 || piece == 4);

			if (!isKing && !movingForward) return false;

			if (isJump) {
				if (Math.abs(rowDiff) != 2 || Math.abs(colDiff) != 2) return false;
				int jumpedPiece = board[(sr + er) / 2][(sc + ec) / 2];
				if (jumpedPiece == 0) return false;

				return (isRed && (jumpedPiece == 2 || jumpedPiece == 4)) ||
						(!isRed && (jumpedPiece == 1 || jumpedPiece == 3));
			} else {
				return Math.abs(rowDiff) == 1 && Math.abs(colDiff) == 1;
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
			if (!isAI) {
				try { blackPlayer.out.writeObject(overMsg); } catch (Exception e) {}
			}
			callback.accept("Game Over. " + winnerName);
		}

		public synchronized void handleRematchRequest(ClientThread sender) {
			if (sender == redPlayer) redWantsRematch = true;
			else if (sender == blackPlayer) blackWantsRematch = true;

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

		// --- AI ENGINE / HINTS ---
		public ArrayList<Message> generateAllLegalMoves(boolean isRed) {
			ArrayList<Message> moves = new ArrayList<>();
			boolean mustJump = canPlayerJump(isRed);
			int[] dRow = {-1, -1, 1, 1, -2, -2, 2, 2};
			int[] dCol = {-1, 1, -1, 1, -2, 2, -2, 2};

			for (int r = 0; r < 8; r++) {
				for (int c = 0; c < 8; c++) {
					int piece = board[r][c];
					if ((isRed && (piece == 1 || piece == 3)) || (!isRed && (piece == 2 || piece == 4))) {
						if (activeJumpRow != -1 && (r != activeJumpRow || c != activeJumpCol)) continue;

						for (int i = 0; i < 8; i++) {
							int er = r + dRow[i];
							int ec = c + dCol[i];
							boolean isJump = Math.abs(dRow[i]) == 2;
							if (mustJump && !isJump) continue;
							if (isValidMove(r, c, er, ec, isRed, piece, isJump)) {
								Message m = new Message();
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

		public int simulateAndScoreMove(Message m) {
			int[][] tempBoard = new int[8][8];
			for (int i = 0; i < 8; i++) System.arraycopy(board[i], 0, tempBoard[i], 0, 8);

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

			if (aiDifficulty.equals("Hard") && humanCanJump(tempBoard)) score -= 50;
			return score;
		}

		private boolean humanCanJump(int[][] tempBoard) {
			int[] dRow = {-2, -2};
			int[] dCol = {-2, 2};
			for (int r = 0; r < 8; r++) {
				for (int c = 0; c < 8; c++) {
					int piece = tempBoard[r][c];
					if (piece == 1 || piece == 3) {
						for(int i=0; i<2; i++) {
							int jumpRow = r + dRow[i]; int jumpCol = c + dCol[i];
							int midRow = r + (dRow[i]/2); int midCol = c + (dCol[i]/2);
							if (jumpRow >= 0 && jumpRow < 8 && jumpCol >= 0 && jumpCol < 8 && tempBoard[jumpRow][jumpCol] == 0) {
								int midPiece = tempBoard[midRow][midCol];
								if (midPiece == 2 || midPiece == 4) return true;
							}
						}
					}
				}
			}
			return false;
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
					bestMove = legalMoves.get((int)(Math.random() * legalMoves.size()));
				} else {
					int bestScore = Integer.MIN_VALUE;
					for (Message move : legalMoves) {
						int score = simulateAndScoreMove(move);
						if (score > bestScore) { bestScore = score; bestMove = move; }
					}
				}
				bestMove.type = Message.MessageType.MOVE;
				handleMove(bestMove, null);
			}).start();
		}
	}

	// INDIVIDUAL CLIENT HANDLER
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
			for (String user : clients.keySet()) {
				if (!activeGames.containsKey(user)) updateMsg.activeUsers.add(user);
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
			} catch (Exception e) { return; }

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
								broadcastClientList();
							}
							break;

						case CHALLENGE:
							if (clients.containsKey(data.recipient) && !activeGames.containsKey(data.recipient)) {
								GameSession newGame = new GameSession(this, clients.get(data.recipient));
								activeGames.put(this.username, newGame);
								activeGames.put(data.recipient, newGame);
								newGame.startGame();
								broadcastClientList();
							}
							break;

						case PLAY_AI:
							if (!activeGames.containsKey(this.username)) {
								GameSession newGame = new GameSession(this, null, true, "Medium");
								activeGames.put(this.username, newGame);
								newGame.startGame();
								broadcastClientList();
							}
							break;

						case SET_DIFFICULTY:
							if (activeGames.containsKey(this.username)) activeGames.get(this.username).handleSetDifficulty(data.content);
							break;

						case MOVE:
							if (activeGames.containsKey(this.username)) activeGames.get(this.username).handleMove(data, this);
							break;

						// --- NEW: Handle Hint Requests by running the AI engine for the human! ---
						case REQUEST_HINT:
							if (activeGames.containsKey(this.username)) {
								GameSession game = activeGames.get(this.username);
								boolean isRedReq = (this == game.redPlayer);

								// Only give a hint if it's actually their turn
								if (isRedReq == game.redTurn) {
									ArrayList<Message> legal = game.generateAllLegalMoves(isRedReq);
									if (!legal.isEmpty()) {
										Message bestMove = legal.get(0);
										int bestScore = Integer.MIN_VALUE;
										for (Message m : legal) {
											int score = game.simulateAndScoreMove(m);
											// Reverse the score logic because simulateAndScoreMove favors Black pieces
											score = isRedReq ? -score : score;
											if (score > bestScore) { bestScore = score; bestMove = m; }
										}
										bestMove.type = Message.MessageType.HINT_RESPONSE;
										try { this.out.writeObject(bestMove); } catch (Exception e){}
									}
								}
							}
							break;

						// --- NEW: Route the Draw Offer ---
						case OFFER_DRAW:
							if (activeGames.containsKey(this.username)) {
								GameSession game = activeGames.get(this.username);
								if (!game.isAI) {
									ClientThread opp = (this == game.redPlayer) ? game.blackPlayer : game.redPlayer;
									try { opp.out.writeObject(data); } catch (Exception e) {}
								}
							}
							break;

						// --- NEW: End Game if Draw Accepted ---
						case DRAW_ACCEPTED:
							if (activeGames.containsKey(this.username)) {
								activeGames.get(this.username).handleGameOver("Draw! (Mutual Agreement)");
							}
							break;

						// --- NEW: Route the Draw Rejection ---
						case DRAW_REJECTED:
							if (activeGames.containsKey(this.username)) {
								GameSession game = activeGames.get(this.username);
								ClientThread opp = (this == game.redPlayer) ? game.blackPlayer : game.redPlayer;
								try { opp.out.writeObject(data); } catch (Exception e) {}
							}
							break;

						case CHAT:
							if (activeGames.containsKey(this.username)) {
								GameSession game = activeGames.get(this.username);
								if (!game.isAI) {
									ClientThread opp = (game.redPlayer == this) ? game.blackPlayer : game.redPlayer;
									this.out.writeObject(data);
									opp.out.writeObject(data);
								} else {
									this.out.writeObject(data);
								}
							}
							break;

						case REMATCH_REQUEST:
							if (activeGames.containsKey(this.username)) activeGames.get(this.username).handleRematchRequest(this);
							break;

						case QUIT:
							if (activeGames.containsKey(this.username)) {
								GameSession game = activeGames.get(this.username);
								if (game.isGameOver) {
									game.handleQuit(this);
								} else {
									String oppName = game.isAI ? "Computer (AI)" : ((game.redPlayer == this) ? game.blackPlayer.username : game.redPlayer.username);
									game.handleGameOver(oppName + " Wins! (Opponent Forfeit)");
									game.handleQuit(this);
								}
							}
							break;
					}
				} catch (Exception e) {
					if (!this.username.isEmpty()) {
						if (activeGames.containsKey(this.username)) {
							GameSession game = activeGames.get(this.username);
							String oppName = game.isAI ? "Computer (AI)" : ((game.redPlayer == this) ? game.blackPlayer.username : game.redPlayer.username);
							game.handleGameOver(oppName + " (Opponent Disconnected)");
							game.handleQuit(this);
						}
						clients.remove(this.username);
						broadcastClientList();
					}
					break;
				}
			}
		}
	}
}


	
	

	
