package ua.urbanist.core;

import java.net.*;
import java.util.*;

public final class NetInfo {
    private NetInfo() {}
    /** Локальні IPv4-адреси (Wi-Fi, точка доступу, Ethernet). */
    public static List<String> ips() {
        List<String> r = new ArrayList<String>();
        try {
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
            while (e != null && e.hasMoreElements()) {
                NetworkInterface ni = e.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress a = ia.getAddress();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) r.add(a.getHostAddress());
                }
            }
        } catch (SocketException ignored) { }
        return r;
    }
    /** Широкомовні адреси підмереж для пошуку ігор. */
    public static List<InetAddress> broadcasts() {
        List<InetAddress> r = new ArrayList<InetAddress>();
        try {
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
            while (e != null && e.hasMoreElements()) {
                NetworkInterface ni = e.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) if (ia.getBroadcast() != null) r.add(ia.getBroadcast());
            }
        } catch (SocketException ignored) { }
        return r;
    }
}
