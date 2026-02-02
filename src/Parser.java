import java.io.File;
import java.net.InetSocketAddress;
import java.util.*;

public class Parser {

    // Maps ID to its IP
    private final Map<String, InetSocketAddress> deviceMap = new HashMap<>();
    // Maps ID to List of neighbor device IDs
    private final Map<String, List<String>> links = new HashMap<>();

    public Parser(String filename) throws Exception {
        parse(filename);
    }

    private void parse(String filename) throws Exception {
        Scanner scanner = new Scanner(new File(filename));
        List<String> linkLines = new ArrayList<>();
        //Parse device information
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            // Differentiates Links from Devices
            if (line.contains(":")) {
                linkLines.add(line);
                break;
            }

            String id = line;
            int port = Integer.parseInt(scanner.nextLine().trim());
            String ip = scanner.nextLine().trim();

            deviceMap.put(id, new InetSocketAddress(ip, port));
        }
        //Parse link information
        while (scanner.hasNextLine()) {
            linkLines.add(scanner.nextLine().trim());
        }
        for (String linkLine : linkLines) {
            String[] parts = linkLine.split(":");
            String a = parts[0];
            String b = parts[1];
            // Add device neighbors
            links.computeIfAbsent(a, c -> new ArrayList<>()).add(b);
            links.computeIfAbsent(b, c -> new ArrayList<>()).add(a);
        }
        scanner.close();
    }
    // Get IP and Port from ID
    public InetSocketAddress getAddress(String id) {
        return deviceMap.get(id);
    }
    // Get List of neighbors from ID
    public List<InetSocketAddress> getNeighbors(String id) {
        List<InetSocketAddress> neighbors = new ArrayList<>();
        List<String> neighborIds = links.getOrDefault(id, List.of());

        for (String neighborId : neighborIds) {
            neighbors.add(deviceMap.get(neighborId));
        }
        return neighbors;
    }
}
