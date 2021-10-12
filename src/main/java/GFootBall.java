import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.parsers.*;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;

@ExtensionInfo(
        Title = "GFootBall",
        Description = "I want to be better than Ahmed",
        Version = "Help me",
        Author = "Julianty"
)

// This library was used: https://github.com/kwhat/jnativehook
public class GFootBall extends ExtensionForm implements NativeKeyListener{

    public TextField textBallID;
    public RadioButton radioButtonShoot, radioButtonTrap, radioButtonDribble,
            radioButtonDoubleClick, radioButtonWalk, radioButtonRun;
    public CheckBox checkBall, checkDisableDouble, checkClickThrough, checkGuideTile;
    public Text textName, textIndex, textYourCoords, textBallCoords;
    public String YourName;
    public int CurrentX, CurrentY, BallX, BallY;
    public int YourIndex = -1;

    @Override
    protected void onShow() {
        sendToServer(new HPacket("InfoRetrieve", HMessage.Direction.TOSERVER));
        try {
            GlobalScreen.registerNativeHook();
            System.out.println("Hook enabled");
        }
        catch (NativeHookException ex) {
            System.err.println("There was a problem registering the native hook.");
            System.err.println(ex.getMessage());

            System.exit(1);
        }
        GlobalScreen.addNativeKeyListener(this);
    }

    @Override
    protected void onHide() {
        try {
            GlobalScreen.unregisterNativeHook();
            System.out.println("Hook disabled");
        } catch (NativeHookException nativeHookException) {
            nativeHookException.printStackTrace();
        }
    }

    @Override
    protected void initExtension() {

        intercept(HMessage.Direction.TOCLIENT, "UserObject", hMessage -> {
            // Gets ID and Name in order.
            int YourID = hMessage.getPacket().readInteger();    YourName = hMessage.getPacket().readString();
            textName.setText("Your Name: " + YourName);
        });

        intercept(HMessage.Direction.TOSERVER, "StartTyping", hMessage -> {
            if(primaryStage.isShowing()){ // Enabled when window is open
                hMessage.setBlocked(true);
            }
        });

        // Intercept this packet when you enter or restart a room
        intercept(HMessage.Direction.TOCLIENT, "Users", hMessage -> {
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
                sendToClient(new HPacket("YouArePlayingGame", HMessage.Direction.TOCLIENT, true));
            }
        });

        intercept(HMessage.Direction.TOCLIENT, "UserUpdate", hMessage -> {
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
                catch (NullPointerException nullPointerException) {/*getMovingTo() throws a NullPointerException error*/}
            }
        });

        // when you kick the ball
        intercept(HMessage.Direction.TOCLIENT, "ObjectUpdate", hMessage -> {
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

        /* When you move ball with admin rights or idk ignore
        intercept(HMessage.Direction.TOCLIENT, 3776, hMessage -> {
            try {
                int FurniID = hMessage.getPacket().readInteger();
                int UniqueID = hMessage.getPacket().readInteger();
                int X = hMessage.getPacket().readInteger();
                int Y = hMessage.getPacket().readInteger();
                if(FurniID == Integer.parseInt(textBallID.getText())){
                    BallX = X; BallY = Y;
                    textBallCoords.setText("Ball Coords: (" + BallX + ", " + BallY + ")");
                }
            }
            catch (Exception ignored){ }
        });*/

        intercept(HMessage.Direction.TOSERVER, "UseFurniture", hMessage -> {
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

    public void kickBall(int PlusX, int PlusY){
        sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, BallX + PlusX, BallY + PlusY));

        // Moves the tile in the client-side
        sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                1, 8237, BallX + PlusX, BallY + PlusY, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
    }

    public void handleClickThrough(ActionEvent actionEvent) {
        if(checkClickThrough.isSelected()){
            sendToServer(new HPacket("InfoRetrieve", HMessage.Direction.TOSERVER));

            sendToClient(new HPacket("YouArePlayingGame", HMessage.Direction.TOCLIENT, true));
        }
        else{
            sendToClient(new HPacket("YouArePlayingGame", HMessage.Direction.TOCLIENT, false));
        }
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent nativeKeyEvent) { }

    // I dont want to type in the chat when i press a key, i need to solve that...
    @Override
    public void nativeKeyPressed(NativeKeyEvent nativeKeyEvent) {
        if(nativeKeyEvent.getKeyCode() == 2){ // Key 1
            radioButtonShoot.setSelected(true);
            System.out.println("entro aca1");
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    1, 8237, BallX, BallY, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
            sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, BallX, BallY));
        }
        if(nativeKeyEvent.getKeyCode() == 3){ // Key 2
            radioButtonTrap.setSelected(true);
            // En habbo futbol "Trap" significa pisar, el usuario caminara una casilla al frente del balon
            // Example -> Ball coords (8, 5)
            // Usuario arriba (8, 4)
            if (BallX == CurrentX && BallY > CurrentY)
            {
                /* Example code in the method 'kickBall()'
                sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, BallX, BallY + 1));*/
                kickBall(0, 1);
            }
            // Usuario abajo (8, 6)
            if (BallX == CurrentX && BallY < CurrentY)
            {
                kickBall(0, -1);
            }
            // Usuario izquierda (7, 5)
            if (BallX > CurrentX && BallY == CurrentY)
            {
                kickBall(1, 0);
            }
            // Usuario derecha (9, 5)
            if (BallX < CurrentX && BallY == CurrentY)
            {
                kickBall(-1, 0);
                /*packetInfoSupport.sendToServer("MoveAvatar", BallX - 1, BallY);
                packetInfoSupport.sendToClient("ObjectUpdate", 1, 8237, BallX - 1, BallY,
                        0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName);*/
            }
            // Example -> Ball coords (8, 5)

            // Usuario diagonal superior izquierda (7, 4)
            if (BallX > CurrentX && BallY > CurrentY)
            {
                kickBall(1, 1);
            }
            // Usuario diagonal superior derecha (9, 4)
            if (BallX < CurrentX && BallY > CurrentY)
            {
                kickBall(-1, 1);
            }
            // Usuario diagonal inferior izquierda (7, 6)
            if (BallX > CurrentX && BallY < CurrentY)
            {
                kickBall(1, -1);
            }
            // Usuario diagonal inferior derecha (9, 6)
            if (BallX < CurrentX && BallY < CurrentY)
            {
                kickBall(-1 , -1);
            }
        }
        if(nativeKeyEvent.getKeyCode() == 4){ // Key 3
            radioButtonDribble.setSelected(true);
            // En habbo futbol "Dribble" significa caminar, el usuario caminara dos casillas al frente del balon
            // Example -> Ball coords (8, 5)
            // Usuario arriba (8, 4)
            if (BallX == CurrentX && BallY > CurrentY)
            {
                kickBall(0 ,2);
            }
            // Usuario abajo (8, 6)
            if (BallX == CurrentX && BallY < CurrentY)
            {
                kickBall(0, -2);
            }
            // Usuario izquierda (7, 5)
            if (BallX > CurrentX && BallY == CurrentY)
            {
                kickBall(2, 0);
            }
            // Usuario derecha (9, 5)
            if (BallX < CurrentX && BallY == CurrentY)
            {
                kickBall(-2 , 0);
            }
            // Example -> Ball coords (8, 5)

            // Usuario diagonal superior izquierda (7, 4)
            if (BallX > CurrentX && BallY > CurrentY)
            {
                kickBall(2, 2);
            }
            // Usuario diagonal superior derecha (9, 4)
            if (BallX < CurrentX && BallY > CurrentY)
            {
                kickBall(-2 , 2);
            }
            // Usuario diagonal inferior izquierda (7, 6)
            if (BallX > CurrentX && BallY < CurrentY)
            {
                kickBall(2, -2);
            }
            // Usuario diagonal inferior derecha (9, 6)
            if (BallX < CurrentX && BallY < CurrentY)
            {
                kickBall(-2 , -2);
            }
        }
        if(nativeKeyEvent.getKeyCode() == 5){ // Key 4
            radioButtonDoubleClick.setSelected(true);
            sendToServer(new HPacket("UseFurniture", HMessage.Direction.TOSERVER,
                    Integer.parseInt(textBallID.getText()), 0));
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT, 1, 8237, BallX, BallY,
                    0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent nativeKeyEvent) { }

    public void handleGuideTile(ActionEvent actionEvent) {
        if(checkGuideTile.isSelected()){
            sendToClient(new HPacket("ObjectAdd", HMessage.Direction.TOCLIENT, 1, 5399, 0
                    , 0, 0, "0.0" /*"3.5"*/, "0.2", 0, 0, "1", -1, 1, 2, YourName));
        }
        else {
            sendToClient(new HPacket("ObjectRemove", HMessage.Direction.TOCLIENT, "1", false, 8636337, 0));
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
