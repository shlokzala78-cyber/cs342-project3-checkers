import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

// Main class for server
// Creates a thread for each connected client and manages them simultaneously
public class Server {

	int count = 1; // Tracks total number of connection attempts
	ConcurrentHashMap<String, ClientThread> clients = new ConcurrentHashMap<>();
	Queue<ClientThread> waitingPlayers = new LinkedList<>();
	ArrayList<GameSession> activeSessions = new ArrayList<>();

	TheServer server;
	private Consumer<Serializable> callback;

	Server(Consumer<Serializable> call) {
		callback = call;
		server = new TheServer();
		server.start();
	}

	class GameSession {
		ClientThread player1;   // Red
		ClientThread player2;   // Black
		String currentTurn;

		GameSession(ClientThread p1, ClientThread p2) {
			this.player1 = p1;
			this.player2 = p2;
			this.currentTurn = "Red";
		}

		ClientThread getOpponent(ClientThread player) {
			return (player == player1) ? player2 : player1;
		}

		String getPlayerColor(ClientThread player) {
			return (player == player1) ? "Red" : "Black";
		}

		void switchTurn() {
			currentTurn = currentTurn.equals("Red") ? "Black" : "Red";
		}
	}

	public class TheServer extends Thread {
		public void run() {
			try (ServerSocket mysocket = new ServerSocket(5555)) {
				callback.accept("Server is waiting for clients on port 5555!");

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

	// Handles one connected client
	class ClientThread extends Thread {

		Socket connection;
		int count;
		ObjectInputStream in;
		ObjectOutputStream out;
		String username = "";
		GameSession session = null;

		ClientThread(Socket s, int count) {
			this.connection = s;
			this.count = count;
		}

		// Broadcast current waiting room user list to all clients
		public void broadcastClientList() {
			Message updateMsg = new Message();
			updateMsg.type = Message.MessageType.CLIENT_LIST;
			updateMsg.activeUsers = new ArrayList<>(clients.keySet());

			for (ClientThread t : clients.values()) {
				try {
					t.out.writeObject(updateMsg);
				} catch (Exception e) {
					e.printStackTrace();
				}
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

								// Add player to waiting queue
								waitingPlayers.add(this);
								callback.accept(username + " added to waiting queue.");

								// Auto-match first two waiting players
								if (waitingPlayers.size() >= 2) {
									ClientThread p1 = waitingPlayers.poll();
									ClientThread p2 = waitingPlayers.poll();

									GameSession session = new GameSession(p1, p2);
									activeSessions.add(session);

									p1.session = session;
									p2.session = session;

									Message msg1 = new Message();
									msg1.type = Message.MessageType.GAME_START;
									msg1.sender = p2.username; // opponent name
									msg1.content = "Red,Red"; // player1 is Red
									p1.out.writeObject(msg1);

									Message msg2 = new Message();
									msg2.type = Message.MessageType.GAME_START;
									msg2.sender = p1.username; // opponent name
									msg2.content = "Black,Red"; // game starts with Red's turn
									p2.out.writeObject(msg2);

									callback.accept("Match created: " + p1.username + " vs " + p2.username);
									callback.accept("Active sessions: " + activeSessions.size());
								}
							}
							break;

						case CHAT:
							// Send chat only to the opponent in the same session
							if (session != null) {
								ClientThread opponent = (session.player1 == this) ? session.player2 : session.player1;
								opponent.out.writeObject(data);

								// Also send back to sender so they see their own message
								this.out.writeObject(data);

								callback.accept("CHAT " + data.sender + ": " + data.content);
							}
							break;

						case MOVE:
							if (session != null) {
								String playerColor = session.getPlayerColor(this);

								// Enforce turn
								if (!playerColor.equals(session.currentTurn)) {
									callback.accept("Rejected move from " + data.sender + " (not " + playerColor + "'s turn)");
									break;
								}

								ClientThread opponent = session.getOpponent(this);

								// For now, accept move without full validation
								session.switchTurn();

								// Send updated turn info back to both clients
								data.content = session.currentTurn + ",false";

								opponent.out.writeObject(data);
								this.out.writeObject(data);

								callback.accept("MOVE from " + data.sender +
										" (" + data.startRow + "," + data.startCol + ") -> (" +
										data.endRow + "," + data.endCol + "), next turn: " + session.currentTurn);
							}
							break;

						case CHALLENGE:
							// Placeholder for later if you want manual challenges
							callback.accept(data.sender + " challenged " + data.recipient);
							break;

						case OFFER_DRAW:
							if (session != null) {
								ClientThread opponent = (session.player1 == this) ? session.player2 : session.player1;
								opponent.out.writeObject(data);
								callback.accept(data.sender + " offered a draw.");
							}
							break;

						case QUIT:
							if (session != null) {
								ClientThread opponent = (session.player1 == this) ? session.player2 : session.player1;

								Message gameOverMsg = new Message();
								gameOverMsg.type = Message.MessageType.GAME_OVER;
								gameOverMsg.content = opponent.username + " wins (other player quit).";

								opponent.out.writeObject(gameOverMsg);
								this.out.writeObject(gameOverMsg);

								callback.accept(data.sender + " quit the game.");
							}
							break;

						case GAME_OVER:
							// Usually server should send this, not receive it
							break;
					}

				} catch (Exception e) {
					if (!this.username.isEmpty()) {
						callback.accept(this.username + " has left the server.");
						clients.remove(this.username);
						waitingPlayers.remove(this);
						broadcastClientList();
					} else {
						callback.accept("Unregistered client #" + count + " disconnected.");
					}
					break;
				}
			}
		}
	}
}