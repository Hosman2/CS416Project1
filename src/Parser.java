import java.io.File;
import java.net.InetSocketAddress;
import java.util.*;

public class Parser {

    // Maps IDs to IP
    private final Map<String, InetSocketAddress> deviceMap = new HashMap<>();
    // Maps IDs to List of neighbor device IDs
    private final Map<String, List<String>> links = new HashMap<>();
    // Maps ID to virtual IPs
    private final Map<String, List<String>> virtualIpMap = new HashMap<>();
    // Maps ID to gateway virtual IPs
    private final Map<String, String> gatewayMap = new HashMap<>();

    public Parser(String filename) throws Exception {
        parse(filename);
    }

    private void parse(String filename) throws Exception {

        List<String> lines = new ArrayList<>();
        Scanner scanner = new Scanner(new File(filename));

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        scanner.close();

        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);

            if (line.contains(":"))
                break;

            String id = line;
            int port = Integer.parseInt(lines.get(i + 1));
            String ip = lines.get(i + 2);
            deviceMap.put(id, new InetSocketAddress(ip, port));
            i += 3;
            List<String> virtualIps = new ArrayList<>();
            while (i < lines.size() && lines.get(i).startsWith("net")) {
                virtualIps.add(lines.get(i));
                i++;
            }
            if (!virtualIps.isEmpty()) {
                virtualIpMap.put(id, virtualIps);
                if (isHost(id) && virtualIps.size() >= 2) {
                    gatewayMap.put(id, virtualIps.get(1));
                }
            }
        }

        while (i < lines.size()) {

            String linkLine = lines.get(i);
            String[] parts = linkLine.split(":");

            if (parts.length == 2) {

                String a = parts[0];
                String b = parts[1];

                links.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
                links.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
            }

            i++;
        }
    }

    private boolean isHost(String id) {
        return id.length() == 1 && Character.isLetter(id.charAt(0));
    }

    public InetSocketAddress getAddress(String id) {
        return deviceMap.get(id);
    }

    public List<InetSocketAddress> getNeighbors(String id) {

        List<InetSocketAddress> neighbors = new ArrayList<>();
        List<String> neighborIds = links.getOrDefault(id, Collections.emptyList());

        for (String neighborId : neighborIds) {
            InetSocketAddress addr = deviceMap.get(neighborId);
            if (addr != null) {
                neighbors.add(addr);
            }
        }

        return neighbors;
    }
    //Get Neighbors
    public List<String> getNeighborIds(String id) {
        return links.getOrDefault(id, Collections.emptyList());
    }
    //Get all virtual IPs
    public List<String> getVirtualIps(String id) {
        return virtualIpMap.getOrDefault(id, Collections.emptyList());
    }
    //Get virtual IP
    public String getHostVirtualIp(String id) {
        List<String> vips = virtualIpMap.get(id);
        if (vips == null || vips.isEmpty()) return null;
        return vips.get(0);
    }
    //Get gateway IP
    public String getGatewayVirtualIp(String id) {
        return gatewayMap.get(id);
    }
}