import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

public class LoginView {

    public final TextField ipInput;
    public final TextField usernameInput;
    public final Label loginStatus;
    public final Button connectBtn;
    public final Button guestBtn;

    public LoginView() {
        ipInput = new TextField("127.0.0.1");
        ipInput.setPromptText("Server IP Address");

        usernameInput = new TextField();
        usernameInput.setPromptText("Enter your username");

        loginStatus = new Label("Status: Disconnected");
        connectBtn = new Button("CONNECT TO SERVER");
        guestBtn = new Button("PLAY AS GUEST");
    }

    public Scene createScene() {
        StackPane root = new StackPane();
        root.getStyleClass().add("login-root");

        VBox boardStrip = new VBox();
        boardStrip.getStyleClass().add("board-strip");
        boardStrip.setPrefWidth(220);
        boardStrip.setMaxHeight(Double.MAX_VALUE);
        StackPane.setAlignment(boardStrip, Pos.CENTER_LEFT);

        VBox card = new VBox(14);
        card.getStyleClass().add("login-card");
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(500);
        card.setPadding(new Insets(30, 38, 30, 38));

        HBox pieceRow = new HBox(16);
        pieceRow.setAlignment(Pos.CENTER);

        Circle redPiece = new Circle(11);
        redPiece.getStyleClass().add("red-piece");

        Circle blackPiece = new Circle(11);
        blackPiece.getStyleClass().add("black-piece");

        pieceRow.getChildren().addAll(redPiece, blackPiece);

        Label title = new Label("CHECKERS");
        title.getStyleClass().add("login-title");
        addTitleAnimation(title);

        Label subtitle = new Label("EVERY MOVE MATTERS");
        subtitle.getStyleClass().add("login-subtitle");
        subtitle.setTextAlignment(TextAlignment.CENTER);

        VBox ipBox = createFieldBox("🌐", "Server IP Address", ipInput);
        ipBox.setPadding(new Insets(5, 0, 0, 0));

        VBox userBox = createFieldBox("👤", "Username", usernameInput);

        connectBtn.getStyleClass().addAll("game-btn", "primary-btn");
        connectBtn.setMaxWidth(Double.MAX_VALUE);
        connectBtn.setPrefHeight(50);

        guestBtn.getStyleClass().addAll("game-btn", "secondary-btn");
        guestBtn.setMaxWidth(Double.MAX_VALUE);
        guestBtn.setPrefHeight(46);

        loginStatus.getStyleClass().add("status-label");

        card.getChildren().addAll(
                pieceRow,
                title,
                subtitle,
                ipBox,
                userBox,
                connectBtn,
                guestBtn,
                loginStatus
        );

        root.getChildren().addAll(boardStrip, card);

        Scene scene = new Scene(root, 900, 620);
        scene.getStylesheets().add(getClass().getResource("/login.css").toExternalForm());
        return scene;
    }

    private VBox createFieldBox(String iconText, String labelText, TextField field) {
        VBox box = new VBox(6);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);

        HBox labelRow = new HBox(8);
        labelRow.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label(iconText);
        icon.getStyleClass().add("field-icon");

        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");

        labelRow.getChildren().addAll(icon, label);

        field.getStyleClass().add("game-field");
        field.setPrefHeight(44);
        field.setMaxWidth(Double.MAX_VALUE);

        box.getChildren().addAll(labelRow, field);
        return box;
    }

    private void addTitleAnimation(Label title) {
        DropShadow glow = new DropShadow();
        glow.setRadius(14);
        glow.setSpread(0.20);
        glow.setOffsetX(0);
        glow.setOffsetY(0);
        glow.setColor(Color.web("#ffb347"));
        title.setEffect(glow);

        Timeline glowTimeline = new Timeline(
                new KeyFrame(Duration.seconds(0.0), e -> glow.setColor(Color.web("#ffb347"))),
                new KeyFrame(Duration.seconds(1.0), e -> glow.setColor(Color.web("#ff8c00"))),
                new KeyFrame(Duration.seconds(2.0), e -> glow.setColor(Color.web("#ffd27f")))
        );
        glowTimeline.setCycleCount(Timeline.INDEFINITE);
        glowTimeline.setAutoReverse(true);
        glowTimeline.play();

        ScaleTransition pulse = new ScaleTransition(Duration.seconds(1.2), title);
        pulse.setFromX(1.0);
        pulse.setToX(1.05);
        pulse.setFromY(1.0);
        pulse.setToY(1.05);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Timeline.INDEFINITE);
        pulse.play();
    }
}