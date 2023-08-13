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
import java.util.logging.LogManager;


@ExtensionInfo(
        Title = "GFootBall",
        Description = "Known as Non DC Bot (bye Ahmed)",
        Version = "1.0.7",
        Author = "Julianty"
)

// This library was used: https://github.com/kwhat/jnativehook
public class GFootBall extends ExtensionForm implements NativeKeyListener {
    public TextField txtBallId;
    public RadioButton radioButtonShoot, radioButtonTrap, radioButtonDribble,
            radioButtonDoubleClick, radioButtonWalk, radioButtonRun;
    public CheckBox checkBall, checkDisableDouble, checkClickThrough, checkGuideTile, checkHideBubble,
            checkGuideTrap, checkDiagoKiller;
    public Text textYourName, textIndex, textYourCoords, textBallCoords;

    public String YourName;
    public int CurrentX, CurrentY, ballX, ballY;
    public int ClickX, ClickY;
    public int YourIndex = -1;
    public boolean flagBallTrap = false, flagBallDribble = false, guideTrap = false;
    public TextField txtShoot, txtTrap, txtDribble, txtDoubleClick, txtUniqueId;
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
        // sendToServer(new HPacket("InfoRetrieve", HMessage.Direction.TOSERVER)); // When its sent, gets UserObject packet
        // sendToServer(new HPacket("AvatarExpression", HMessage.Direction.TOSERVER, 0));  // With this it's not necessary to restart the room
        sendToServer(new HPacket("{out:InfoRetrieve}"));
        sendToServer(new HPacket("{out:AvatarExpression}{i:0}"));


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
        YourIndex = -1;
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
            int YourID = hMessage.getPacket().readInteger();    YourName = hMessage.getPacket().readString();
            textYourName.setText("Your Name: " + YourName);    // No es necesario usar Platform.runLater()
        });

        // Response of packet AvatarExpression (gets userIndex)
        intercept(HMessage.Direction.TOCLIENT, "Expression", hMessage -> {
            // First integer is index in room, second is animation id, i think
            if(primaryStage.isShowing() && YourIndex == -1){ // this could avoid any bug
                YourIndex = hMessage.getPacket().readInteger();
                textIndex.setText("Your Index: " + YourIndex);  // GUI updated!
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
        });

        // Intercepts this packet when you enter or any user arrive to the room
        intercept(HMessage.Direction.TOCLIENT, "Users", hMessage -> {
            try {
                HPacket hPacket = hMessage.getPacket();
                HEntity[] roomUsersList = HEntity.parse(hPacket);
                for (HEntity hEntity: roomUsersList){
                    if(YourName.equals(hEntity.getName())){
                        YourIndex = hEntity.getIndex();
                    }
                }
            } catch (NullPointerException ignored) { }
            if(checkClickThrough.isSelected()){
                sendToClient(new HPacket("YouArePlayingGame", HMessage.Direction.TOCLIENT, true));
            }
        });

        // Intercepts when users walk in the room
        intercept(HMessage.Direction.TOCLIENT, "UserUpdate", hMessage -> {
            HPacket hPacket = hMessage.getPacket();
            // The HEntityUpdate class allows obtain the index of the user who is walking and other things
            for (HEntityUpdate hEntityUpdate: HEntityUpdate.parse(hPacket)){
                try {
                    int CurrentIndex = hEntityUpdate.getIndex();
                    if(CurrentIndex == YourIndex){
                        textIndex.setText("Your Index: " + CurrentIndex);

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
                            CurrentX = hEntityUpdate.getMovingTo().getX();  CurrentY = hEntityUpdate.getMovingTo().getY();
                        }
                        if(radioButtonWalk.isSelected()){
                            CurrentX = hEntityUpdate.getTile().getX();  CurrentY = hEntityUpdate.getTile().getY();
                        }
                        textYourCoords.setText("Your Coords: (" + CurrentX + ", " + CurrentY + ")");

                        if(flagBallTrap){
                            if(ballX - 1 == CurrentX && ballY - 1 == CurrentY){
                                kickBall(1, 1);
                                flagBallTrap = false;
                            }
                            if(ballX + 1 == CurrentX && ballY - 1 == CurrentY){
                                kickBall(-1, 1);
                                flagBallTrap =  false;
                            }
                            if(ballX - 1 == CurrentX && ballY + 1 == CurrentY){
                                kickBall(1, -1);
                                flagBallTrap = false;
                            }
                            if(ballX + 1 == CurrentX && ballY + 1 == CurrentY){
                                kickBall(-1 , -1);
                                flagBallTrap = false;
                            }
                        }
                        if(flagBallDribble){
                            if(ballX - 1 == CurrentX && ballY - 1 == CurrentY){
                                kickBall(2, 2);
                                flagBallDribble = false;
                            }
                            if(ballX + 1 == CurrentX && ballY - 1 == CurrentY){
                                kickBall(-2, 2);
                                flagBallDribble =  false;
                            }
                            if(ballX - 1 == CurrentX && ballY + 1 == CurrentY){
                                kickBall(2, -2);
                                flagBallDribble = false;
                            }
                            if(ballX + 1 == CurrentX && ballY + 1 == CurrentY){
                                kickBall(-2, -2);
                                flagBallDribble = false;
                            }
                        }
                    }
                }
                catch (NullPointerException nullPointerException) {/*getMovingTo() throws a NullPointerException error*/}
            }
        });

        intercept(HMessage.Direction.TOSERVER, "MoveAvatar", hMessage -> {
            if(guideTrap){
                ClickX = hMessage.getPacket().readInteger();    ClickY = hMessage.getPacket().readInteger();
                Suggest(ClickX, ClickY);
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
                    int direction = hMessage.getPacket().readInteger();
                    String zTile = hMessage.getPacket().readString();
                    Platform.runLater(()-> textBallCoords.setText("Ball Coords: (" + ballX + ", " + ballY + ")"));
                    if(checkDiagoKiller.isSelected()){
                        // Diago Izquierda Abajo
                        sendToClient(new HPacket(String.format(
                                "{in:ObjectUpdate}{i:3}{i:%s}{i:%d}{i:%d}{i:0}{s:\"%s\"}{s:\"0.0\"}{i:0}{i:0}{s:\"1\"}{i:-1}{i:1}{i:123}",
                                txtUniqueId.getText(), ballX - 4, ballY + 4, zTile)));
                        // Diago Derecha Abajo
                        sendToClient(new HPacket(String.format(
                                "{in:ObjectUpdate}{i:4}{i:%s}{i:%d}{i:%d}{i:0}{s:\"%s\"}{s:\"0.0\"}{i:0}{i:0}{s:\"1\"}{i:-1}{i:1}{i:123}",
                                txtUniqueId.getText(), ballX + 4, ballY + 4, zTile)));
                        // Diago Izquierda Arriba
                        sendToClient(new HPacket(String.format(
                                "{in:ObjectUpdate}{i:5}{i:%s}{i:%d}{i:%d}{i:0}{s:\"%s\"}{s:\"0.0\"}{i:0}{i:0}{s:\"1\"}{i:-1}{i:1}{i:123}",
                                txtUniqueId.getText(), ballX - 4, ballY - 4, zTile)));
                        // Diago Derecha Arriba
                        sendToClient(new HPacket(String.format(
                                "{in:ObjectUpdate}{i:6}{i:%s}{i:%d}{i:%d}{i:0}{s:\"%s\"}{s:\"0.0\"}{i:0}{i:0}{s:\"1\"}{i:-1}{i:1}{i:123}",
                                txtUniqueId.getText(), ballX + 4, ballY - 4, zTile)));
                    }
                }
            }
            catch (Exception ignored){ }
        });

        /* When you move ball with admin rights, useful in holos i think, so ignore this
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
        }); */

        // Intercepts when you give double click on a furniture
        intercept(HMessage.Direction.TOSERVER, "UseFurniture", hMessage -> {
            if(checkDisableDouble.isSelected()){
                hMessage.setBlocked(true);
            }
            else if(checkBall.isSelected() && !checkDisableDouble.isSelected()){
                int BallID = hMessage.getPacket().readInteger();
                txtBallId.setText(String.valueOf(BallID));
                checkBall.setSelected(false);
            }
        });
    }

    public void kickBall(int PlusX, int PlusY){
        // Moves the tile in the client-side
        sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                1, 8237, ballX + PlusX, ballY + PlusY, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        // Moves the user in the server-side
        sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, ballX + PlusX, ballY + PlusY));
        flagBallTrap = false;
    }

    public void Suggest(int ClickX, int ClickY){
        // Seria bueno en el futuro agregar una animacion del recorrido
        if(ClickX == ballX - 1 && ClickY == ballY - 1){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, ballX + 6, ballY + 6, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        }
        else if(ClickX == ballX + 1 && ClickY == ballY + 1){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, ballX - 6, ballY - 6, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        }
        else if(ClickX == ballX - 1 && ClickY == ballY + 1){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, ballX + 6, ballY - 6, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        }
        else if(ClickX == ballX + 1 && ClickY == ballY - 1){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, ballX - 6, ballY + 6, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        }

        if(ClickX == ballX - 1 && ClickY == ballY){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, ballX + 6, ballY, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        }
        else if(ClickX == ballX + 1 && ClickY == ballY){
            System.out.println("6");
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, ballX - 6, ballY, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        }
        else if(ClickX == ballX && ClickY == ballY + 1){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, ballX, ballY - 6, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        }
        else if(ClickX == ballX && ClickY == ballY - 1){
            sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                    2, 8237, ballX, ballY + 6, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        }
        sendToClient(new HPacket("{in:Chat}{i:-1}{s:\"Remember to press the ESCAPE key to kick\"}{i:0}{i:30}{i:0}{i:0}"));
    }

    public void handleClickThrough() {
        if(checkClickThrough.isSelected()){
            sendToClient(new HPacket("YouArePlayingGame", HMessage.Direction.TOCLIENT, true));  // Enable Click Through
        }
        else{
            sendToClient(new HPacket("YouArePlayingGame", HMessage.Direction.TOCLIENT, false)); // Disable Click Through
        }
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent nativeKeyEvent) { }

    // I don't want to type in the chat when i press a key but unfortunately this in java cannot be solved :(, i think
    @Override
    public void nativeKeyPressed(NativeKeyEvent nativeKeyEvent) {
        if(nativeKeyEvent.getKeyCode() == NativeKeyEvent.VC_ESCAPE){
            guideTrap = false;  sendToServer(new HPacket("{out:MoveAvatar}{i:"+ClickX+"}{i:"+ClickY+"}"));
        }
        flagBallTrap = false;   flagBallDribble = false;    // restart booleans

        String keyText = NativeKeyEvent.getKeyText(nativeKeyEvent.getKeyCode());
        TextInputControl[] txtFieldsHotKeys = new TextInputControl[]{txtShoot, txtTrap, txtDribble, txtDoubleClick};
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
                else if(element.equals(txtDoubleClick)){
                    Platform.runLater(()-> radioButtonDoubleClick.setText(String.format("DoubleClick [Key %s]", keyText)));
                }
                // lastInputControl = element;
                Platform.runLater(labelShoot::requestFocus);    // Al parecer darle el foco a un label sin modificar es la mejor opcion
            }
            else if(!element.isFocused()){  // Si ninguno de los elementos tiene el foco...
                if(element.getText().equals(keyText)){
                    if(keyText.equals(txtShoot.getText())){
                        keyShoot();
                    }
                    else if(keyText.equals(txtTrap.getText())){
                        keyTrap();
                    }
                    else if(keyText.equals(txtDribble.getText())){
                        keyDribble();
                    }
                    else if(keyText.equals(txtDoubleClick.getText())){
                        keyDoubleClick();
                    }
                }
            }
        }
    }

    private void keyDoubleClick() {
        radioButtonDoubleClick.setSelected(true);
        sendToServer(new HPacket("UseFurniture", HMessage.Direction.TOSERVER,
                Integer.parseInt(txtBallId.getText()), 0));
        sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT, 1, 8237, ballX, ballY,
                0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
    }

    private void keyDribble() {
        radioButtonDribble.setSelected(true);
        // En habbo futbol "Dribble" significa caminar, el usuario caminara dos casillas al frente del balon

        // Example -> Ball coords (8, 5) ; User up (8, 4)
        if (ballX == CurrentX && ballY > CurrentY)
        {
            kickBall(0 ,2);
        }
        // Example -> Ball coords (8, 5) ; User down (8, 6)
        if (ballX == CurrentX && ballY < CurrentY)
        {
            kickBall(0, -2);
        }
        // Example -> Ball coords (8, 5) ; User left (7, 5)
        if (ballX > CurrentX && ballY == CurrentY)
        {
            kickBall(2, 0);
        }
        // Example -> Ball coords (8, 5) ; User right (9, 5)
        if (ballX < CurrentX && ballY == CurrentY)
        {
            kickBall(-2 , 0);
        }

        // Example -> Ball coords (8, 5) ; User corner top left (7, 4)
        if (ballX > CurrentX && ballY > CurrentY)
        {
            if(ballX - 1 == CurrentX && ballY - 1 == CurrentY){ // BallX - 2 == CurrentX && BallY - 2 == CurrentY
                kickBall(2, 2);
            }
            else {
                sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                        1, 8237, ballX - 1, ballY - 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
                sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, ballX - 1, ballY - 1));
                flagBallDribble =  true;
            }
        }
        // Example -> Ball coords (8, 5) ; User corner top right (9, 4)
        if (ballX < CurrentX && ballY > CurrentY)
        {
            if(ballX + 1 == CurrentX && ballY - 1 == CurrentY){
                kickBall(-2 , 2);
            }
            else {
                sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                        1, 8237, ballX + 1, ballY - 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
                sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, ballX + 1, ballY - 1));
                flagBallDribble =  true;
            }
        }
        // Example -> Ball coords (8, 5) ; User corner lower left (7, 6)
        if (ballX > CurrentX && ballY < CurrentY)
        {
            if(ballX - 1 == CurrentX && ballY + 1 == CurrentY){
                kickBall(2, -2);
            }
            else {
                sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                        1, 8237, ballX - 1, ballY + 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
                sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, ballX - 1, ballY + 1));
                flagBallDribble =  true;
            }
        }
        // Example -> Ball coords (8, 5) ; User corner lower right (9, 6)
        if (ballX < CurrentX && ballY < CurrentY)
        {
            if(ballX + 1 == CurrentX && ballY + 1 == CurrentY){
                kickBall(-2 , -2);
            }
            else {
                sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                        1, 8237, ballX + 1, ballY + 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
                sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, ballX + 1, ballY + 1));
                flagBallDribble =  true;
            }
        }
    }

    private void keyTrap() {
        radioButtonTrap.setSelected(true);
        // En habbo futbol "Trap" significa pisar, el usuario caminara una casilla al frente del balon

        // Example -> Ball coords (8, 5) ; User up (8, 4)
        if (ballX == CurrentX && ballY > CurrentY)
        {
            kickBall(0, 1);
        }
        // Example -> Ball coords (8, 5) ; User down (8, 6)
        if (ballX == CurrentX && ballY < CurrentY)
        {
            kickBall(0, -1);
        }
        // Example -> Ball coords (8, 5) ; User left (7, 5)
        if (ballX > CurrentX && ballY == CurrentY)
        {
            kickBall(1, 0);
        }
        // Example -> Ball coords (8, 5) ; User right (9, 5)
        if (ballX < CurrentX && ballY == CurrentY)
        {
            kickBall(-1, 0);
        }

        // Example -> Ball coords (8, 5) ; User corner top left (7, 4)
        if (ballX > CurrentX && ballY > CurrentY)
        {
            if(ballX - 1 == CurrentX && ballY - 1 == CurrentY){
                kickBall(1, 1);
            }
            else {
                sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                        1, 8237, ballX - 1, ballY - 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
                sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, ballX - 1, ballY - 1));
                flagBallTrap =  true;
            }
        }
        // Example -> Ball coords (8, 5) ; User corner top right (9, 4)
        if (ballX < CurrentX && ballY > CurrentY)
        {
            if(ballX + 1 == CurrentX && ballY - 1 == CurrentY){
                kickBall(-1, 1);
            }
            else {
                sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                        1, 8237, ballX + 1, ballY - 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
                sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, ballX + 1, ballY - 1));
                flagBallTrap =  true;
            }
        }
        // Example -> Ball coords (8, 5) ; User corner lower left (7, 6)
        if (ballX > CurrentX && ballY < CurrentY)
        {
            if(ballX - 1 == CurrentX && ballY + 1 == CurrentY){
                kickBall(1, -1);
            }
            else {
                sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                        1, 8237, ballX - 1, ballY + 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
                sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, ballX - 1, ballY + 1));
                flagBallTrap =  true;
            }
        }
        // Example -> Ball coords (8, 5) ; User corner lower right (9, 6)
        if (ballX < CurrentX && ballY < CurrentY)
        {
            if(ballX + 1 == CurrentX && ballY + 1 == CurrentY){
                kickBall(-1 , -1);
            }
            else {
                sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                        1, 8237, ballX + 1, ballY + 1, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
                sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, ballX + 1, ballY + 1));
                flagBallTrap =  true;
            }
        }
    }

    private void keyShoot() {
        radioButtonShoot.setSelected(true);
        sendToClient(new HPacket("ObjectUpdate", HMessage.Direction.TOCLIENT,
                1, 8237, ballX, ballY, 0, "0.0", "1.0", 0, 0, 1, 822083583, 2, YourName));
        sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, ballX, ballY));
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent nativeKeyEvent) { }

    public void handleGuideTile() {
        if(checkGuideTile.isSelected()){
            sendToClient(new HPacket("ObjectAdd", HMessage.Direction.TOCLIENT, 1,
                    Integer.parseInt(txtUniqueId.getText()), 0, 0, 0, "0.0" /*"3.5"*/, "0.2", 0, 0, "1", -1, 1, 2, YourName));
        }
        else {
            sendToClient(new HPacket("ObjectRemove", HMessage.Direction.TOCLIENT, "1", false, 8636337, 0));
        }
    }

    public void handleGuideTrap(){
        if(checkGuideTrap.isSelected()){
            sendToClient(new HPacket("ObjectAdd", HMessage.Direction.TOCLIENT, 2,
                    Integer.parseInt(txtUniqueId.getText()), 0, 0, 0, "0.0", "0.2", 0, 0, "1", -1, 1, 2, YourName));
        }
        else {
            sendToClient(new HPacket("ObjectRemove", HMessage.Direction.TOCLIENT, "2", false, 8636337, 0));
        }
    }

    public void handleDiagoKiller(ActionEvent actionEvent) {
        CheckBox checkBox = (CheckBox) actionEvent.getSource();
        if(checkBox.isSelected()){
            // Diago Izquierda abajo
            sendToClient(new HPacket(String.format(
                    "{in:ObjectAdd}{i:3}{i:%s}{i:-4}{i:4}{i:0}{s:\"0.5\"}{s:\"0.0\"}{i:0}{i:0}{s:\"\"}{i:-1}{i:0}{i:123}{s:\"OwnerName\"}",
                    txtUniqueId.getText())));

            // Diago Derecha abajo
            sendToClient(new HPacket(String.format(
                    "{in:ObjectAdd}{i:4}{i:%s}{i:4}{i:4}{i:0}{s:\"0.5\"}{s:\"0.0\"}{i:0}{i:0}{s:\"\"}{i:-1}{i:0}{i:123}{s:\"OwnerName\"}",
                    txtUniqueId.getText())));

            // Diago Izquierda Arriba
            sendToClient(new HPacket(String.format(
                    "{in:ObjectAdd}{i:5}{i:%s}{i:-4}{i:-4}{i:0}{s:\"0.5\"}{s:\"0.0\"}{i:0}{i:0}{s:\"\"}{i:-1}{i:0}{i:123}{s:\"OwnerName\"}",
                    txtUniqueId.getText())));

            // Diago Derecha Arriba
            sendToClient(new HPacket(String.format(
                    "{in:ObjectAdd}{i:6}{i:%s}{i:4}{i:-4}{i:0}{s:\"0.5\"}{s:\"0.0\"}{i:0}{i:0}{s:\"\"}{i:-1}{i:0}{i:123}{s:\"OwnerName\"}",
                    txtUniqueId.getText())));
        }
        else {
            sendToClient(new HPacket("{in:ObjectRemove}{s:\"3\"}{b:false}{i:123}{i:0}"));
            sendToClient(new HPacket("{in:ObjectRemove}{s:\"4\"}{b:false}{i:123}{i:0}"));
            sendToClient(new HPacket("{in:ObjectRemove}{s:\"5\"}{b:false}{i:123}{i:0}"));
            sendToClient(new HPacket("{in:ObjectRemove}{s:\"6\"}{b:false}{i:123}{i:0}"));
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
