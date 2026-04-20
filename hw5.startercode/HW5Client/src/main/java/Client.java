import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.util.function.Consumer;


// Handles the network to the server
public class Client extends Thread{

	// Making a socket for client
	Socket socketClient;

	// Initializing input-output streams
	ObjectOutputStream out;
	ObjectInputStream in;
	
	private Consumer<Message> callback;
	
	Client(Consumer<Message> call){
	
		callback = call;
	}

	// The main class to establish connection and execute the thread.
	public void run() {
		
		try {
		socketClient= new Socket("127.0.0.1",5555);
	    out = new ObjectOutputStream(socketClient.getOutputStream());
	    in = new ObjectInputStream(socketClient.getInputStream());
	    socketClient.setTcpNoDelay(true);
		}
		catch(Exception e) {
			e.printStackTrace();
		}

		// While loop to constantly receive the data
		while(true) {
			 
			try {
			Message message = (Message) in.readObject();
			callback.accept(message);
			}
			catch(Exception e) {
				e.printStackTrace();
				break;
			}
		}
	
    }

	// Sends a message to the server
	public void send(Message data) {
		try {
			out.writeObject(data);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


}
