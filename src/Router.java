import java.net.*;
import java.io.*;
import java.util.*;

public class Router {

    private String routerId;
    private int myPort;
    private DatagramSocket socket;

    private Map<String, InetSocketAddress> neighbors = new HashMap<>();

    private Map<String, ForwardingEntry> forwardingTable = new HashMap<>();

    // Link-state structures
    private Map<String, Set<String>> topology = new HashMap<>();
    private Map<String, String> lsaDatabase = new HashMap<>();
    private Map<String, String> subnetToRouter = new HashMap<>();
    private Set<String> mySubnets = new HashSet<>();
    private String myLSA = "";

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
    }

    public void start() throws Exception {

        System.out.println("Router " + routerId + " started...");

        // Send initial LSA
        sendInitialLSA();
        floodLSA(myLSA, null);

        Thread lsaThread = new Thread(() -> {
            while (true) {
                try {
                    floodAllKnownLSAs();
                    Thread.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        lsaThread.setDaemon(true);

        lsaThread.start();

        while (true) {
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            String frame = new String(packet.getData(), 0, packet.getLength()).trim();
            processFrame(frame);
        }
    }

    private void processFrame(String frame) throws Exception {

        String[] parts = frame.split(":", 6);
        if (parts.length < 6) {
            System.out.println("[DEBUG] Malformed frame: " + frame);
            return;
        }

        String flag = parts[0];
        String srcMAC = parts[1];
        String destMAC = parts[2];
        String srcIP = parts[3];
        String destIP = parts[4];
        String message = parts[5];

        // Routing packet (LSA)
        if (flag.equals("1")) {
            processLSA(message, srcMAC);
            return;
        }

        if (!destMAC.equals(routerId)) {
            return;
        }

        System.out.println("\nRouter " + routerId + " RECEIVED:");
        printFrame(srcMAC, destMAC, srcIP, destIP, message);

        String dstSubnet = destIP.split("\\.")[0];

        String destRouter = subnetToRouter.get(dstSubnet);

        if (destRouter == null) {
            System.out.println("[DEBUG] Unknown subnet " + dstSubnet);
            return;
        }

        if (destRouter.equals(routerId)) {
            // Send toward host (final delivery)
            String hostId = destIP.split("\\.")[1];  // "B" from net2.B

            String newFrame = "0:" + routerId + ":" + hostId + ":" +
                    srcIP + ":" + destIP + ":" + message;

            InetSocketAddress lanNeighbor = null;
            for (String neighborId : neighbors.keySet()) {
                if (neighborId.startsWith("S")) {
                    lanNeighbor = neighbors.get(neighborId);
                    break;
                }
            }

            if (lanNeighbor == null) {
                System.out.println("[DEBUG] No local switch for final delivery");
                return;
            }

            System.out.println("Router " + routerId + " DELIVERING TO HOST:");
            printFrame(routerId, hostId, srcIP, destIP, message);

            sendFrame(newFrame, lanNeighbor);
            return;
        }

        ForwardingEntry entry = forwardingTable.get(destRouter);

        if (entry == null) {
            System.out.println("[DEBUG] No route to router " + destRouter);
            return;
        }

        String nextHopId = entry.exitPortNeighborId;
        InetSocketAddress outgoingAddress = neighbors.get(nextHopId);

        if (outgoingAddress == null) {
            System.out.println("[DEBUG] No neighbor for " + nextHopId);
            return;
        }

        String newFrame = "0:" + routerId + ":" + nextHopId + ":" +
                srcIP + ":" + destIP + ":" + message;

        System.out.println("Router " + routerId + " FORWARDING:");
        printFrame(routerId, nextHopId, srcIP, destIP, message);

        sendFrame(newFrame, outgoingAddress);
    }

    // ===================== LINK STATE =====================

    private void sendInitialLSA() throws Exception {
        List<String> routerNeighbors = new ArrayList<>();
        for (String neighborId : neighbors.keySet()) {
            if (neighborId.startsWith("R")) {
                routerNeighbors.add(neighborId);
            }
        }

        String subnetList = String.join(",", mySubnets);
        String neighborList = String.join(",", routerNeighbors);
        myLSA = routerId + ":" + subnetList + ":" + neighborList;
        topology.put(routerId, new HashSet<>(routerNeighbors));
        lsaDatabase.put(routerId, myLSA);
        runDijkstra();
    }

    private void processLSA(String message, String sender) throws Exception {

        String[] parts = message.split(":", -1);

        if (parts.length < 3) {
            System.out.println("[DEBUG] Malformed LSA: " + message);
            return;
        }

        String router = parts[0];

        String old = lsaDatabase.get(router);
        if (message.equals(old)) {
            return;
        }

        lsaDatabase.put(router, message);

        String[] subnets = parts[1].isEmpty() ? new String[0] : parts[1].split(",");
        String[] neighborList = parts[2].isEmpty() ? new String[0] : parts[2].split(",");

        for (String subnet : subnets) {
            subnetToRouter.put(subnet, router);
        }

        Set<String> routerNeighbors = new HashSet<>();
        for (String neighborId : neighborList) {
            if (neighborId.startsWith("R")) {
                routerNeighbors.add(neighborId);
            }
        }

        topology.put(router, routerNeighbors);

        floodLSA(message, sender);

        System.out.println("[DEBUG] " + routerId + " learned LSA from " + router +
                " subnets=" + Arrays.toString(subnets) +
                " neighbors=" + Arrays.toString(neighborList));

        runDijkstra();
    }

    private void floodLSA(String message, String sender) throws Exception {
        for (String neighbor : neighbors.keySet()) {

            if (!neighbor.startsWith("R")) {
                continue;
            }

            if (sender != null && neighbor.equals(sender)) continue;

            String frame = "1:" + routerId + ":" + neighbor + ":::" + message;
            sendFrame(frame, neighbors.get(neighbor));
        }
    }

    private void floodAllKnownLSAs() throws Exception {
        for (String lsaMessage : lsaDatabase.values()) {
            floodLSA(lsaMessage, null);
        }
    }

    private void runDijkstra() {

        Map<String, Integer> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();

        for (String node : topology.keySet()) {
            dist.put(node, Integer.MAX_VALUE);
        }

        dist.put(routerId, 0);

        PriorityQueue<String> pq = new PriorityQueue<>(Comparator.comparingInt(dist::get));
        pq.add(routerId);

        while (!pq.isEmpty()) {
            String current = pq.poll();

            for (String neighbor : topology.getOrDefault(current, Collections.emptySet())) {

                int alt = dist.get(current) + 1;

                if (alt < dist.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    dist.put(neighbor, alt);
                    prev.put(neighbor, current);
                    pq.add(neighbor);
                }
            }
        }

        buildForwardingTable(prev);
    }

    private void buildForwardingTable(Map<String, String> prev) {

        forwardingTable.clear();

        for (String dest : prev.keySet()) {

            String nextHop = dest;

            while (prev.containsKey(nextHop) && !prev.get(nextHop).equals(routerId)) {
                nextHop = prev.get(nextHop);
            }

            if (prev.containsKey(nextHop)) {
                forwardingTable.put(dest, new ForwardingEntry(nextHop, null));
            }
        }

        System.out.println("\n[DEBUG] Updated Forwarding Table for " + routerId + ":");
        for (String dest : forwardingTable.keySet()) {
            System.out.println(dest + " -> " + forwardingTable.get(dest).exitPortNeighborId);
        }
    }

    // ===================== UTIL =====================

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

        for (String neighborId : parser.getNeighborIds(id)) {
            InetSocketAddress addr = parser.getAddress(neighborId);
            router.addNeighbor(neighborId,
                    addr.getAddress().getHostAddress(),
                    addr.getPort());
        }
        List<String> interfaces = parser.getVirtualIps(id);

        for (String iface : interfaces) {
            String subnet = iface.split("\\.")[0];  // "net1" from "net1.R1"

            router.mySubnets.add(subnet);
            router.subnetToRouter.put(subnet, id);
        }

        router.start();
    }
}