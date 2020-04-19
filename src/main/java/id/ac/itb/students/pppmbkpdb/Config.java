package id.ac.itb.students.pppmbkpdb;

import org.apache.http.util.TextUtils;

public class Config {

    private static Config ourInstance = new Config();
    public static Config getInstance() {
        return ourInstance;
    }

    private final String NIM_FINDER_URL;
    private final String FP_SERIAL_PORT;

    private static String getEnv(String env, String defaultValue) {
        String envValue = System.getenv(env);
        return TextUtils.isEmpty(envValue) ? defaultValue : envValue;
    }

    private Config() {
        this.NIM_FINDER_URL = getEnv("NIM_FINDER_URL", "https://reksti.didithilmy.com/api/smartcampus/students/{nim}/");
        this.FP_SERIAL_PORT = getEnv("FP_SERIAL_PORT", "/dev/tty.usbserial-00000000");  // /dev/ttyUSB0
    }

    public String getNimFinderUrl() {
        return NIM_FINDER_URL;
    }

    public String getFpSerialPort() {
        return FP_SERIAL_PORT;
    }
}
