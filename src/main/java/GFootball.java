import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.extra.tools.PacketInfoSupport;
import gearth.extensions.parsers.*;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;

@ExtensionInfo(
        Title = "GFootball",
        Description = "I want to be better than Ahmed",
        Version = "Help me",
        Author = "Julianty"
)

// This library was used: https://github.com/kwhat/jnativehook
public class GFootball extends ExtensionForm implements NativeKeyListener{

    public TextField textBallID;
    public RadioButton radioButtonShoot, radioButtonTrap, radioButtonDribble,
            radioButtonDoubleClick, radioButtonWalk, radioButtonRun;
    private PacketInfoSupport packetInfoSupport;
    public CheckBox checkBall, checkDisableDouble, checkClickThrough, checkGuideTile;
    public Text textName, textIndex, textYourCoords, textBallCoords;
    public String YourName;
    public int CurrentX, CurrentY, BallX, BallY;
    public int YourIndex = -1;


    public static void main(String[] args) {
        runExtensionForm(args, GFootball.class);
    }


    @Override
    public ExtensionForm launchForm(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("GFootball.fxml"));
        Parent root = loader.load();

        primaryStage.setTitle("GFootball");
        primaryStage.setScene(new Scene(root));
        primaryStage.setResizable(false);
        primaryStage.setAlwaysOnTop(true);

        return loader.getController();
    }

    @Override
    protected void initExtension() {
        packetInfoSupport = new PacketInfoSupport(this);

        primaryStage.setOnShowing(event -> {
            try {
                GlobalScreen.registerNativeHook(); // Hook enabled
            }
            catch (NativeHookException ex) {
                System.err.println("There was a problem registering the native hook.");
                System.err.println(ex.getMessage());

                System.exit(1);
            }
            GlobalScreen.addNativeKeyListener(this);
        });

        primaryStage.setOnCloseRequest(event -> {
            try {
                GlobalScreen.unregisterNativeHook(); // Hook disabled
            } catch (NativeHookException nativeHookException) {
                nativeHookException.printStackTrace();
            }
        });

        sendToServer(new HPacket(96));

        intercept(HMessage.Direction.TOCLIENT, 2360, hMessage -> {
            // Gets Name and ID in order.
            int YourID = hMessage.getPacket().readInteger();
            YourName = hMessage.getPacket().readString();
            textName.setText("Your Name: " + YourName);
        });

        packetInfoSupport.intercept(HMessage.Direction.TOSERVER, "StartTyping", hMessage -> {
            if(primaryStage.isShowing()){ // Enabled when window is open
                hMessage.setBlocked(true);
            }
        });

        // Intercept this packet when you enter or restart a room
        packetInfoSupport.intercept(HMessage.Direction.TOCLIENT, "Users", hMessage -> {
            try {
                HPacket hPacket = hMessage.getPacket();
                HEntity[] roomUsersList = HEntity.parse(hPacket);
                for (HEntity hEntity: roomUsersList){
                    if(YourName.equals(hEntity.getName())){
                        YourIndex = hEntity.getIndex();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if(checkClickThrough.isSelected()){
                packetInfoSupport.sendToClient("YouArePlayingGame", true);
            }
        });

        packetInfoSupport.intercept(HMessage.Direction.TOCLIENT, "UserUpdate", hMessage -> {
            HPacket hPacket = hMessage.getPacket();
            // The HEntityUpdate class allows obtain the index of the user who is walking and other things
            for (HEntityUpdate hEntityUpdate: HEntityUpdate.parse(hPacket)){
                try {
                    int CurrentIndex = hEntityUpdate.getIndex();
                    if(CurrentIndex == YourIndex){
                        textIndex.setText("Your Index: " + CurrentIndex);
                        if(radioButtonRun.isSelected()){
                            CurrentX = hEntityUpdate.getMovingTo().getX();  CurrentY = hEntityUpdate.getMovingTo().getY();
                        }
                        if(radioButtonWalk.isSelected()){
                            CurrentX = hEntityUpdate.getTile().getX();  CurrentY = hEntityUpdate.getTile().getY();
                        }
                        textYourCoords.setText("Your Coords: (" + CurrentX + ", " + CurrentY + ")");
                    }
                }
                catch (NullPointerException nullPointerException) {
                    // getMovingTo() throws a NullPointerException error
                }
            }
        });

        packetInfoSupport.intercept(HMessage.Direction.TOCLIENT, "ObjectUpdate", hMessage -> {
            try {
                int FurniID = hMessage.getPacket().readInteger();
                if(FurniID == Integer.parseInt(textBallID.getText())){
                    int UniqueID = hMessage.getPacket().readInteger();
                    BallX = hMessage.getPacket().readInteger();
                    BallY = hMessage.getPacket().readInteger();
                    textBallCoords.setText("Ball Coords: (" + BallX + ", " + BallY + ")");
                }
            }
            catch (Exception ignored){ }
        });

        // Hash/Name : UseFurniture
        intercept(HMessage.Direction.TOSERVER, 3782, hMessage -> {
            if(checkDisableDouble.isSelected()){
                hMessage.setBlocked(true);
            }
            else if(checkBall.isSelected() && !checkDisableDouble.isSelected()){
                int BallID = hMessage.getPacket().readInteger();
                textBallID.setText(String.valueOf(BallID));
                checkBall.setSelected(false);
            }
        });
    }

    public void handleClickThrough(ActionEvent actionEvent) {
        if(checkClickThrough.isSelected()){
            packetInfoSupport.sendToClient("YouArePlayingGame", true);
        }
        else{
            packetInfoSupport.sendToClient("YouArePlayingGame", false);
        }
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent nativeKeyEvent) { }

    // I dont want to type in the chat when i press a key, i need to solve that...
    @Override
    public void nativeKeyPressed(NativeKeyEvent nativeKeyEvent) {
        if(nativeKeyEvent.getKeyCode() == 2){ // Key 1
            radioButtonShoot.setSelected(true);
            packetInfoSupport.sendToClient("ObjectUpdate", 1, 8237, BallX, BallY, 0, "0.0", "1.0",
                    0, 0, 1, 822083583, 2, YourName);
            packetInfoSupport.sendToServer("MoveAvatar", BallX, BallY);
        }
        if(nativeKeyEvent.getKeyCode() == 3){ // Key 2
            radioButtonTrap.setSelected(true);
            // En habbo futbol "Trap" significa pisar, el usuario caminara una casilla al frente del balon
            // Example -> Ball coords (8, 5)
            // Usuario arriba (8, 4)
            if (BallX == CurrentX && BallY > CurrentY)
            {
                packetInfoSupport.sendToServer("MoveAvatar", BallX, BallY + 1);
                packetInfoSupport.sendToClient("ObjectUpdate", 1, 8237, BallX, BallY + 1,
                        0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName);
            }
            // Usuario abajo (8, 6)
            if (BallX == CurrentX && BallY < CurrentY)
            {
                packetInfoSupport.sendToServer("MoveAvatar", BallX, BallY - 1);
                packetInfoSupport.sendToClient("ObjectUpdate", 1, 8237, BallX, BallY - 1,
                        0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName);
            }
            // Usuario izquierda (7, 5)
            if (BallX > CurrentX && BallY == CurrentY)
            {
                packetInfoSupport.sendToServer("MoveAvatar", BallX + 1, BallY);
                packetInfoSupport.sendToClient("ObjectUpdate", 1, 8237, BallX + 1, BallY,
                        0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName);
            }
            // Usuario derecha (9, 5)
            if (BallX < CurrentX && BallY == CurrentY)
            {
                packetInfoSupport.sendToServer("MoveAvatar", BallX - 1, BallY);
                packetInfoSupport.sendToClient("ObjectUpdate", 1, 8237, BallX - 1, BallY,
                        0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName);
            }
            // Example -> Ball coords (8, 5)

            // Usuario diagonal superior izquierda (7, 4)
            if (BallX > CurrentX && BallY > CurrentY)
            {
                packetInfoSupport.sendToServer("MoveAvatar", BallX + 1, BallY + 1);
                packetInfoSupport.sendToClient("ObjectUpdate", 1, 8237, BallX + 1, BallY+ 1,
                        0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName);
            }
            // Usuario diagonal superior derecha (9, 4)
            if (BallX < CurrentX && BallY > CurrentY)
            {
                packetInfoSupport.sendToServer("MoveAvatar", BallX - 1, BallY + 1);
                packetInfoSupport.sendToClient("ObjectUpdate", 1, 8237, BallX - 1, BallY + 1,
                        0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName);
            }
            // Usuario diagonal inferior izquierda (7, 6)
            if (BallX > CurrentX && BallY < CurrentY)
            {
                packetInfoSupport.sendToServer("MoveAvatar", BallX + 1, BallY - 1);
                packetInfoSupport.sendToClient("ObjectUpdate", 1, 8237, BallX + 1, BallY - 1,
                        0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName);
            }
            // Usuario diagonal inferior derecha (9, 6)
            if (BallX < CurrentX && BallY < CurrentY)
            {
                packetInfoSupport.sendToServer("MoveAvatar", BallX - 1, BallY - 1);
                packetInfoSupport.sendToClient("ObjectUpdate", 1, 8237, BallX - 1, BallY - 1,
                        0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName);
            }
        }
        if(nativeKeyEvent.getKeyCode() == 4){ // Key 3
            radioButtonDribble.setSelected(true);
            // En habbo futbol "Dribble" significa caminar, el usuario caminara dos casillas al frente del balon
            // Example -> Ball coords (8, 5)
            // Usuario arriba (8, 4)
            if (BallX == CurrentX && BallY > CurrentY)
            {
                packetInfoSupport.sendToServer("MoveAvatar", BallX, BallY + 2);
                packetInfoSupport.sendToClient("ObjectUpdate", 1, 8237, BallX, BallY + 2,
                        0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName);
            }
            // Usuario abajo (8, 6)
            if (BallX == CurrentX && BallY < CurrentY)
            {
                packetInfoSupport.sendToServer("MoveAvatar", BallX, BallY - 2);
                packetInfoSupport.sendToClient("ObjectUpdate", 1, 8237, BallX, BallY - 2,
                        0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName);
            }
            // Usuario izquierda (7, 5)
            if (BallX > CurrentX && BallY == CurrentY)
            {
                packetInfoSupport.sendToServer("MoveAvatar", BallX + 2, BallY);
                packetInfoSupport.sendToClient("ObjectUpdate", 1, 8237, BallX + 2, BallY,
                        0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName);
            }
            // Usuario derecha (9, 5)
            if (BallX < CurrentX && BallY == CurrentY)
            {
                packetInfoSupport.sendToServer("MoveAvatar", BallX - 2, BallY);
                packetInfoSupport.sendToClient("ObjectUpdate", 1, 8237, BallX - 2, BallY,
                        0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName);
            }
            // Example -> Ball coords (8, 5)

            // Usuario diagonal superior izquierda (7, 4)
            if (BallX > CurrentX && BallY > CurrentY)
            {
                packetInfoSupport.sendToServer("MoveAvatar", BallX + 2, BallY + 2);
                packetInfoSupport.sendToClient("ObjectUpdate", 1, 8237, BallX + 2, BallY+ 2,
                        0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName);
            }
            // Usuario diagonal superior derecha (9, 4)
            if (BallX < CurrentX && BallY > CurrentY)
            {
                packetInfoSupport.sendToServer("MoveAvatar", BallX - 2, BallY + 2);
                packetInfoSupport.sendToClient("ObjectUpdate", 1, 8237, BallX - 2, BallY + 2,
                        0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName);
            }
            // Usuario diagonal inferior izquierda (7, 6)
            if (BallX > CurrentX && BallY < CurrentY)
            {
                packetInfoSupport.sendToServer("MoveAvatar", BallX + 2, BallY - 2);
                packetInfoSupport.sendToClient("ObjectUpdate", 1, 8237, BallX + 2, BallY - 2,
                        0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName);
            }
            // Usuario diagonal inferior derecha (9, 6)
            if (BallX < CurrentX && BallY < CurrentY)
            {
                packetInfoSupport.sendToServer("MoveAvatar", BallX - 2, BallY - 2);
                packetInfoSupport.sendToClient("ObjectUpdate", 1, 8237, BallX - 2, BallY - 2,
                        0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName);
            }
        }
        if(nativeKeyEvent.getKeyCode() == 5){ // Key 4
            radioButtonDoubleClick.setSelected(true);
            packetInfoSupport.sendToServer("UseFurniture", Integer.parseInt(textBallID.getText()), 0);
            packetInfoSupport.sendToClient("ObjectUpdate", 1, 8237, BallX, BallY, 0, "0.0", "1.0",
                    0, 0, 1, 822083583, 2, YourName);
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent nativeKeyEvent) { }

    public void handleGuideTile(ActionEvent actionEvent) {
        if(checkGuideTile.isSelected()){
            packetInfoSupport.sendToClient("ObjectAdd", 1, 5399, BallX, BallY, 0, "0.0"/*"3.5"*/,
                    "0.0", 0, 0, "0", 1, 8636337, 4, YourName);
        }
        else {
            packetInfoSupport.sendToClient("ObjectRemove", "1", false, 8636337, 0);
        }
    }
}

/* Ventana de confirmacion de dialogo puede ser util

Platform.runLater(() -> {
                Alert alert = ConfirmationDialog.createAlertWithOptOut(Alert.AlertType.WARNING, connectExtensionKey
                        ,"Confirmation Dialog", null,
                        "Extension \""+extension.getTitle()+"\" tries to connect but isn't known to G-Earth, accept this connection?", "Remember my choice",
                        ButtonType.YES, ButtonType.NO
                );

                if (!(alert.showAndWait().filter(t -> t == ButtonType.YES).isPresent())) {
                    allowConnection[0] = false;
                }
                done[0] = true;
                if (!ConfirmationDialog.showDialog(connectExtensionKey)) {
                    rememberOption = allowConnection[0];
                }
            });
 */
