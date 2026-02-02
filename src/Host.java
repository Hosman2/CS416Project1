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

    public Host(String hostId, int listenPort, InetSocketAddress neighborSwitch) throws Exception{
        this.hostId = hostId;
        this.listenPort = listenPort;
        this.neighborSwitch = neighborSwitch;
        this.socket = new DatagramSocket(listenPort);

        System.out.println("Host " + hostId + " listening on " + listenPort);
        System.out.println("Connected switch: " + neighborSwitch.getAddress().getHostAddress() +
                ":" + neighborSwitch.getPort());
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
        while(true) {
            try {
                System.out.print("Enter destination host ID (or 'quit'): ");
                String dst = sc.nextLine().trim();
                if (dst.equalsIgnoreCase("quit"))break;
                if(dst.isEmpty()) continue;

                System.out.print("Message: ");
                String msg = sc.nextLine();
                if (msg == null) msg ="";
                msg = msg.trim();

                String frame = hostId + ":" + dst + ":" +msg;
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
        //Frame format: srcMAC:dstMAC:payload
        String[] parts = frame.split(":", 3);
        if (parts.length != 3) return;

        String src = parts[0];
        String dst = parts[1];
        String payload = parts[2];

        // If the frame wasn't actually for me, it arrived via flooding.
        if (dst.equals(this.hostId)) {
            System.out.println("\n[RECEIVED @ " + hostId + "] from " + src + ": " + payload);
        } else {
            System.out.println("[DEBUG] Destination MAC mismatch (flooded frame). " +
                    "Frame dst=" + dst + ", myMAC=" +hostId);
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

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java Host <HostID>");
            return;
        }

        String hostId = args[0];

        Parser parser = new Parser("Config");

        InetSocketAddress myAddr = parser.getAddress(hostId);
        if (myAddr == null) {
            System.out.println("Host ID not found in config: " + hostId);
            return;
        }

        // Host should only have one neighbor (the connected switch)
        List<InetSocketAddress> neighbors = parser.getNeighbors(hostId);
        if (neighbors.isEmpty()) {
            System.out.println("No neighbor switch found for host: " + hostId);
            return;
        }
        InetSocketAddress neighborSwitch = neighbors.get(0);

        Host host = new Host(hostId, myAddr.getPort(), neighborSwitch);
        host.start();
    }
}