
import java.util.HashMap;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

// Main class to create visual interface for server
public class GuiServer extends Application{
	Server serverConnection; // The thread that handles all the socket communication
	ListView<String> listItems;
	
	
	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		listItems = new ListView<>();

		// Start the server and pass incoming log strings to the UI
		serverConnection = new Server(data -> {
			Platform.runLater(() -> {
				listItems.getItems().add(data.toString());
			});
		});

		Scene serverScene = createServerGui();

		primaryStage.setOnCloseRequest(t -> {
			Platform.exit();
			System.exit(0);
		});

		primaryStage.setScene(serverScene);
		primaryStage.setTitle("Server Log");
		primaryStage.show();
	}

	// Class to create different Gui elements for the server
	public Scene createServerGui() {
		BorderPane pane = new BorderPane();
		pane.setPadding(new Insets(70));

		pane.setStyle("-fx-background-color: red; -fx-font-family: 'serif';");

		pane.setCenter(listItems);
		return new Scene(pane, 500, 400);
	}


}
