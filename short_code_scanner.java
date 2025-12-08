import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class NeoFastScanner extends JFrame {
    private static final String FONT_PATH = "fonts/Perfect_DOS_VGA_437.ttf";
    private Font dosFont = new Font("Monospaced", Font.PLAIN, 16);

    private final DefaultTableModel table = new DefaultTableModel(
        new String[]{"TARGET", "IP", "PORTS", "TITLE", "SERVER", "TECH", "SEC"}, 0);
    private final JTextArea log = new JTextArea();
    private final ExecutorService pool = Executors.newFixedThreadPool(20);

    public NeoFastScanner() {
        setTitle("NEO SCANNER v10 - HYPER MODE");
        setSize(1400, 900);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        try {
            if (Files.exists(Paths.get(FONT_PATH))) {
                dosFont = Font.createFont(Font.TRUETYPE_FONT, new File(FONT_PATH)).deriveFont(18f);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(dosFont);
            }
        } catch (Exception ignored) {}

        initUI();
        log("NEO SCANNER v10 READY", "GREEN");
    }

    private void initUI() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.BLACK);

        // Header
        JTextArea header = new JTextArea(
            "╔══════════════════════════════════════════════════════════════╗\n" +
            "║                  NEO SCANNER v10 - HYPER MODE                ║\n" +
            "╚══════════════════════════════════════════════════════════════╝");
        header.setFont(dosFont);
        header.setForeground(Color.GREEN);
        header.setBackground(Color.BLACK);
        header.setEditable(false);
        add(header, BorderLayout.NORTH);

        // Table
        JTable jt = new JTable(table);
        jt.setFont(dosFont);
        jt.setForeground(Color.GREEN);
        jt.setBackground(Color.BLACK);
        jt.setGridColor(new Color(0, 80, 0));
        jt.setRowHeight(30);
        add(new JScrollPane(jt), BorderLayout.CENTER);

        // Bottom panel
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(Color.BLACK);

        JTextField input = new JTextField("https://example.com");
        input.setFont(dosFont);
        input.setForeground(Color.GREEN);
        input.setBackground(Color.BLACK);
        input.setCaretColor(Color.GREEN);

        JButton scan = new JButton(">> EXECUTE <<");
        scan.setFont(dosFont);
        scan.setForeground(Color.GREEN);
        scan.setBackground(new Color(0, 40, 0));
        scan.addActionListener(e -> scanTarget(input.getText().trim()));

        JPanel ctrl = new JPanel();
        ctrl.setBackground(Color.BLACK);
        ctrl.add(input);
        ctrl.add(scan);
        bottom.add(ctrl, BorderLayout.NORTH);

        log.setFont(dosFont);
        log.setForeground(Color.GREEN);
        log.setBackground(Color.BLACK);
        log.setEditable(false);
        bottom.add(new JScrollPane(log), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // CRT effect
        Timer flicker = new Timer(100, e -> {
            int a = (System.currentTimeMillis() % 300 < 150) ? 30 : 50;
            getContentPane().setBackground(new Color(0, a, 0));
        });
        flicker.start();
    }

    private void log(String msg, String type) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new Date());
        Color c = type.equals("RED") ? Color.RED : type.equals("YELLOW") ? Color.YELLOW : new Color(0,255,100);
        SwingUtilities.invokeLater(() -> {
            log.append(String.format("[%s] %s\n", time, msg));
            log.setForeground(c);
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    private void scanTarget(String target) {
        if (target.isEmpty()) {
            log("NO TARGET", "RED");
            return;
        }

        pool.submit(() -> {
            try {
                String url = target.matches("^https?://.*") ? target : "http://" + target;
                URL u = new URL(url);
                String host = u.getHost();
                String ip = InetAddress.getByName(host).getHostAddress();

                log("LOCKED: " + host + " [" + ip + "]", "GREEN");

                Set<Integer> openPorts = ConcurrentHashMap.newKeySet();
                List<Future<?>> tasks = new ArrayList<>();

                int[] ports = {80, 443, 21, 22, 25, 53, 110, 143, 993, 995, 8080, 8443, 3389, 3306, 5432};
                for (int p : ports) {
                    tasks.add(pool.submit(() -> {
                        if (checkPort(ip, p)) {
                            openPorts.add(p);
                            log("PORT " + p + "/tcp OPEN", "YELLOW");
                            if (p == 80 || p == 443 || p == 8080 || p == 8443) {
                                fetchWebInfo(host, p);
                            }
                        }
                    }));
                }

                for (Future<?> f : tasks) f.get(); // wait all

                String portsStr = openPorts.isEmpty() ? "NONE" : openPorts.stream()
                    .sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));

                SwingUtilities.invokeLater(() -> table.addRow(new Object[]{
                    host, ip, portsStr, "—", "—", "—", "—"
                }));

                log("SCAN COMPLETE → " + host, "GREEN");

            } catch (Exception ex) {
                log("ERROR: " + ex.getMessage(), "RED");
            }
        });
    }

    private boolean checkPort(String ip, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 1500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void fetchWebInfo(String host, int port) {
        String proto = (port == 443 || port == 8443) ? "https" : "http";
        String url = proto + "://" + host + (port != 80 && port != 443 ? ":" + port : "");

        try {
            Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(7000)
                .followRedirects(true)
                .get();

            String title = doc.title();
            if (title.length() > 40) title = title.substring(0, 37) + "...";

            String server = doc.select("meta[name=server],meta[property=server]").attr("content");
            if (server.isEmpty()) {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                server = conn.getHeaderField("Server");
                conn.disconnect();
            }
            server = server == null ? "Hidden" : server.split(" ")[0];

            StringBuilder tech = new StringBuilder();
            String body = doc.body().text().toLowerCase();
            if (body.contains("wordpress")) tech.append("WP ");
            if (body.contains("react")) tech.append("React ");
            if (body.contains("vue")) tech.append("Vue ");
            if (body.contains("laravel")) tech.append("Laravel ");
            tech.append(server.contains("nginx") ? "nginx" : server.contains("Apache") ? "Apache" : "");

            int sec = 0;
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            if (conn.getHeaderField("X-Frame-Options") != null) sec++;
            if (conn.getHeaderField("X-Content-Type-Options") != null) sec++;
            if (conn.getHeaderField("Strict-Transport-Security") != null) sec++;
            conn.disconnect();

            String finalTech = tech.toString().trim();
            if (finalTech.isEmpty()) finalTech = "Unknown";

            int row = table.getRowCount() - 1;
            SwingUtilities.invokeLater(() -> {
                table.setValueAt(title.isEmpty() ? "No Title" : title, row, 3);
                table.setValueAt(server, row, 4);
                table.setValueAt(finalTech, row, 5);
                table.setValueAt(sec + "/3", row, 6);
            });

            log("WEB → \"" + title + "\" | " + server + " | " + finalTech, "GREEN");

        } catch (Exception ignored) {
            log("WEB FAILED ON PORT " + port, "RED");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new NeoFastScanner().setVisible(true));
    }
}