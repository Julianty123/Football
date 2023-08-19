import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.parsers.*;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.LogManager;


@ExtensionInfo(
        Title = "GFootBall",
        Description = "Known as Non DC Bot",
        Version = "1.1.1",
        Author = "Julianty"
)

// This library was used: https://github.com/kwhat/jnativehook
public class GFootBall extends ExtensionForm implements NativeKeyListener {
    public TextField txtBallId;
    public RadioButton radioButtonShoot, radioButtonTrap, radioButtonDribble,
            radioButtonDoubleClick, radioButtonMix, radioButtonWalk, radioButtonRun;
    public CheckBox checkUserName, checkBall, checkDisableDouble, checkClickThrough, checkGuideTile,
            checkHideBubble, checkGuideTrap, checkDiagoKiller;
    public Text textUserIndex, textUserCoords, textBallCoords;

    public String userName;
    public int currentX, currentY, ballX, ballY;
    public int clickX, clickY;
    public int userIndex = -1;

    public int userIdSelected = -1;

    HashMap<Integer,Integer> hashUserIdAndIndex = new HashMap<>();
    HashMap<Integer,String> hashUserIdAndName = new HashMap<>();

    Map<Integer, HPoint> userCoords = new HashMap<>();

    public boolean flagBallTrap = false, flagBallDribble = false, guideTrap = false;
    public TextField txtShoot, txtTrap, txtDribble, txtMix, txtUniqueId;

    public TextField txtUpperLeft, txtUpperRight, txtLowerLeft, txtLowerRight;


    public Label labelShoot; // Lo instancie para darle el foco

    /*
    [StartTyping]
Outgoing[2395] -> [0][0][0][2][9][91]
{out:StartTyping}
--------------------
[UserTyping]
Incoming[2969] -> [0][0][0][10][11][153][0][0][0][0][0][0][0][1]
{in:UserTyping}{i:0}{i:1}
	  userIndex  state: empieza a escribir
--------------------
[CancelTyping]
Outgoing[3575] -> [0][0][0][2][13]÷
{out:CancelTyping}
--------------------
[UserTyping]
Incoming[2969] -> [0][0][0][10][11][153][0][0][0][0][0][0][0][0]
{in:UserTyping}{i:0}{i:0}
	  userIndex  state: termina de escribir
--------------------
     */

    @Override
    protected void onShow() {
        sendToServer(new HPacket("{out:InfoRetrieve}")); // When its sent, gets UserObject packet
        sendToServer(new HPacket("{out:AvatarExpression}{i:0}")); // With this it's not necessary to restart the room
        sendToServer(new HPacket("{out:GetHeightMap}"));    // Get Flooritems, Wallitems, etc. Without restart room

        LogManager.getLogManager().reset(); // https://stackoverflow.com/questions/30560212/how-to-remove-the-logging-data-from-jnativehook-library
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
        userIndex = -1;
        sendToClient(new HPacket("ObjectRemove", HMessage.Direction.TOCLIENT, "1" /* "1" = id */, false, 8636337, 0));
        sendToClient(new HPacket("{in:ObjectRemove}{s:\"2\"}{b:false}{i:8636337}{i:0}"));

        Platform.runLater(()->{
            checkGuideTile.setSelected(false);
            checkGuideTrap.setSelected(false);
        });

        try {
            GlobalScreen.unregisterNativeHook();
            System.out.println("Hook disabled");
        } catch (NativeHookException nativeHookException) {
            nativeHookException.printStackTrace();
        }
    }

    @Override
    protected void initExtension() {
        // Response of packet InfoRetrieve
        intercept(HMessage.Direction.TOCLIENT, "UserObject", hMessage -> {
            // Gets ID and Name in order.
            int YourID = hMessage.getPacket().readInteger();    userName = hMessage.getPacket().readString();
            Platform.runLater(()-> checkUserName.setText("User Name: " + userName)); // TextField no necesita usar Platform.runLater(..)
        });

        // Response of packet AvatarExpression (gets userIndex)
        intercept(HMessage.Direction.TOCLIENT, "Expression", hMessage -> {
            // First integer is index in room, second is animation id, i think
            if(primaryStage.isShowing() && userIndex == -1){ // this could avoid any bug
                userIndex = hMessage.getPacket().readInteger();
                textUserIndex.setText("User Index: " + userIndex);  // GUI updated!
            }
        });

        // Intercepts when you start typing
        intercept(HMessage.Direction.TOSERVER, "StartTyping", hMessage -> {
            if(primaryStage.isShowing() && checkHideBubble.isSelected()){   // If the window is open and the control is checked, it will do that
                hMessage.setBlocked(true);
            }
        });

        intercept(HMessage.Direction.TOCLIENT, "RoomReady", hMessage -> {
            System.out.println("RoomReady");
            hashUserIdAndIndex.clear(); hashUserIdAndName.clear();
        });

        intercept(HMessage.Direction.TOSERVER, "GetSelectedBadges", hMessage -> {
            try {
                userIdSelected = hMessage.getPacket().readInteger();
                if(checkUserName.isSelected()){
                    userIndex = hashUserIdAndIndex.get(userIdSelected); userName = hashUserIdAndName.get(userIdSelected);
                    Platform.runLater(() -> {
                        textUserIndex.setText("User Index: " + userIndex);
                        checkUserName.setText("User Name: "+ userName);
                        checkUserName.setSelected(false);
                    });
                }
            }catch (NullPointerException ignored){}
        });

        // Intercepts this packet when you enter or any user arrive to the room
        intercept(HMessage.Direction.TOCLIENT, "Users", hMessage -> {
            try {
                HPacket hPacket = hMessage.getPacket();
                HEntity[] roomUsersList = HEntity.parse(hPacket);
                for (HEntity hEntity: roomUsersList){
                    // If the key already exists, it will be replaced
                    hashUserIdAndIndex.put(hEntity.getId(), hEntity.getIndex());
                    hashUserIdAndName.put(hEntity.getId(), hEntity.getName());
                }
            } catch (NullPointerException ignored) { }

            if(checkClickThrough.isSelected()) sendToClient(new HPacket("{in:YouArePlayingGame}{b:true}"));
        });

        // Intercepts when users walk in the room
        intercept(HMessage.Direction.TOCLIENT, "UserUpdate", hMessage -> {
            HPacket hPacket = hMessage.getPacket();
            // The HEntityUpdate class allows obtain the index of the user who is walking and other things
            for (HEntityUpdate hEntityUpdate: HEntityUpdate.parse(hPacket)){
                try {
                    int currentIndex = hEntityUpdate.getIndex();

                    // If the coordinate already exist, it will be updated
                    if(hEntityUpdate.getMovingTo() != null) userCoords.put(currentIndex, hEntityUpdate.getMovingTo());

                    if(currentIndex == userIndex){
                        textUserIndex.setText("User Index: " + currentIndex);

                        int JokerX = hEntityUpdate.getTile().getX(); int JokerY = hEntityUpdate.getTile().getY(); // Necesario para el modo de trap
                        if(checkGuideTrap.isSelected()){
                            if(JokerX == ballX && JokerY == ballY){
                                sendToClient(new HPacket("{in:Chat}{i:-1}{s:\"You are on the ball\"}{i:0}{i:30}{i:0}{i:0}"));
                                guideTrap = true;
                            }
                            else{
                                guideTrap = false; // Resuelve el problema de que se quede en el trap
                            }
                        }

                        if(radioButtonRun.isSelected()){
                            currentX = hEntityUpdate.getMovingTo().getX();  currentY = hEntityUpdate.getMovingTo().getY();
                        }
                        if(radioButtonWalk.isSelected()){
                            currentX = hEntityUpdate.getTile().getX();  currentY = hEntityUpdate.getTile().getY();
                        }
                        textUserCoords.setText("User Coords: (" + currentX + ", " + currentY + ")");

                        if(flagBallTrap){
                            if(ballX - 1 == currentX && ballY - 1 == currentY){
                                kickBall(1, 1); flagBallTrap = false;
                            }
                            if(ballX + 1 == currentX && ballY - 1 == currentY){
                                kickBall(-1, 1);    flagBallTrap =  false;
                            }
                            if(ballX - 1 == currentX && ballY + 1 == currentY){
                                kickBall(1, -1);    flagBallTrap = false;
                            }
                            if(ballX + 1 == currentX && ballY + 1 == currentY){
                                kickBall(-1 , -1);  flagBallTrap = false;
                            }
                        }
                        if(flagBallDribble){
                            if(ballX - 1 == currentX && ballY - 1 == currentY){
                                kickBall(2, 2); flagBallDribble = false;
                            }
                            if(ballX + 1 == currentX && ballY - 1 == currentY){
                                kickBall(-2, 2);    flagBallDribble =  false;
                            }
                            if(ballX - 1 == currentX && ballY + 1 == currentY){
                                kickBall(2, -2);    flagBallDribble = false;
                            }
                            if(ballX + 1 == currentX && ballY + 1 == currentY){
                                kickBall(-2, -2);   flagBallDribble = false;
                            }
                        }
                    }
                }
                catch (NullPointerException nullPointerException) {/*getMovingTo() throws a NullPointerException error*/}
            }
        });

        intercept(HMessage.Direction.TOSERVER, "MoveAvatar", hMessage -> {
            if(guideTrap){
                clickX = hMessage.getPacket().readInteger();    clickY = hMessage.getPacket().readInteger();
                Suggest(clickX, clickY);
                hMessage.setBlocked(true);
            }
        });

        // Intercepts when the users kick the soccer ball
        intercept(HMessage.Direction.TOCLIENT, "ObjectUpdate", hMessage -> {
            // {in:ObjectUpdate}{i:249715730}{i:3213}{i:10}{i:9}{i:1}{s:"1.0E-5"}{s:"1.0E-6"}{i:0}{i:0}{s:"44"}{i:-1}{i:0}{i:51157174}
            try {
                int furnitureId = hMessage.getPacket().readInteger();
                if(furnitureId == Integer.parseInt(txtBallId.getText())){
                    int UniqueId = hMessage.getPacket().readInteger();
                    ballX = hMessage.getPacket().readInteger(); ballY = hMessage.getPacket().readInteger();
                    int direction = hMessage.getPacket().readInteger(); String zTile = hMessage.getPacket().readString();
                    String idk = hMessage.getPacket().readString();
                    int idk1 = hMessage.getPacket().readInteger(); int idk2 = hMessage.getPacket().readInteger();
                    String furnitureState = hMessage.getPacket().readString();

                    Platform.runLater(()-> textBallCoords.setText("Ball Coords: (" + ballX + ", " + ballY + ")"));
                    if(checkDiagoKiller.isSelected()) tileInClient(zTile);
                }
            }
            catch (Exception ignored){ }
        });

        // When you move ball with admin rights, or something like that (Happens in some holos, Wtf?)
        intercept(HMessage.Direction.TOCLIENT, "SlideObjectBundle", hMessage -> {
            // {in:SlideObjectBundle}{i:4}{i:11}{i:5}{i:10}{i:1}{i:5451413}{s:"0.0"}{s:"0.0"}{i:5451413}
            try {
                int oldX = hMessage.getPacket().readInteger(); int oldY = hMessage.getPacket().readInteger();
                int newX = hMessage.getPacket().readInteger(); int newY = hMessage.getPacket().readInteger();
                int direction = hMessage.getPacket().readInteger(); int furnitureId = hMessage.getPacket().readInteger();
                String zTile = hMessage.getPacket().readString();
                if(furnitureId == Integer.parseInt(txtBallId.getText())){
                    ballX = newX; ballY = newY;
                    Platform.runLater(()-> textBallCoords.setText("Ball Coords: (" + ballX + ", " + ballY + ")"));
                    textBallCoords.setText("Ball Coords: (" + ballX + ", " + ballY + ")");
                    if(checkDiagoKiller.isSelected()) tileInClient(zTile);
                }
            }
            catch (Exception ignored){ }
        });

        // Intercepts when you give double click on a furniture
        intercept(HMessage.Direction.TOSERVER, "UseFurniture", hMessage -> {
            if(checkDisableDouble.isSelected()){ hMessage.setBlocked(true); }
            else if(checkBall.isSelected() && !checkDisableDouble.isSelected()){
                int BallID = hMessage.getPacket().readInteger();
                txtBallId.setText(String.valueOf(BallID));
                checkBall.setSelected(false);
            }
        });
    }

    private void tileInClient(String zTile) {
        sendToClient(new HPacket(String.format(
                "{in:ObjectUpdate}{i:3}{i:%s}{i:%d}{i:%d}{i:0}{s:\"%s\"}{s:\"0.0\"}{i:0}{i:0}{s:\"0\"}{i:-1}{i:1}{i:123}",
                txtUniqueId.getText(), ballX - 4, ballY + 4, zTile))); // Diago Izquierda Abajo

        sendToClient(new HPacket(String.format(
                "{in:ObjectUpdate}{i:4}{i:%s}{i:%d}{i:%d}{i:0}{s:\"%s\"}{s:\"0.0\"}{i:0}{i:0}{s:\"0\"}{i:-1}{i:1}{i:123}",
                txtUniqueId.getText(), ballX + 4, ballY + 4, zTile))); // Diago Derecha Abajo

        sendToClient(new HPacket(String.format(
                "{in:ObjectUpdate}{i:5}{i:%s}{i:%d}{i:%d}{i:0}{s:\"%s\"}{s:\"0.0\"}{i:0}{i:0}{s:\"0\"}{i:-1}{i:1}{i:123}",
                txtUniqueId.getText(), ballX - 4, ballY - 4, zTile))); // Diago Izquierda Arriba

        sendToClient(new HPacket(String.format(
                "{in:ObjectUpdate}{i:6}{i:%s}{i:%d}{i:%d}{i:0}{s:\"%s\"}{s:\"0.0\"}{i:0}{i:0}{s:\"0\"}{i:-1}{i:1}{i:123}",
                txtUniqueId.getText(), ballX + 4, ballY - 4, zTile))); // Diago Derecha Arriba

        sendToClient(new HPacket(String.format(
                "{in:ObjectUpdate}{i:7}{i:%s}{i:%d}{i:%d}{i:0}{s:\"%s\"}{s:\"0.0\"}{i:0}{i:0}{s:\"0\"}{i:-1}{i:1}{i:123}",
                txtUniqueId.getText(), ballX - 4, ballY, zTile))); // Izquierda

        sendToClient(new HPacket(String.format(
                "{in:ObjectUpdate}{i:8}{i:%s}{i:%d}{i:%d}{i:0}{s:\"%s\"}{s:\"0.0\"}{i:0}{i:0}{s:\"0\"}{i:-1}{i:1}{i:123}",
                txtUniqueId.getText(), ballX + 4, ballY, zTile))); // Derecha

        sendToClient(new HPacket(String.format(
                "{in:ObjectUpdate}{i:9}{i:%s}{i:%d}{i:%d}{i:0}{s:\"%s\"}{s:\"0.0\"}{i:0}{i:0}{s:\"0\"}{i:-1}{i:1}{i:123}",
                txtUniqueId.getText(), ballX, ballY - 4, zTile))); // Arriba

        sendToClient(new HPacket(String.format(
                "{in:ObjectUpdate}{i:10}{i:%s}{i:%d}{i:%d}{i:0}{s:\"%s\"}{s:\"0.0\"}{i:0}{i:0}{s:\"0\"}{i:-1}{i:1}{i:123}",
                txtUniqueId.getText(), ballX, ballY + 4, zTile))); // Abajo
    }

    public void kickBall(int PlusX, int PlusY){
        // Moves the tile in the client-side
        sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                1, 8237, ballX + PlusX, ballY + PlusY, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
        // Moves the user in the server-side
        sendToServer(new HPacket(String.format("{out:MoveAvatar}{i:%d}{i:%d}", ballX + PlusX, ballY + PlusY)));
        flagBallTrap = false;
    }

    public void Suggest(int ClickX, int ClickY){
        // Seria bueno en el futuro agregar una animacion del recorrido
        if(ClickX == ballX - 1 && ClickY == ballY - 1){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, ballX + 6, ballY + 6, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
        }
        else if(ClickX == ballX + 1 && ClickY == ballY + 1){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, ballX - 6, ballY - 6, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
        }
        else if(ClickX == ballX - 1 && ClickY == ballY + 1){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, ballX + 6, ballY - 6, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
        }
        else if(ClickX == ballX + 1 && ClickY == ballY - 1){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, ballX - 6, ballY + 6, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
        }

        if(ClickX == ballX - 1 && ClickY == ballY){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, ballX + 6, ballY, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
        }
        else if(ClickX == ballX + 1 && ClickY == ballY){
            System.out.println("6");
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, ballX - 6, ballY, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
        }
        else if(ClickX == ballX && ClickY == ballY + 1){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, ballX, ballY - 6, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
        }
        else if(ClickX == ballX && ClickY == ballY - 1){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, ballX, ballY + 6, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
        }
        sendToClient(new HPacket("{in:Chat}{i:-1}{s:\"Remember to press the ESCAPE key to kick\"}{i:0}{i:30}{i:0}{i:0}"));
    }

    public void handleClickThrough() {
        if(checkClickThrough.isSelected()){
            sendToClient(new HPacket("{in:YouArePlayingGame}{b:true}"));  // Enable Click Through
        }
        else{
            sendToClient(new HPacket("{in:YouArePlayingGame}{b:false}")); // Disable Click Through
        }
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent nativeKeyEvent) { }

    // I don't want to type in the chat when i press a key but unfortunately this in java cannot be solved :(, i think
    @Override
    public void nativeKeyPressed(NativeKeyEvent nativeKeyEvent) {
        if(nativeKeyEvent.getKeyCode() == NativeKeyEvent.VC_ESCAPE){
            guideTrap = false;  sendToServer(new HPacket(String.format("{out:MoveAvatar}{i:%d}{i:%d}", clickX, clickY)));
        }

        // restart booleans
        if(radioButtonTrap.isSelected() || radioButtonDribble.isSelected()){ flagBallTrap = false;   flagBallDribble = false; }

        String keyText = NativeKeyEvent.getKeyText(nativeKeyEvent.getKeyCode());
        TextInputControl[] txtFieldsHotKeys = new TextInputControl[]{txtShoot, txtTrap, txtDribble,
                txtMix, txtUpperLeft, txtUpperRight, txtLowerLeft, txtLowerRight};
        /* When the key is released, somehow the loop stops, however it reduces performance and fails sometimes, sorry :/
        new Thread(() -> { }).start();*/
        for(TextInputControl element: txtFieldsHotKeys){
            if(element.isFocused()){    // si alguno de los controles tiene el control hace algo...
                Platform.runLater(()-> element.setText(keyText));
                if(element.equals(txtShoot)){
                    Platform.runLater(()-> radioButtonShoot.setText(String.format("Shoot [Key %s]", keyText)));
                }
                else if(element.equals(txtTrap)){
                    Platform.runLater(()-> radioButtonTrap.setText(String.format("Trap [Key %s]", keyText)));
                }
                else if(element.equals(txtDribble)){
                    Platform.runLater(()-> radioButtonDribble.setText(String.format("Dribble [Key %s]", keyText)));
                }
                else if(element.equals(txtMix)){
                    Platform.runLater(()-> radioButtonMix.setText(String.format("Mix (Trap & Dribble) [Key %s]", keyText)));
                }
                // lastInputControl = element;
                Platform.runLater(labelShoot::requestFocus);    // Al parecer darle el foco a un label sin modificar es la mejor opcion
            }
            else if(!element.isFocused()){  // Si ninguno de los elementos tiene el foco...
                if(element.getText().equals(keyText)){
                    if(keyText.equals(txtShoot.getText())) keyShoot();
                    else if(keyText.equals(txtTrap.getText())) keyTrap();
                    else if(keyText.equals(txtDribble.getText())) keyDribble();
                    else if(keyText.equals(txtMix.getText())) keyMix();
                    else if(keyText.equals(txtUpperLeft.getText())) keyUpperLeft();
                    else if(keyText.equals(txtUpperRight.getText())) keyUpperRight();
                    else if(keyText.equals(txtLowerLeft.getText())) keyLowerLeft();
                    else if(keyText.equals(txtLowerRight.getText())) keyLowerRight();
                }
            }
        }
    }

    private void keyUpperLeft(){ // Superior izquierda
        sendToServer(new HPacket(String.format("{out:MoveAvatar}{i:%d}{i:%d}", ballX - 3, ballY - 3)));
    }

    private void keyUpperRight(){ // Superior derecha
        sendToServer(new HPacket(String.format("{out:MoveAvatar}{i:%d}{i:%d}", ballX + 3, ballY - 3)));
    }

    private void keyLowerLeft(){ // Inferior izquierda
        sendToServer(new HPacket(String.format("{out:MoveAvatar}{i:%d}{i:%d}", ballX - 3, ballY + 3)));
    }

    private void keyLowerRight(){ // Inferior derecha
        sendToServer(new HPacket(String.format("{out:MoveAvatar}{i:%d}{i:%d}", ballX + 3, ballY + 3)));
    }


    private void keyDoubleClick() {
        Platform.runLater(()-> radioButtonDoubleClick.setSelected(true));
        sendToServer(new HPacket(String.format("{out:UseFurniture}{i:%d}{i:0}", Integer.parseInt(txtBallId.getText()))));
        sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT, 1, 8237, ballX, ballY,
                0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
    }

    private void keyDribble() {
        Platform.runLater(()-> radioButtonDribble.setSelected(true));
        // En habbo futbol "Dribble" significa caminar, el usuario caminara dos casillas al frente del balon

        // Example -> Ball coords (8, 5) ; User up (8, 4)
        if (ballX == currentX && ballY > currentY) kickBall(0 ,2);

        // Example -> Ball coords (8, 5) ; User down (8, 6)
        if (ballX == currentX && ballY < currentY) kickBall(0, -2);

        // Example -> Ball coords (8, 5) ; User left (7, 5)
        if (ballX > currentX && ballY == currentY) kickBall(2, 0);

        // Example -> Ball coords (8, 5) ; User right (9, 5)
        if (ballX < currentX && ballY == currentY) kickBall(-2 , 0);

        // Example -> Ball coords (8, 5) ; User corner top left (7, 4)
        if (ballX > currentX && ballY > currentY) {
            if(ballX - 1 == currentX && ballY - 1 == currentY){ // BallX - 2 == CurrentX && BallY - 2 == CurrentY
                kickBall(2, 2);
            }
            else {
                sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                        1, 8237, ballX - 1, ballY - 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
                sendToServer(new HPacket(String.format("{out:MoveAvatar}{i:%d}{i:%d}", ballX - 1, ballY - 1)));
                flagBallDribble =  true;
            }
        }
        // Example -> Ball coords (8, 5) ; User corner top right (9, 4)
        if (ballX < currentX && ballY > currentY) {
            if(ballX + 1 == currentX && ballY - 1 == currentY){
                kickBall(-2 , 2);
            }
            else {
                sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                        1, 8237, ballX + 1, ballY - 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
                sendToServer(new HPacket(String.format("{out:MoveAvatar}{i:%d}{i:%d}", ballX + 1, ballY - 1)));
                flagBallDribble =  true;
            }
        }
        // Example -> Ball coords (8, 5) ; User corner lower left (7, 6)
        if (ballX > currentX && ballY < currentY) {
            if(ballX - 1 == currentX && ballY + 1 == currentY){
                kickBall(2, -2);
            }
            else {
                sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                        1, 8237, ballX - 1, ballY + 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
                sendToServer(new HPacket(String.format("{out:MoveAvatar}{i:%d}{i:%d}", ballX - 1, ballY + 1)));
                flagBallDribble =  true;
            }
        }
        // Example -> Ball coords (8, 5) ; User corner lower right (9, 6)
        if (ballX < currentX && ballY < currentY) {
            if(ballX + 1 == currentX && ballY + 1 == currentY){
                kickBall(-2 , -2);
            }
            else {
                sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                        1, 8237, ballX + 1, ballY + 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
                sendToServer(new HPacket(String.format("{out:MoveAvatar}{i:%d}{i:%d}", ballX + 1, ballY + 1)));
                flagBallDribble =  true;
            }
        }
    }

    // This is like an AutoTrap, mix between Trap and Dribble (Thanks to SoftBot)
    public void keyMix() {
        Platform.runLater(()-> radioButtonMix.setSelected(true));

        int num =  currentX - ballX;
        int num2 = currentY - ballY;
        int num3 = 0;
        int num4 = 0;
        int num5 = 1;

        if (Math.abs(num) == Math.abs(num2)) {
            if (num > 0 && num2 > 0) {
                num3 = -1;
                num4 = -1;
            } else if (num > 0 && num2 < 0) {
                num3 = -1;
                num4 = 1;
            } else if (num < 0 && num2 < 0) {
                num3 = 1;
                num4 = 1;
            } else if (num < 0 && num2 > 0) {
                num3 = 1;
                num4 = -1;
            }
        } else if (num > num2) {
            if (Math.abs(num) > Math.abs(num2)) {
                num3 = -1;
                num4 = 0;
            } else {
                num3 = 0;
                num4 = 1;
            }
        } else if (Math.abs(num) < Math.abs(num2)) {
            num3 = 0;
            num4 = -1;
        } else {
            num3 = 1;
            num4 = 0;
        }

        int num6 = ballX + num3 * num5;
        int num7 = ballY + num4 * num5;

        if (occupiedTile(num6, num7)) {
            num5++;
            num6 = ballX + num3 * num5;
            num7 = ballY + num4 * num5;
        }

        sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                1, 8237, num6, num7, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
        sendToServer(new HPacket(String.format("{out:MoveAvatar}{i:%d}{i:%d}", num6, num7)));
    }

    private boolean occupiedTile(int x, int y) {
        for (Map.Entry<Integer, HPoint> entry : userCoords.entrySet()) {
            HPoint value = entry.getValue();
            if (x == value.getX() && y == value.getY()) return true;
        }
        return false;
    }

    private void keyTrap() {
        Platform.runLater(()-> radioButtonTrap.setSelected(true));
        // En habbo futbol "Trap" significa pisar, el usuario caminara una casilla al frente del balon

        // Example -> Ball coords (8, 5) ; User up (8, 4)
        if (ballX == currentX && ballY > currentY) kickBall(0, 1);

        // Example -> Ball coords (8, 5) ; User down (8, 6)
        if (ballX == currentX && ballY < currentY) kickBall(0, -1);

        // Example -> Ball coords (8, 5) ; User left (7, 5)
        if (ballX > currentX && ballY == currentY) kickBall(1, 0);

        // Example -> Ball coords (8, 5) ; User right (9, 5)
        if (ballX < currentX && ballY == currentY) kickBall(-1, 0);

        // Example -> Ball coords (8, 5) ; User corner top left (7, 4)
        if (ballX > currentX && ballY > currentY) {
            if(ballX - 1 == currentX && ballY - 1 == currentY){
                kickBall(1, 1);
            }
            else {
                sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                        1, 8237, ballX - 1, ballY - 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
                sendToServer(new HPacket(String.format("{out:MoveAvatar}{i:%d}{i:%d}", ballX - 1, ballY - 1)));
                flagBallTrap =  true;
            }
        }
        // Example -> Ball coords (8, 5) ; User corner top right (9, 4)
        if (ballX < currentX && ballY > currentY) {
            if(ballX + 1 == currentX && ballY - 1 == currentY){
                kickBall(-1, 1);
            }
            else {
                sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                        1, 8237, ballX + 1, ballY - 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
                sendToServer(new HPacket(String.format("{out:MoveAvatar}{i:%d}{i:%d}", ballX + 1, ballY - 1)));
                flagBallTrap =  true;
            }
        }
        // Example -> Ball coords (8, 5) ; User corner lower left (7, 6)
        if (ballX > currentX && ballY < currentY) {
            if(ballX - 1 == currentX && ballY + 1 == currentY){
                kickBall(1, -1);
            }
            else {
                sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                        1, 8237, ballX - 1, ballY + 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
                sendToServer(new HPacket(String.format("{out:MoveAvatar}{i:%d}{i:%d}", ballX - 1, ballY + 1)));
                flagBallTrap =  true;
            }
        }
        // Example -> Ball coords (8, 5) ; User corner lower right (9, 6)
        if (ballX < currentX && ballY < currentY) {
            if(ballX + 1 == currentX && ballY + 1 == currentY){
                kickBall(-1 , -1);
            }
            else {
                sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                        1, 8237, ballX + 1, ballY + 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
                sendToServer(new HPacket(String.format("{out:MoveAvatar}{i:%d}{i:%d}", ballX + 1, ballY + 1)));
                flagBallTrap =  true;
            }
        }
    }

    private void keyShoot() {
        Platform.runLater(()-> radioButtonShoot.setSelected(true));
        sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                1, 8237, ballX, ballY, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, userName));
        sendToServer(new HPacket(String.format("{out:MoveAvatar}{i:%d}{i:%d}", ballX, ballY)));
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent nativeKeyEvent) { }

    public void handleGuideTile() {
        if(checkGuideTile.isSelected()){
            sendToClient(new HPacket("ObjectAdd", HMessage.Direction.TOCLIENT, 1,
                    Integer.parseInt(txtUniqueId.getText()), 0, 0, 0, "0.0" /*"3.5"*/, "0.2", 0, 0, "1", -1, 1, 2, userName));
        }
        else {
            sendToClient(new HPacket("{in:ObjectRemove}{i:\"1\"}{b:false}{i:8636337}{i:0}"));
        }
    }

    public void handleGuideTrap(){
        if(checkGuideTrap.isSelected()){
            sendToClient(new HPacket("ObjectAdd", HMessage.Direction.TOCLIENT, 2,
                    Integer.parseInt(txtUniqueId.getText()), 0, 0, 0, "0.0", "0.2", 0, 0, "1", -1, 1, 2, userName));
        }
        else {
            sendToClient(new HPacket("{in:ObjectRemove}{i:\"2\"}{b:false}{i:8636337}{i:0}"));
        }
    }

    public void handleDiagoKiller(ActionEvent actionEvent) {
        CheckBox chkBoxDiago = (CheckBox) actionEvent.getSource();
        if(chkBoxDiago.isSelected()){
            sendToClient(new HPacket(String.format(
                    "{in:ObjectAdd}{i:3}{i:%s}{i:-4}{i:4}{i:0}{s:\"0.5\"}{s:\"0.0\"}{i:0}{i:0}{s:\"0\"}{i:-1}{i:0}{i:123}{s:\"OwnerName\"}",
                    txtUniqueId.getText()))); // Diago Izquierda abajo

            sendToClient(new HPacket(String.format(
                    "{in:ObjectAdd}{i:4}{i:%s}{i:4}{i:4}{i:0}{s:\"0.5\"}{s:\"0.0\"}{i:0}{i:0}{s:\"0\"}{i:-1}{i:0}{i:123}{s:\"OwnerName\"}",
                    txtUniqueId.getText()))); // Diago Derecha abajo

            sendToClient(new HPacket(String.format(
                    "{in:ObjectAdd}{i:5}{i:%s}{i:-4}{i:-4}{i:0}{s:\"0.5\"}{s:\"0.0\"}{i:0}{i:0}{s:\"0\"}{i:-1}{i:0}{i:123}{s:\"OwnerName\"}",
                    txtUniqueId.getText()))); // Diago Izquierda Arriba

            sendToClient(new HPacket(String.format(
                    "{in:ObjectAdd}{i:6}{i:%s}{i:4}{i:-4}{i:0}{s:\"0.5\"}{s:\"0.0\"}{i:0}{i:0}{s:\"0\"}{i:-1}{i:0}{i:123}{s:\"OwnerName\"}",
                    txtUniqueId.getText()))); // Diago Derecha Arriba

            sendToClient(new HPacket(String.format(
                    "{in:ObjectAdd}{i:7}{i:%s}{i:-4}{i:0}{i:0}{s:\"0.5\"}{s:\"0.0\"}{i:0}{i:0}{s:\"0\"}{i:-1}{i:0}{i:123}{s:\"OwnerName\"}",
                    txtUniqueId.getText()))); // Izquierda

            sendToClient(new HPacket(String.format(
                    "{in:ObjectAdd}{i:8}{i:%s}{i:4}{i:0}{i:0}{s:\"0.5\"}{s:\"0.0\"}{i:0}{i:0}{s:\"0\"}{i:-1}{i:0}{i:123}{s:\"OwnerName\"}",
                    txtUniqueId.getText()))); // Derecha

            sendToClient(new HPacket(String.format(
                    "{in:ObjectAdd}{i:9}{i:%s}{i:0}{i:-4}{i:0}{s:\"0.5\"}{s:\"0.0\"}{i:0}{i:0}{s:\"0\"}{i:-1}{i:0}{i:123}{s:\"OwnerName\"}",
                    txtUniqueId.getText()))); // Arriba

            sendToClient(new HPacket(String.format(
                    "{in:ObjectAdd}{i:10}{i:%s}{i:0}{i:4}{i:0}{s:\"0.5\"}{s:\"0.0\"}{i:0}{i:0}{s:\"0\"}{i:-1}{i:0}{i:123}{s:\"OwnerName\"}",
                    txtUniqueId.getText()))); // Abajo
        }
        else {
            sendToClient(new HPacket("{in:ObjectRemove}{s:\"3\"}{b:false}{i:123}{i:0}"));
            sendToClient(new HPacket("{in:ObjectRemove}{s:\"4\"}{b:false}{i:123}{i:0}"));
            sendToClient(new HPacket("{in:ObjectRemove}{s:\"5\"}{b:false}{i:123}{i:0}"));
            sendToClient(new HPacket("{in:ObjectRemove}{s:\"6\"}{b:false}{i:123}{i:0}"));
            sendToClient(new HPacket("{in:ObjectRemove}{s:\"7\"}{b:false}{i:123}{i:0}"));
            sendToClient(new HPacket("{in:ObjectRemove}{s:\"8\"}{b:false}{i:123}{i:0}"));
            sendToClient(new HPacket("{in:ObjectRemove}{s:\"9\"}{b:false}{i:123}{i:0}"));
            sendToClient(new HPacket("{in:ObjectRemove}{s:\"10\"}{b:false}{i:123}{i:0}"));
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