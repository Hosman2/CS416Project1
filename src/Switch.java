import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Switch {

    private final String switchId;
    private final int listenPort;
    private final DatagramSocket socket;

    //Switch table: MAC address -> Neighbor (IP + Port)

    private final Map<String, InetSocketAddress> switchTable =
            new ConcurrentHashMap<>();

    //All directly connected neighbors

    private final List<InetSocketAddress> neighbors;

    public Switch(String switchId,
                  int listenPort,
                  List<InetSocketAddress> neighbors) throws Exception {

        this.switchId = switchId;
        this.listenPort = listenPort;
        this.neighbors = neighbors;
        this.socket = new DatagramSocket(listenPort);

        System.out.println("Switch " + switchId + " listening on port " + listenPort);
        System.out.println("Neighbors: " + neighbors);
    }


    public void start() {
        byte[] buffer = new byte[4096];

        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                InetSocketAddress incomingPort =
                        new InetSocketAddress(packet.getAddress(), packet.getPort());

                String frame = new String(packet.getData(), 0, packet.getLength()).trim();
                handleFrame(frame, incomingPort);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //Ethernet Learning Switch Logic
    private void handleFrame(String frame, InetSocketAddress incomingPort) throws Exception {
        // Frame format: srcMAC:dstMAC:payload
        String[] parts = frame.split(":", 3);
        if (parts.length != 3) return;

        String srcMac = parts[0];
        String dstMac = parts[1];

        if (!switchTable.containsKey(srcMac)) {
            switchTable.put(srcMac, incomingPort);
            printSwitchTable();
        }

        if (switchTable.containsKey(dstMac)) {
            InetSocketAddress outPort = switchTable.get(dstMac);

            if (!outPort.equals(incomingPort)) {
                sendFrame(frame, outPort);
            }
        } else {
            flood(frame, incomingPort);
        }
    }

    //Flood frame to all ports except incoming
    private void flood(String frame, InetSocketAddress incomingPort) throws Exception {
        for (InetSocketAddress neighbor : neighbors) {
            if (!neighbor.equals(incomingPort)) {
                sendFrame(frame, neighbor);
            }
        }
    }

    //Send frame via UDP

    private void sendFrame(String frame, InetSocketAddress target) throws Exception {
        byte[] data = frame.getBytes();
        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                target.getAddress(),
                target.getPort()
        );
        socket.send(packet);
    }

    //Prints the switch table
    private void printSwitchTable() {
        System.out.println("\n--- Switch Table @ " + switchId + " ---");
        for (Map.Entry<String, InetSocketAddress> entry : switchTable.entrySet()) {
            System.out.println(
                    "MAC " + entry.getKey() + " -> " +
                            entry.getValue().getAddress().getHostAddress() +
                            ":" + entry.getValue().getPort()
            );
        }
        System.out.println("------------------------\n");
    }

    public static void main(String[] args) throws Exception {

        if (args.length != 1) {
            System.out.println("Usage: java Switch <SwitchID>");
            return;
        }

        String switchId = args[0];

        Parser parser = new Parser("Config");

        InetSocketAddress myAddress = parser.getAddress(switchId);
        if (myAddress == null) {
            System.out.println("Switch ID not found in config: " + switchId);
            return;
        }

        List<InetSocketAddress> neighbors = parser.getNeighbors(switchId);

        Switch sw = new Switch(
                switchId,
                myAddress.getPort(),
                neighbors
        );

        sw.start();
    }
}
