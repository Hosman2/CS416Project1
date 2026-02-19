import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

public class Host {

    private final String hostId;
    private final int listenPort;
    private final InetSocketAddress neighborSwitch;
    private final DatagramSocket socket;
    private final String myVirtualIp;
    private final String gatewayVirtualIp;
    private final String gatewayMac;


    public Host(String hostId, int listenPort, InetSocketAddress neighborSwitch, String myVirtualIp, String gatewayVirtualIp) throws Exception {
        this.hostId = hostId;
        this.listenPort = listenPort;
        this.neighborSwitch = neighborSwitch;
        this.socket = new DatagramSocket(listenPort);
        this.myVirtualIp = myVirtualIp;
        this.gatewayVirtualIp = gatewayVirtualIp;
        this.gatewayMac = extractIdFromVirtualIp(gatewayVirtualIp);

        System.out.println("Host " + hostId + " listening on " + listenPort);
        System.out.println("Connected switch: " + neighborSwitch.getAddress().getHostAddress() +
                ":" + neighborSwitch.getPort());
        System.out.println("My virtual IP: " + myVirtualIp);
        System.out.println("Gateway virtual IP: " + gatewayVirtualIp + "(gateway MAC=" + gatewayMac + ")");
    }

    public void start() {
        //Receiver thread
        Thread rx = new Thread(this::receiveLoop, "Host-RX-" + hostId);
        rx.setDaemon(true);
        rx.start();

        sendLoop();
    }

    private void sendLoop() {
        Scanner sc = new Scanner(System.in);
        while (true) {
            try {
                System.out.print("Enter destination virtual IP (or 'quit'): ");
                String dstVip = sc.nextLine();
                if (dstVip == null) continue;
                dstVip = dstVip.trim();
                if (dstVip.equalsIgnoreCase("quit")) break;
                if (dstVip.isEmpty()) continue;

                System.out.print("Message: ");
                String msg = sc.nextLine();
                if (msg == null) msg = "";
                msg = msg.trim();

                if (msg.contains(":")) msg = msg.replace(":", "_");

                String frame = hostId + ":" + gatewayMac + ":" + myVirtualIp + ":" + dstVip + ":" + msg;
                sendFrameToSwitch(frame);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        sc.close();
    }

    private void receiveLoop() {
        byte[] buffer = new byte[4096];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String frame = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                handleIncomingFrame(frame);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handleIncomingFrame(String frame) {
        //Frame format: srcMAC:dstMAC:srcVIP:dstVIP:message
        String[] parts = frame.split(":", 5);
        if (parts.length != 5) {
            System.out.println("\n[DEBUG] Malformed frame (expected 5 fields): " + frame);
            return;
        }

        String srcMac = parts[0];
        String dstMac = parts[1];
        String srcVip = parts[2];
        String dstVip = parts[3];
        String payload = parts[4];

        if (dstMac.equals(hostId)) {
            System.out.println("\n[RECEIVED @ " + hostId + "] from " + srcMac +
                    " (" + srcVip + " -> " + dstVip + "): " + payload);
        } else {
            System.out.println("\n[DEBUG] Flooded frame not for me. dstMAC=" + dstMac + ", myMAC=" + hostId +
                    " | srcMAC=" + srcMac + " srcVIP=" + srcVip + " dstVIP=" + dstVip);
        }
        System.out.println();
    }

    private void sendFrameToSwitch(String frame) throws Exception {
        byte[] data = frame.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                neighborSwitch.getAddress(),
                neighborSwitch.getPort());
        socket.send(packet);
    }

    private static String extractIdFromVirtualIp(String virtualIp) {
        int dot = virtualIp.lastIndexOf('.');
        if (dot < 0 || dot == virtualIp.length() - 1) return virtualIp;
        return virtualIp.substring(dot + 1);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 && args.length != 3) {
            System.out.println("Usage: java Host <HostID> [<MyVirtualIP> <GatewayVirtualIP>]");
            return;
        }

        String hostId = args[0];

        Parser parser = new Parser("Config");

        InetSocketAddress myAddr = parser.getAddress(hostId);
        if (myAddr == null) {
            System.out.println("Host ID not found in config: " + hostId);
            return;
        }

        List<InetSocketAddress> neighbors = parser.getNeighbors(hostId);
        if (neighbors.isEmpty()) {
            System.out.println("No neighbor switch found for host: " + hostId);
            return;
        }
        InetSocketAddress neighborSwitch = neighbors.get(0);

        String myVirtualIp;
        String gatewayVirtualIp;

        if (args.length == 3) {
            // Works NOW, no Parser changes required
            myVirtualIp = args[1];
            gatewayVirtualIp = args[2];
        } else {
            // myVirtualIp = parser.getVirtualIp(hostId);
            // gatewayVirtualIp = parser.getGatewayVirtualIp(hostId);

            System.out.println("Parser virtual IP methods not integrated yet.");
            System.out.println("Run with: java Host " + hostId + " <MyVirtualIP> <GatewayVirtualIP>");
            return;
        }

        Host host = new Host(hostId, myAddr.getPort(), neighborSwitch, myVirtualIp, gatewayVirtualIp);
        host.start();
    }
}