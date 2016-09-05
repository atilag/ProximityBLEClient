package mozilla.cd.proximityble;

import java.util.UUID;

/**
 * Created by jgomez on 29/08/16.
 */
public class ProximityService {

    public static final UUID SERVICE_UUID = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff0");
    public static final UUID CHARACTERISTIC_MY_PUBLIC_KEY_UUID = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff1");
    public static final UUID CHARACTERISTIC_AES_SECRET_UUID = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff2");
    public static final UUID CHARACTERISTIC_PASSWORD_UUID = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff3");
    public static final UUID CHARACTERISTIC_SSID_UUID = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff4");


}
