package id.ac.itb.students.pppmbkpdb;

import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import javafx.event.ActionEvent;
import javafx.scene.control.TextField;
import fpm10a.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import jssc.SerialPort;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.smartcardio.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class Controller {
    @FXML
    private Label lblTime;

    @FXML
    private Label lblDate;

    @FXML
    private Label lblName;

    @FXML
    private Label lblNIM;

    @FXML
    private Label lblScanPrompt;

    @FXML
    private TextField txtNIM;

    @FXML
    public ComboBox<String> pcdListPICC;

    @FXML
    private AnchorPane paneData;

    @FXML
    private AnchorPane paneScan;

    @FXML
    private AnchorPane paneTap;

    @FXML
    private AnchorPane paneDelete;

    @FXML
    private AnchorPane paneCancel;

    @FXML
    private AnchorPane paneNext;

    private String name, NIM;
    private int[] fingerprintTemplate;
    private byte[] fpChunk1, fpChunk2, fpChunk3, fpChunk4;
    private boolean readSuccess;

    private CardTerminal cardTerminal;
    private CardChannel cardChannel;
    private Card card;

    private int pendingAction;

    public final static int PENDING_ACTION_NONE = 0;
    public final static int PENDING_ACTION_WRITE = 1;
    public final static int PENDING_ACTION_CLEAR = 2;
    public final static int FP_ENROLLMENT_FINGER_ID = 10;
    public final static String NIM_FINDER_URL = Config.getInstance().getNimFinderUrl();
    public final static String FP_SERIAL_PORT = Config.getInstance().getFpSerialPort();

    private FingerprintSensor fingerprintSensor;
    private HumanActionListener humanActionListener;
    private boolean isEnrollingFinger = false;

    public void initialize() {
        Timer timer = new Timer("Display Timer");

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                // Task to be executed every second
                Platform.runLater(new Runnable() {

                    @Override
                    public void run() {
                        DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
                        DateFormat dateFormat = new SimpleDateFormat("MMM dd");
                        Calendar cali = Calendar.getInstance();
                        cali.getTime();
                        String time = timeFormat.format(cali.getTimeInMillis());
                        String date = dateFormat.format(cali.getTimeInMillis());
                        lblTime.setText(time);
                        lblDate.setText(date);
                    }
                });
            }
        };

        timer.scheduleAtFixedRate(task, 1000, 1000);

        doCardReaderCommunication();
        initializeFingerprintDevice();
        setPendingAction(PENDING_ACTION_NONE);
    }

    private void onCardAttached() {
        System.out.println("card attached");
        readSuccess = false;
        try {
            card = cardTerminal.connect("*");
            cardChannel = card.getBasicChannel();
            readSuccess = initiateCardState();

            if(readSuccess) {
                if(pendingAction == PENDING_ACTION_WRITE) {
                    doWriteData();

                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            setPendingAction(PENDING_ACTION_NONE);
                        }
                    });
                } else if(pendingAction == PENDING_ACTION_CLEAR) {
                    doClearData();

                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            setPendingAction(PENDING_ACTION_NONE);
                        }
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onCardDetached() {
        System.out.println("card detached");
    }

    private boolean initiateCardState() throws CardException {
        // // Authenticate block 78 : FF 86 00 00 05 01 00 78 60 00
        if(!authenticateBlock((byte) 0x78)) {
            System.out.println("Failed authenticating block 0x78");
            return false;
        }

        System.out.println("Block 0x78 authenticated");
        return true;
    }

    private boolean authenticateBlock(byte blockNo) throws CardException {
        // // Authenticate block 78 : FF 86 00 00 05 01 00 78 60 00
        System.out.println("Authenticating block " + Helper.byteArrayToHexString(new byte[] {blockNo}));
        CommandAPDU commandAPDU = new CommandAPDU(new byte[] {(byte) 0xFF, (byte) 0x86, (byte) 0x00, (byte) 0x00, (byte) 0x05, (byte) 0x01, (byte) 0x00, blockNo, (byte) 0x60, (byte) 0x00, });
        ResponseAPDU responseAPDU = cardChannel.transmit(commandAPDU);

        if(responseAPDU.getSW() != 0x9000) {
            return false;
        } else {
            return true;
        }
    }

    private byte[] readBlocks(byte blockLength, byte... blocks) throws CardException {
        boolean readSuccess = true;
        int i = 0;
        byte[] unpadded = new byte[0];

        while(readSuccess && i < blocks.length) {
            byte blockNo = blocks[i];
            // Read block:  FF B0 00 [b] [l]
            CommandAPDU apdu = new CommandAPDU(new byte[] {(byte) 0xFF, (byte) 0xB0, 0x00, blockNo, blockLength});
            ResponseAPDU rApdu = cardChannel.transmit(apdu);

            if(rApdu.getSW() != 0x9000) {
                System.out.println("Failed reading block " + Helper.byteArrayToHexString(new byte[] {blockNo}));
                readSuccess = false;
            } else {
                i++;
                unpadded = Helper.concatBytes(unpadded, rApdu.getData());
            }
        }

        if(readSuccess) {
            byte[] depadded;
            int paddingIndex = Helper.getPaddingIndex(unpadded, (byte) 0x00, (byte) 0xFF);
            if (paddingIndex != 0) { // Padding found
                depadded = new byte[paddingIndex];
                System.arraycopy(unpadded, 0, depadded, 0, Math.min(unpadded.length, depadded.length));
            } else {    // No padding found
                depadded = unpadded;
            }

            return depadded;
        } else {
            return null;
        }
    }

    //FF D6 00 78 10 01 06 05 01 07 02 09 02 FF 00 00 00 00 00 00 00
    private boolean writeBlock(byte[] data, byte... blocks) throws CardException {
        boolean writeSuccess = true, writeFinished = false;
        int i = 0;
        int currIndex;

        // Add padding if data does not reach maximum length
        // But it's computationally expensive
        if(data.length < 16*blocks.length) data = Helper.concatBytes(data, new byte[] {(byte) 0xFF});

        while(writeSuccess && !writeFinished && i < blocks.length) {
            byte blockNo = blocks[i];

            currIndex = i * 16;
            System.out.println("Writing block 0x" + Helper.byteArrayToHexString(new byte[] {blockNo}));
            int dataLength = data.length - currIndex;
            byte[] toWrite = new byte[17];  // Last one for Lc byte
            System.arraycopy(data, currIndex, toWrite, 0, Math.min(dataLength, 16));
            System.out.println("Writing: " + Helper.byteArrayToHexString(toWrite));

            if(dataLength < 16) {
                writeFinished = true;
            }

            byte[] cmd = new byte[]{(byte) 0xFF, (byte) 0xD6, (byte) 0x00, blockNo, (byte) 0x10};
            byte[] apduBytes = Helper.concatBytes(cmd, toWrite);

            CommandAPDU apdu = new CommandAPDU(apduBytes);
            ResponseAPDU rApdu = cardChannel.transmit(apdu);

            if(rApdu.getSW() != 0x9000) {
                System.out.println("Failed writing block " + Helper.byteArrayToHexString(new byte[] {blockNo}));
                writeSuccess = false;
            } else {
                i++;
            }
        }

        return writeSuccess;
    }

    private void doCardReaderCommunication() {
        Task task = new Task() {
            @Override
            protected Object call() throws Exception {
                TerminalFactory terminalFactory = TerminalFactory.getDefault();
                try {
                    List<CardTerminal> cardTerminalList = terminalFactory.terminals().list();
                    if (cardTerminalList.size() > 0) {
                        System.out.println("Congratulations, setup is working. At least 1 cardreader is detected");
                        cardTerminal = cardTerminalList.get(0);
                        while (true) {
                            cardTerminal.waitForCardPresent(0);
                            System.out.println("Inserted card");
                            onCardAttached();
                            cardTerminal.waitForCardAbsent(0);
                            onCardDetached();
                            System.out.println("Removed card");
                        }
                    } else {
                        System.out.println("Ouch, setup is NOT working. No cardreader is detected");
                    }
                } catch (Exception e) {
                    System.out.println("An exception occured while doing card reader communication.");
                    e.printStackTrace();
                }
                return null;
            }
        };
        Thread thread = new Thread(task);
        thread.start();
    }

    private void setPendingAction(int pendingAction) {
        if(pendingAction != PENDING_ACTION_NONE) {
            paneDelete.setVisible(false);
            paneNext.setVisible(false);
            paneCancel.setVisible(true);
            paneData.setVisible(false);
            paneTap.setVisible(true);
            paneScan.setVisible(false);
        } else {
            // Action cancelled
            paneDelete.setVisible(true);
            paneNext.setVisible(true);
            paneCancel.setVisible(false);
            paneData.setVisible(true);
            paneTap.setVisible(false);
            name = "";
            NIM = "";
            lblName.setText("");
            lblNIM.setText("");
        }
        this.pendingAction = pendingAction;
    }

    @FXML
    private void findNIM(ActionEvent event) {
        retrieveMhsData(txtNIM.getText());
    }

    @FXML
    private void clearData(MouseEvent event) {
        setPendingAction(PENDING_ACTION_CLEAR);
    }

    @FXML
    private void cancelCardUpdate(MouseEvent event) {
        setPendingAction(PENDING_ACTION_NONE);
    }

    private void doClearData() {
        try {
            System.out.println("Clearing data...");
            // Authenticate block 78 : FF 86 00 00 05 01 00 78 60 00
            if (!authenticateBlock((byte) 0x78)) {
                System.out.println("Failed authenticating block 0x78");
                return;
            }

            // Clear NIM and name

            if (!writeBlock(new byte[16*3], (byte) 0x78, (byte) 0x79, (byte) 0x7A)) {
                System.out.println("Failed clearing data");
                return;
            }

            // Clear fingerprint template
            boolean fpCleared1 = writeBlock(new byte[16*15],
                    (byte) 0x80, (byte) 0x81, (byte) 0x82, (byte) 0x83, (byte) 0x84, (byte) 0x85, (byte) 0x86, (byte) 0x87,
                    (byte) 0x88, (byte) 0x89, (byte) 0x8A, (byte) 0x8B, (byte) 0x8C, (byte) 0x8D, (byte) 0x8E);

            if (!fpCleared1) {
                System.out.println("Failed clearing first FP template");
                return;
            }

            if (!authenticateBlock((byte) 0x90)) {
                System.out.println("Failed authenticating block 0x90");
                return;
            }

            boolean fpCleared2 = writeBlock(new byte[16*15],
                    (byte) 0x90, (byte) 0x91, (byte) 0x92, (byte) 0x93, (byte) 0x94, (byte) 0x95, (byte) 0x96, (byte) 0x97,
                    (byte) 0x98, (byte) 0x99, (byte) 0x9A, (byte) 0x9B, (byte) 0x9C, (byte) 0x9D, (byte) 0x9E);

            if (!fpCleared2) {
                System.out.println("Failed clearing second FP template");
                return;
            }

            if (!authenticateBlock((byte) 0xA0)) {
                System.out.println("Failed authenticating block 0x90");
                return;
            }

            boolean fpCleared3 = writeBlock(new byte[16*15],
                    (byte) 0xA0, (byte) 0xA1, (byte) 0xA2, (byte) 0xA3, (byte) 0xA4, (byte) 0xA5, (byte) 0xA6, (byte) 0xA7,
                    (byte) 0xA8, (byte) 0xA9, (byte) 0xAA, (byte) 0xAB, (byte) 0xAC, (byte) 0xAD, (byte) 0xAE);

            if (!fpCleared3) {
                System.out.println("Failed clearing third FP template");
                return;
            }

            if (!authenticateBlock((byte) 0xB0)) {
                System.out.println("Failed authenticating block 0x90");
                return;
            }

            boolean fpCleared4 = writeBlock(new byte[16*15],
                    (byte) 0xB0, (byte) 0xB1, (byte) 0xB2, (byte) 0xB3, (byte) 0xB4, (byte) 0xB5, (byte) 0xB6, (byte) 0xB7,
                    (byte) 0xB8, (byte) 0xB9, (byte) 0xBA, (byte) 0xBB, (byte) 0xBC, (byte) 0xBD, (byte) 0xBE);

            if (!fpCleared4) {
                System.out.println("Failed clearing fourth FP template");
                return;
            }
        } catch (CardException e) {
            e.printStackTrace();
        }
    }


    @FXML
    private void writeData(MouseEvent event) {
        setPendingAction(PENDING_ACTION_WRITE);
    }

    private void doWriteData() {
        try {
            System.out.println("Writing data...");
            // Authenticate block 78 : FF 86 00 00 05 01 00 78 60 00
            if (!authenticateBlock((byte) 0x78)) {
                System.out.println("Failed authenticating block 0x78");
                return;
            }

            // Write NIM

            if(!writeBlock(Helper.numberStringToByteArray(lblNIM.getText()), (byte) 0x78)) {
                System.out.println("Failed writing NIM");
                return;
            }

            if (!writeBlock(lblName.getText().getBytes(), (byte) 0x79, (byte) 0x7A)) {
                System.out.println("Failed writing name");
                return;
            }

            // Chunk fp template
            chunkFingerprintData();

            // Write fingerprint template
            if(fpChunk1 != null) {
                if (!authenticateBlock((byte) 0x80)) {
                    System.out.println("Failed authenticating block 0x80");
                    return;
                }

                boolean fpCleared1 = writeBlock(fpChunk1,
                        (byte) 0x80, (byte) 0x81, (byte) 0x82, (byte) 0x83, (byte) 0x84, (byte) 0x85, (byte) 0x86, (byte) 0x87,
                        (byte) 0x88, (byte) 0x89, (byte) 0x8A, (byte) 0x8B, (byte) 0x8C, (byte) 0x8D, (byte) 0x8E);

                if (!fpCleared1) {
                    System.out.println("Failed clearing first FP template");
                    return;
                }
            }

            if(fpChunk2 != null) {
                if (!authenticateBlock((byte) 0x90)) {
                    System.out.println("Failed authenticating block 0x90");
                    return;
                }

                boolean fpCleared2 = writeBlock(fpChunk2,
                        (byte) 0x90, (byte) 0x91, (byte) 0x92, (byte) 0x93, (byte) 0x94, (byte) 0x95, (byte) 0x96, (byte) 0x97,
                        (byte) 0x98, (byte) 0x99, (byte) 0x9A, (byte) 0x9B, (byte) 0x9C, (byte) 0x9D, (byte) 0x9E);

                if (!fpCleared2) {
                    System.out.println("Failed clearing second FP template");
                    return;
                }
            }

            if(fpChunk3 != null) {

                if (!authenticateBlock((byte) 0xA0)) {
                    System.out.println("Failed authenticating block 0x90");
                    return;
                }

                boolean fpCleared3 = writeBlock(fpChunk3,
                        (byte) 0xA0, (byte) 0xA1, (byte) 0xA2, (byte) 0xA3, (byte) 0xA4, (byte) 0xA5, (byte) 0xA6, (byte) 0xA7,
                        (byte) 0xA8, (byte) 0xA9, (byte) 0xAA, (byte) 0xAB, (byte) 0xAC, (byte) 0xAD, (byte) 0xAE);

                if (!fpCleared3) {
                    System.out.println("Failed clearing third FP template");
                    return;
                }
            }

            if(fpChunk4 != null) {
                if (!authenticateBlock((byte) 0xB0)) {
                    System.out.println("Failed authenticating block 0x90");
                    return;
                }

                boolean fpCleared4 = writeBlock(fpChunk4,
                        (byte) 0xB0, (byte) 0xB1, (byte) 0xB2, (byte) 0xB3, (byte) 0xB4, (byte) 0xB5, (byte) 0xB6, (byte) 0xB7,
                        (byte) 0xB8, (byte) 0xB9, (byte) 0xBA, (byte) 0xBB, (byte) 0xBC, (byte) 0xBD, (byte) 0xBE);

                if (!fpCleared4) {
                    System.out.println("Failed clearing fourth FP template");
                    return;
                }
            }
        } catch (CardException e) {
            e.printStackTrace();
        }
    }

    private void retrieveMhsData(String nim) {
        Task task = new Task() {
            @Override
            protected Object call() throws Exception {
                try {
                    JSONArray jo = Unirest.get(NIM_FINDER_URL)
                            .queryString("input", nim)
                            .asJson().getBody().getArray();

                    if(jo.length() == 0) {
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                mhsNotFound();
                            }
                        });
                    } else {
                        JSONObject jb = jo.getJSONObject(0);
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                mhsFound(jb.getString("nimF"), jb.getString("name"));
                            }
                        });
                        enroll();
                    }
                    return null;
                } catch (UnirestException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        };
        Thread thread = new Thread(task);
        thread.start();
    }

    private void mhsNotFound() {

    }

    private void mhsFound(String nim, String name) {
        lblName.setText(name);
        lblNIM.setText(nim);
        paneScan.setVisible(true);
    }

    private void initializeFingerprintDevice() {
        fingerprintSensor = new FingerprintSensor(FP_SERIAL_PORT, SerialPort.BAUDRATE_115200);
        fingerprintSensor.open();
        System.out.println("Fingerprint scanner initialized.");
        humanActionListener = new HumanActionListener() {
            @Override
            public void putFinger() {
                System.out.println("Waiting finger...");
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        if(isEnrollingFinger) lblScanPrompt.setText("Letakkan jari Anda pada alat pemindai");
                    }
                });
            }

            @Override
            public void removeFinger() {
                System.out.println("Release finger");
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        if(isEnrollingFinger) lblScanPrompt.setText("Lepaskan jari Anda");
                    }
                });
            }

            @Override
            public void waitWhileDataIsTransferring() {
                System.out.println("Transferring data...");
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        if(isEnrollingFinger) lblScanPrompt.setText("Tunggu sebentar...");
                    }
                });
            }
        };
    }

    private void enroll() {
        isEnrollingFinger = true;
        System.out.println("Begin enrollment");
        fingerprintSensor.enrollActivity(FP_ENROLLMENT_FINGER_ID, humanActionListener);
        System.out.println("Downloading model...");
        fingerprintTemplate = fingerprintSensor.downloadModel(FP_ENROLLMENT_FINGER_ID, 10000);

        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                lblScanPrompt.setText("Pemindaian jari selesai!");
            }
        });

        if(fingerprintTemplate.length > 240*4) {
            System.out.println("Error: template too large");
            return;
        }
        System.out.println("Model downloaded");
        System.out.println("Model is " + fingerprintTemplate.length + " bytes long.");
        System.out.println(Helper.intArrayToHexString(fingerprintTemplate));

        isEnrollingFinger = false;
    }

    private void chunkFingerprintData() {
        // Cast int to byte
        byte[] array = new byte[fingerprintTemplate.length];
        for(int i = 0; i < fingerprintTemplate.length; i++) {
            array[i] = (byte) fingerprintTemplate[i];
        }

        System.out.println("Array: " + Helper.byteArrayToHexString(array));

        fpChunk1 = new byte[Math.min(fingerprintTemplate.length, 240)];
        System.arraycopy(array, 0, fpChunk1, 0, fpChunk1.length);

        if(fingerprintTemplate.length > 240) {
            fpChunk2 = new byte[Math.min(fingerprintTemplate.length - 240, 240)];
            System.arraycopy(array, 240, fpChunk2, 0, fpChunk2.length);

            if(fingerprintTemplate.length > 480) {
                fpChunk3 = new byte[Math.min(fingerprintTemplate.length - 480, 240)];
                System.arraycopy(array, 480, fpChunk3, 0, fpChunk3.length);

                if(fingerprintTemplate.length > 720) {
                    fpChunk4 = new byte[Math.min(fingerprintTemplate.length - 720, 240)];
                    System.arraycopy(array, 720, fpChunk4, 0, fpChunk4.length);
                }
            }
        }
    }
}
