import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.function.Consumer;

// Handles the network to the server
public class Client extends Thread {

	Socket socketClient;
	ObjectOutputStream out;
	ObjectInputStream in;

	private Consumer<Message> callback;
	private String serverIp;
	private int serverPort;

	Client(String serverIp, int serverPort, Consumer<Message> call) {
		this.serverIp = serverIp;
		this.serverPort = serverPort;
		this.callback = call;
	}

	@Override
	public void run() {
		try {
			socketClient = new Socket(serverIp, serverPort);
			out = new ObjectOutputStream(socketClient.getOutputStream());
			in = new ObjectInputStream(socketClient.getInputStream());
			socketClient.setTcpNoDelay(true);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		while (true) {
			try {
				Message message = (Message) in.readObject();
				callback.accept(message);
			} catch (Exception e) {
				e.printStackTrace();
				break;
			}
		}
	}

	public void send(Message data) {
		try {
			if (out == null) return;
			out.writeObject(data);
			out.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}