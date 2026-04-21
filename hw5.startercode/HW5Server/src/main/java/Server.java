import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.Queue;
import java.util.LinkedList;

import javafx.application.Platform;
import javafx.scene.control.ListView;

// Main class for server
// Creates thread for each connected client and manages them simultaneously
public class Server{

	int count = 1; // Tracks the total number of connection attempts
	ConcurrentHashMap<String, ClientThread> clients = new ConcurrentHashMap<>();
	ConcurrentHashMap<String, ArrayList<String>> groups = new ConcurrentHashMap<>();
	Queue<ClientThread> waitingPlayers = new LinkedList<>();
	TheServer server;
	private Consumer<Serializable> callback;
	
	
	Server(Consumer<Serializable> call){
	
		callback = call;
		server = new TheServer();
		server.start();
	}
	
	
	public class TheServer extends Thread{

		public void run() {
			try (ServerSocket mysocket = new ServerSocket(5555)) {
				callback.accept("Server is waiting for clients on port 5555!");

				// Loop to constantly accept new connections
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
	
		// Manages connection with specific clients
		class ClientThread extends Thread{
			
		
			Socket connection;
			int count;
			ObjectInputStream in;
			ObjectOutputStream out;
			String username = "";
			
			ClientThread(Socket s, int count){
				this.connection = s;
				this.count = count;	
			}

			// Manages the current list of active clients and broadcasts it
			public void broadcastClientList() {
				Message updateMsg = new Message();
				updateMsg.type = Message.MessageType.CLIENT_LIST;
				updateMsg.activeUsers = new ArrayList<>(clients.keySet());
				updateMsg.activeGroups = new ArrayList<>(groups.keySet());

				// Iterate through every active client
				for (ClientThread t : clients.values()) {
					try {
						t.out.writeObject(updateMsg);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			
			public void run(){

				try {
					out = new ObjectOutputStream(connection.getOutputStream());
					in = new ObjectInputStream(connection.getInputStream());
					connection.setTcpNoDelay(true);
				} catch (Exception e) {
					callback.accept("Streams not open for client #" + count);
					return;
				}

				// Loop to continuously read messages from client
				while (true) {
					try {
						Message data = (Message) in.readObject();

						// Take action based on the message received
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

									// If 2 players are waiting, match them
									if (waitingPlayers.size() >= 2) {
										ClientThread p1 = waitingPlayers.poll();
										ClientThread p2 = waitingPlayers.poll();

										Message matchMsg1 = new Message();
										matchMsg1.type = Message.MessageType.MATCH_FOUND;
										matchMsg1.content = "Match found against " + p2.username;
										p1.out.writeObject(matchMsg1);

										Message matchMsg2 = new Message();
										matchMsg2.type = Message.MessageType.MATCH_FOUND;
										matchMsg2.content = "Match found against " + p1.username;
										p2.out.writeObject(matchMsg2);

										callback.accept("Match created: " + p1.username + " vs " + p2.username);
									}
								}
								break;

							case BROADCAST:
								callback.accept("BROADCAST from " + data.sender + ": " + data.content);
								for (ClientThread t : clients.values()) {
									t.out.writeObject(data);
								}
								break;

							case PRIVATE:
								callback.accept("PRIVATE " + data.sender + " -> " + data.recipient + ": " + data.content);
								if (clients.containsKey(data.recipient)) {
									clients.get(data.recipient).out.writeObject(data);
									// Send copy back to sender so they see their own message
									if (!data.sender.equals(data.recipient)) {
										this.out.writeObject(data);
									}
								}
								break;

							case CREATE_GROUP:
								if (!groups.containsKey(data.recipient)) {
									groups.put(data.recipient, new ArrayList<>());
									groups.get(data.recipient).add(this.username);
									callback.accept(this.username + " created group: " + data.recipient);
									broadcastClientList();
								}
								break;

							case GROUP_MESSAGE:
								callback.accept("GROUP [" + data.recipient + "] " + data.sender + ": " + data.content);
								if (groups.containsKey(data.recipient)) {
									// Add sender to group if they aren't already in it
									if (!groups.get(data.recipient).contains(this.username)) {
										groups.get(data.recipient).add(this.username);
									}
									// Broadcast to group members
									for (String member : groups.get(data.recipient)) {
										if (clients.containsKey(member)) {
											clients.get(member).out.writeObject(data);
										}
									}
								}
								break;
						}
					} catch (Exception e) { // Exception for when the client disconnects or crashes
						if (!this.username.isEmpty()) {
							callback.accept(this.username + " has left the server.");
							clients.remove(this.username);
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


	
	

	
