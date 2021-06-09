import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Date;
import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

public class NTPTime {

    private static final NTPUDPClient ntpUDPClient = new NTPUDPClient();

    private static final String[] hosts = new String[]{
            "ntp02.oal.ul.pt",
            "ntp04.oal.ul.pt",
    };

    public static Instant getNTPTimestamp() {

        // We want to timeout if a response takes longer than 5 seconds
        ntpUDPClient.setDefaultTimeout(5000);

        for (String host : hosts) {

            try {
                InetAddress hostAddr = InetAddress.getByName(host);
                TimeInfo info = ntpUDPClient.getTime(hostAddr);

                return new Date(info.getMessage().getTransmitTimeStamp().getTime()).toInstant();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static void close(){
        ntpUDPClient.close();
    }
}
