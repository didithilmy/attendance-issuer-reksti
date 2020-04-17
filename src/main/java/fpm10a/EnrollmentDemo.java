package fpm10a;
public class EnrollmentDemo {

    public static void main(String[] args) {
        FingerprintSensor sensor = new FingerprintSensor("/dev/ttyUSB0");
        sensor.open();
        HumanActionListener hal = new HumanActionListener() {
            @Override
            public void putFinger() {
                System.out.println("Place finger on the sensor.");
            }

            @Override
            public void removeFinger() {
                System.out.println("Remove finger.");
            }

            @Override
            public void waitWhileDataIsTransferring() {
                System.out.println("Wait.");
            }
        };    

        try {
            int fingerprintId = 10;
            sensor.enrollActivity(fingerprintId, hal);
            int[] ia = sensor.downloadModel(fingerprintId, 10000);
            String hstr = Helper.intArrayToHexString(ia);
            System.out.println("Model is " + ia.length + " bytes long.");
            System.out.println(hstr);
            System.out.println("Rewriting model...");
            sensor.uploadModel(2, ia, 10000);
            System.out.println("Loading model...");
            sensor.storeModel(15, 2, 10000);
            System.out.println("Matching model...");
            int confident = sensor.matchActivity(15, hal);
            System.out.println("Match result: " + confident);
        } catch (Exception e) {
            System.out.println(e);
        } finally {
            sensor.close();
        }
    }
}
