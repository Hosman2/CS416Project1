import java.net.*;
import java.io.*;
import java.util.*;

public class Router {

    private String routerId;
    private int myPort;
    private DatagramSocket socket;

    private Map<String, InetSocketAddress> neighbors = new HashMap<>();

    private Map<String, ForwardingEntry> forwardingTable = new HashMap<>();

    private static class ForwardingEntry {
        String exitPortNeighborId;
        String nextHopVirtualIP;

        ForwardingEntry(String exitPortNeighborId, String nextHopVirtualIP) {
            this.exitPortNeighborId = exitPortNeighborId;
            this.nextHopVirtualIP = nextHopVirtualIP;
        }
    }

    public Router(String routerId, int myPort) throws Exception {
        this.routerId = routerId;
        this.myPort = myPort;
        this.socket = new DatagramSocket(myPort);

        loadForwardingTable();
    }


    private void loadForwardingTable() {


        if (routerId.equals("R1")) {
            forwardingTable.put("net1", new ForwardingEntry("S1", null));
            forwardingTable.put("net2", new ForwardingEntry("S1", null));
            forwardingTable.put("net3", new ForwardingEntry(null, "net2.R2"));
        }

        if (routerId.equals("R2")) {
            forwardingTable.put("net3", new ForwardingEntry("S2", null));
            forwardingTable.put("net2", new ForwardingEntry("S2", null));
            forwardingTable.put("net1", new ForwardingEntry(null, "net2.R1"));
        }
    }

    public void start() throws Exception {

        System.out.println("Router " + routerId + " started...");

        while (true) {

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            String frame = new String(packet.getData(), 0, packet.getLength());

            processFrame(frame);
        }
    }


    private void processFrame(String frame) throws Exception {

        String[] parts = frame.split(":", 5);
        if (parts.length != 5) {
            System.out.println("\n[DEBUG] Malformed frame (expected 5 fields): " + frame);
            return;
        }

        String srcMAC = parts[0];
        String destMAC = parts[1];
        String srcIP = parts[2];
        String destIP = parts[3];
        String message = parts[4];

        System.out.println("\nRouter " + routerId + " RECEIVED:");
        printFrame(srcMAC, destMAC, srcIP, destIP, message);

        String destSubnet = destIP.split("\\.")[0];

        ForwardingEntry entry = forwardingTable.get(destSubnet);

        String srcNet = srcIP.substring(0,4);
        String destNet = destIP.substring(0,4);
        if (destMAC.equals(routerId)) {
            System.out.println("\n[RECEIVED @ " + routerId + "] from " + srcMAC +
                    " (" + srcIP + " -> " + destIP + "): " + message);
        } else if (srcNet.equals(destNet)) {
            System.out.println("\n[DEBUG] Message Received and Ignored. dstMAC=" + destMAC + ", myMAC=" + routerId +
                    " | srcMAC=" + srcMAC + " srcVIP=" + srcIP + " dstVIP=" + destIP);
        } else {
            System.out.println("\n[DEBUG] Message Ignored. dstMAC=" + destMAC + ", myMAC=" + routerId +
                    " | srcMAC=" + srcMAC + " srcVIP=" + srcIP + " dstVIP=" + destIP);
        }

        String nextHopId;
        InetSocketAddress outgoingAddress;

        if (entry.nextHopVirtualIP == null) {
            nextHopId = extractHostFromIP(destIP); // send directly to host
            outgoingAddress = neighbors.get(entry.exitPortNeighborId);
        }
        else {
            nextHopId = extractHostFromIP(entry.nextHopVirtualIP);
            outgoingAddress = neighbors.get(nextHopId);
        }

        String newSrcMAC = routerId;
        String newDestMAC = nextHopId;

        String newFrame = newSrcMAC + ":" +
                newDestMAC + ":" +
                srcIP + ":" +
                destIP + ":" +
                message;

        System.out.println("Router " + routerId + " FORWARDING:");
        printFrame(newSrcMAC, newDestMAC, srcIP, destIP, message);

        sendFrame(newFrame, outgoingAddress);
    }


    private void sendFrame(String frame, InetSocketAddress address) throws Exception {

        byte[] data = frame.getBytes();

        DatagramPacket packet =
                new DatagramPacket(data, data.length,
                        address.getAddress(), address.getPort());

        socket.send(packet);
    }

    private void printFrame(String srcMAC, String destMAC,
                            String srcIP, String destIP,
                            String message) {

        System.out.println("Source MAC: " + srcMAC);
        System.out.println("Dest MAC: " + destMAC);
        System.out.println("Source IP: " + srcIP);
        System.out.println("Dest IP: " + destIP);
        System.out.println("Message: " + message);
    }

    private String extractHostFromIP(String virtualIP) {
        // net2.R2 -> R2
        return virtualIP.split("\\.")[1];
    }


    public void addNeighbor(String neighborId, String ip, int port) {
        neighbors.put(neighborId,
                new InetSocketAddress(ip, port));
    }


    public static void main(String[] args) throws Exception {

        if (args.length != 1) {
            System.out.println("Usage: Router <ID>");
            return;
        }

        String id = args[0];

        Parser parser = new Parser("Config");

        InetSocketAddress myAddr = parser.getAddress(id);
        if (myAddr == null) {
            System.out.println("Router ID not found in config: " + id);
            return;
        }

        Router router = new Router(id, myAddr.getPort());

        // Add neighbors from parser
        for (String neighborId : parser.getNeighborIds(id)) {
            InetSocketAddress addr = parser.getAddress(neighborId);
            router.addNeighbor(neighborId,
                    addr.getAddress().getHostAddress(),
                    addr.getPort());
        }

        router.start();
    }
}
