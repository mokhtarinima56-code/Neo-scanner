import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class Main extends JFrame {
    private static final String FONT_PATH = "fonts/Perfect_DOS_VGA_437.ttf";
    private Font dosFont;

    private JTable matrixTable;
    private DefaultTableModel tableModel;
    private JTextArea logArea;
    private JTextArea mapArea;
    private JTextField urlField;
    private JButton executeBtn;
    private JLabel crtOverlay;

    private final Map<String, TargetInfo> results = new LinkedHashMap<>();

    public Main() {
        setTitle("NEO SCANNER v9.9 - DOS MODE");
        setSize(1280, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        loadDosFont();
        initUI();
        applyCrtStyle();
        startCrtFlicker();
        log("SYSTEM BOOT", "INFO");
        log("ENTER URL", "INFO");
    }

    private void loadDosFont() {
        try {
            if (Files.exists(Paths.get(FONT_PATH))) {
                dosFont = Font.createFont(Font.TRUETYPE_FONT, new File(FONT_PATH)).deriveFont(16f);
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                ge.registerFont(dosFont);
                log("DOS VGA FONT LOADED", "INFO");
            } else {
                dosFont = new Font("Monospaced", Font.PLAIN, 16);
                log("FONT MISSING: " + FONT_PATH, "ALERT");
            }
        } catch (Exception e) {
            dosFont = new Font("Monospaced", Font.PLAIN, 16);
            log("FONT LOAD FAILED", "ALERT");
        }
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(Color.BLACK);

        // Header
        JTextArea header = new JTextArea(
            "╔══════════════════════════════════════════════════════════════╗\n" +
            "║                NEO SCANNER v9.9 - WEB RECON                  ║\n" +
            "╚══════════════════════════════════════════════════════════════╝"
        );
        header.setEditable(false);
        header.setBackground(Color.BLACK);
        header.setForeground(new Color(0, 255, 0));
        header.setFont(dosFont);
        header.setAlignmentX(CENTER_ALIGNMENT);
        mainPanel.add(header, BorderLayout.NORTH);

        // Center: Table + Map + Log
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        centerSplit.setResizeWeight(0.6);

        // Matrix Table
        tableModel = new DefaultTableModel(new String[]{"DOMAIN", "PORTS", "TITLE", "SERVER", "SECURITY"}, 0);
        matrixTable = new JTable(tableModel);
        matrixTable.setFont(dosFont);
        matrixTable.getTableHeader().setFont(dosFont);
        matrixTable.setRowHeight(28);
        matrixTable.setBackground(Color.BLACK);
        matrixTable.setForeground(new Color(0, 255,0));
        matrixTable.setGridColor(new Color(0,51,0));
        centerSplit.setLeftComponent(new JScrollPane(matrixTable));

        // Right panel: Map + Log
        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        rightSplit.setResizeWeight(0.3);

        mapArea = new JTextArea();
        mapArea.setEditable(false);
        mapArea.setBackground(Color.BLACK);
        mapArea.setForeground(new Color(0,255,0));
        mapArea.setFont(dosFont);
        rightSplit.setTopComponent(new JScrollPane(mapArea));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(new Color(0,255,0));
        logArea.setFont(dosFont);
        rightSplit.setBottomComponent(new JScrollPane(logArea));

        centerSplit.setRightComponent(rightSplit);
        mainPanel.add(centerSplit, BorderLayout.CENTER);

        // Control Panel
        JPanel control = new JPanel();
        control.setBackground(Color.BLACK);
        control.setLayout(new BoxLayout(control, BoxLayout.X_AXIS));

        urlField = new JTextField("https://example.com");
        urlField.setMaximumSize(new Dimension(600, 40));
        urlField.setFont(dosFont);
        urlField.setForeground(new Color(0,255,0));
        urlField.setBackground(Color.BLACK);
        urlField.setCaretColor(Color.GREEN);
        control.add(urlField);

        executeBtn = new JButton("EXECUTE");
        executeBtn.setFont(dosFont);
        executeBtn.setBackground(new Color(0,17,0));
        executeBtn.setForeground(Color.GREEN);
        executeBtn.setFocusPainted(false);
        executeBtn.addActionListener(e -> startScan());
        control.add(Box.createHorizontalStrut(10));
        control.add(executeBtn);

        JButton clearBtn = new JButton("PURGE");
        clearBtn.setFont(dosFont);
        clearBtn.setBackground(new Color(0,17,0));
        clearBtn.setForeground(Color.GREEN);
        clearBtn.setFocusPainted(false);
        clearBtn.addActionListener(e -> clearAll());
        control.add(Box.createHorizontalStrut(10));
        control.add(clearBtn);

        mainPanel.add(control, BorderLayout.SOUTH);

        // CRT Overlay
        crtOverlay = new JLabel();
        crtOverlay.setOpaque(false);
        crtOverlay.setBackground(new Color(0, 50, 0, 20));

        setLayout(new BorderLayout());
        add(mainPanel, BorderLayout.CENTER);
        add(crtOverlay, BorderLayout.CENTER);
    }

    private void applyCrtStyle() {
        UIManager.put("Table.gridColor", new Color(0, 51, 0));
        UIManager.put("Table.background", Color.BLACK);
        UIManager.put("Table.foreground", new Color(0,255,0));
        UIManager.put("Table.selectionBackground", new Color(0,80,0));
        UIManager.put("TableHeader.background", new Color(0,17,0));
        UIManager.put("TableHeader.foreground", new Color(0,255,128));

        updateScanlines();
    }

    private void updateScanlines() {
        int w = getWidth(), h = getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(new Color(0, 40, 0, 30));
        for (int y = 0; y < h; y += 4) {
            g2.drawLine(0, y, w, y);
        }
        g2.dispose();
        crtOverlay.setIcon(new ImageIcon(img));
    }

    private void startCrtFlicker() {
        Timer timer = new Timer(120, e -> {
            float alpha = (System.currentTimeMillis() / 500) % 2 == 0 ? 0.04f : 0.07f;
            crtOverlay.setBackground(new Color(0, 50, 0, (int)(alpha * 255)));
        });
        timer.start();
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        updateScanlines();
    }

    private void log(String msg, String level) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new Date());
        Color color = switch (level) {
            case "ALERT" -> Color.RED;
            case "OPEN"  -> new Color(0,255,128);
            case "WEB"   -> Color.YELLOW;
            default      -> new Color(0,255,0);
        };
        SwingUtilities.invokeLater(() -> {
            logArea.append(String.format("[%s] %s%n", time, msg));
            logArea.setForeground(color);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void startScan() {
        String input = urlField.getText().trim();
        if (input.isEmpty()) {
            log("NO TARGET", "ALERT");
            return;
        }

        executeBtn.setEnabled(false);
        new Thread(() -> scanTarget(input)).start();
    }

    private void scanTarget(String urlInput) {
        String urlStr = urlInput.startsWith("http") ? urlInput : "http://" + urlInput;
        String domain;
        try {
            URL url = new URL(urlStr);
            domain = url.getHost();
            if (domain.isEmpty()) throw new Exception();
        } catch (Exception ex) {
            log("INVALID URL", "ALERT");
            SwingUtilities.invokeLater(() -> executeBtn.setEnabled(true));
            return;
        }

        log("LOCK ON: " + urlStr, "INFO");
        log("DOMAIN: " + domain, "INFO");

        String ip;
        try {
            ip = InetAddress.getByName(domain).getHostAddress();
            log("IP: " + ip, "INFO");
        } catch (Exception ex) {
            log("DOMAIN NOT FOUND", "ALERT");
            SwingUtilities.invokeLater(() -> executeBtn.setEnabled(true));
            return;
        }

        TargetInfo info = new TargetInfo();
        int[] ports = {80, 443, 8080, 8443};

        for (int port : ports) {
            if (isPortOpen(ip, port, 3000)) {
                info.ports.add(port);
                log("PORT " + port + " OPEN", "OPEN");
                if (port == 80 || port == 443 || port == 8080 || port == 8443) {
                    fetchHttpInfo(domain, port, info);
                }
            }
        }

        results.put(domain, info);
        updateMatrix();
        updateAsciiMap();
        log("SCAN COMPLETE", "OPEN");
        SwingUtilities.invokeLater(() -> executeBtn.setEnabled(true));
    }

    private boolean isPortOpen(String ip, int port, int timeout) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), timeout);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void fetchHttpInfo(String domain, int port, TargetInfo info) {
        String protocol = (port == 443 || port == 8443) ? "https" : "http";
        String url = protocol + "://" + domain + (port != 80 && port != 443 ? ":" + port : "");

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setInstanceFollowRedirects(true);

            if (conn.getResponseCode() >= 400) return;

            String server = conn.getHeaderField("Server");
            if (server != null) info.server = server;

            // Title via Jsoup
            Document doc = Jsoup.connect(url).timeout(8000).get();
            String title = doc.title();
            if (title.length() > 45) title = title.substring(0, 45) + "...";
            if (info.title.equals("NO TITLE")) info.title = title;
            log("TITLE: \"" + title + "\"", "WEB");

            // Tech detection
            String body = doc.body().text().toLowerCase();
            if (conn.getHeaderField("X-Powered-By") != null) info.tech.add(conn.getHeaderField("X-Powered-By").split(",")[0]);
            if (body.contains("wordpress")) info.tech.add("WordPress");
            if (body.contains("react")) info.tech.add("React");

            // Security headers
            Set<String> required = Set.of("X-Frame-Options", "X-Content-Type-Options", "Strict-Transport-Security");
            Set<String> present = new HashSet<>();
            for (String h : required) {
                if (conn.getHeaderField(h) != null) present.add(h);
                else log((conn.getHeaderField(h) == null ? "SEC: " + h + " MISSING" : ""), conn.getHeaderField(h) == null ? "ALERT" : "INFO");
            }
            info.securityCount = present.size();

        } catch (Exception ex) {
            log("HTTP ERROR", "ALERT");
        }
    }

    private void updateMatrix() {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            for (Map.Entry<String, TargetInfo> entry : results.entrySet()) {
                String domain = entry.getKey();
                TargetInfo i = entry.getValue();
                String ports = i.ports.isEmpty() ? "CLOSED" : i.ports.stream().map(String::valueOf).collect(Collectors.joining(", "));
                String tech = i.tech.isEmpty() ? "UNKNOWN" : String.join(" | ", i.tech);
                tableModel.addRow(new Object[]{
                    domain,
                    ports,
                    i.title,
                    i.server + " | " + tech,
                    i.securityCount + "/3 SEC"
                });
            }
        });
    }

    private void updateAsciiMap() {
        StringBuilder sb = new StringBuilder();
        sb.append("        ┌──────────┐\n");
        sb.append("        │ INTERNET │\n");
        sb.append("        └──────────┘\n");
        sb.append("              │\n");

        int count = 0;
        for (Map.Entry<String, TargetInfo> e : results.entrySet()) {
            if (count++ >= 2) break;
            String tech = e.getValue().tech.isEmpty() ? "WEB" : e.getValue().tech.get(0);
            sb.append(String.format("              │           ◆ [%s] → %s\n", e.getKey(), tech));
        }
        while (count < 2) { sb.append("\n"); count++; }
        sb.append("        ◆ = WEB SERVER    → = DATA FLOW");

        mapArea.setText(sb.toString());
    }

    private void clearAll() {
        results.clear();
        tableModel.setRowCount(0);
        mapArea.setText("");
        logArea.setText("");
        log("MEMORY PURGED", "INFO");
    }

    // Data holder
    static class TargetInfo {
        List<Integer> ports = new ArrayList<>();
        String title = "NO TITLE";
        String server = "UNKNOWN";
        List<String> tech = new ArrayList<>();
        int securityCount = 0;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeel());
            } catch (Exception ignored) {}
            new Main().setVisible(true);
        });
    }
}