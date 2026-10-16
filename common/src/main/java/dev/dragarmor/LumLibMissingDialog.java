package dev.dragarmor;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Friendly pre-launch prompt shown when this mod is loaded without LumLib. */
public final class LumLibMissingDialog {
    private static final Logger LOG = LoggerFactory.getLogger("dragarmor");
    private static final Color BG = new Color(15, 19, 28);
    private static final Color PANEL = new Color(23, 29, 41);
    private static final Color TEXT = new Color(240, 244, 251);
    private static final Color MUTED = new Color(158, 171, 191);
    private static final Color ACCENT = new Color(115, 151, 255);
    // "hashes":{..."sha512":"<128 hex>"...},"url":"https://cdn.modrinth.com/....jar"
    private static final Pattern FILE = Pattern.compile(
            "\"sha512\"\\s*:\\s*\"([0-9a-f]{128})\"[^}]*\\}\\s*,\\s*\"url\"\\s*:\\s*\"(https://cdn\\.modrinth\\.com/[^\"]+\\.jar)\"",
            Pattern.DOTALL);

    private static final String[] LANGS = {"ar_sa", "bg_bg", "cs_cz", "da_dk", "de_de", "el_gr", "en_us", "es_es",
            "fi_fi", "fr_fr", "he_il", "hu_hu", "id_id", "it_it", "ja_jp", "ko_kr", "nl_nl", "no_no", "pl_pl", "pt_br",
            "pt_pt", "ro_ro", "ru_ru", "sv_se", "th_th", "tr_tr", "uk_ua", "zh_cn"};
    private static final Pattern ENTRY = Pattern.compile("\"([^\"\\\\]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Map<String, String> TRANSLATIONS = new java.util.HashMap<>();
    private static boolean dialogFont;

    private LumLibMissingDialog() {}

    /** English text is built in; translations come from assets/dragarmor/lumlib_prompt/<locale>.json. */
    private static String tr(String key, String english) {
        return TRANSLATIONS.getOrDefault(key, english);
    }

    private static void loadLanguage() {
        Locale locale = Locale.getDefault();
        String lang = locale.getLanguage();
        switch (lang) {
            case "iw" -> lang = "he";
            case "in" -> lang = "id";
            case "nb", "nn" -> lang = "no";
            default -> { }
        }
        String exact = (lang + "_" + locale.getCountry()).toLowerCase(Locale.ROOT);
        String pick = null;
        for (String code : LANGS) {
            if (code.equals(exact)) { pick = code; break; }
        }
        if (pick == null) {
            for (String code : LANGS) {
                if (code.startsWith(lang + "_")) { pick = code; break; }
            }
        }
        if (pick == null) return;
        dialogFont = lang.equals("ja") || lang.equals("ko") || lang.equals("zh")
                || lang.equals("ar") || lang.equals("he") || lang.equals("th");
        try (java.io.InputStream in = LumLibMissingDialog.class
                .getResourceAsStream("/assets/dragarmor/lumlib_prompt/" + pick + ".json")) {
            if (in == null) return;
            Matcher m = ENTRY.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            while (m.find()) TRANSLATIONS.put(m.group(1), unescape(m.group(2)));
        } catch (Exception exception) {
            LOG.warn("Could not load the prompt translation", exception);
        }
    }

    private static String unescape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) { out.append(c); continue; }
            char n = s.charAt(++i);
            switch (n) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (i + 4 < s.length()) {
                        out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                }
                default -> out.append(n);
            }
        }
        return out.toString();
    }

    public static void checkAndShow(String modName) {
        if (FabricLoader.getInstance().isModLoaded("lumlib")) return;
        System.setProperty("java.awt.headless", "false");
        String version = FabricLoader.getInstance().getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("?");
        boolean shown = false;
        try {
            SwingUtilities.invokeAndWait(() -> show(modName, version));
            shown = true;
        } catch (Throwable exception) {
            LOG.warn("Could not show LumLib setup prompt", exception);
        }
        // LumLib is mandatory: however the window was closed, never continue without it.
        // (a jar downloaded in this session is only loaded after a restart)
        LOG.error("LumLib is required by {}. Closing the game.", modName);
        if (shown) {
            try {
                System.exit(0);
            } catch (Throwable ignored) {
            }
        }
        throw new IllegalStateException("LumLib is required by " + modName
                + ". Install it from https://modrinth.com/mod/lumlib and restart the game.");
    }

    private static void show(String modName, String gameVersion) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}

        int width = 520, height = 402;
        loadLanguage();
        Font regular = preferredFont(Font.PLAIN, 13f);
        Font medium = preferredFont(Font.BOLD, 13f);
        Font title = preferredFont(Font.BOLD, 24f);
        String downloadText = tr("download", "Download LumLib");
        String directText = tr("direct", "Download directly to the mods folder");
        String url = "https://modrinth.com/mod/lumlib/versions?g=" + gameVersion;

        JDialog dialog = new JDialog((Frame) null, modName, true);
        dialog.setUndecorated(true);
        dialog.setSize(width, height);
        dialog.setLocationRelativeTo(null);
        dialog.setBackground(BG);

        JPanel root = new JPanel(null) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setPaint(new GradientPaint(0, 0, new Color(29, 39, 59), width, height, BG));
                g.fillRoundRect(0, 0, width, height, 22, 22);
                g.setColor(new Color(255, 255, 255, 24));
                g.drawRoundRect(0, 0, width - 1, height - 1, 22, 22);
                g.setPaint(new GradientPaint(20, 0, new Color(116, 160, 255), width - 20, 0,
                        new Color(147, 111, 255)));
                g.fillRoundRect(24, 0, width - 48, 4, 4, 4);
                g.dispose();
            }
        };
        root.setOpaque(false);

        JPanel header = new JPanel(null);
        header.setOpaque(false);
        header.setBounds(0, 0, width, 82);
        root.add(header);

        JComponent mark = logoMark();
        mark.setBounds(26, 14, 54, 54);
        header.add(mark);

        JLabel app = new JLabel(modName);
        app.setFont(title);
        app.setForeground(TEXT);
        app.setBounds(92, 20, 360, 34);
        header.add(app);

        JLabel eyebrow = new JLabel(tr("eyebrow", "A SMALL LIBRARY IS MISSING"));
        eyebrow.setFont(preferredFont(Font.BOLD, 10f));
        eyebrow.setForeground(new Color(159, 183, 255));
        eyebrow.setBounds(94, 53, 300, 17);
        header.add(eyebrow);

        JButton close = new JButton("×");
        close.setFont(preferredFont(Font.PLAIN, 23f));
        close.setForeground(MUTED);
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setFocusPainted(false);
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.setBounds(width - 47, 18, 30, 30);
        close.addActionListener(event -> dialog.dispose());
        header.add(close);

        JPanel card = new JPanel(null);
        card.setBackground(PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 22)),
                BorderFactory.createEmptyBorder(18, 20, 16, 20)));
        card.setBounds(26, 94, width - 52, 154);
        root.add(card);

        JLabel required = new JLabel(tr("required", "REQUIRED BY THIS MOD"));
        required.setFont(preferredFont(Font.BOLD, 10f));
        required.setForeground(MUTED);
        required.setBounds(20, 15, 250, 18);
        card.add(required);

        JLabel library = new JLabel("LumLib");
        library.setFont(preferredFont(Font.BOLD, 20f));
        library.setForeground(TEXT);
        library.setBounds(20, 38, 240, 28);
        card.add(library);

        JLabel explanation = new JLabel(tr("explanation", "Provides the settings screen used to configure this mod."));
        explanation.setFont(regular);
        explanation.setForeground(MUTED);
        explanation.setBounds(20, 73, 420, 22);
        card.add(explanation);

        JLabel version = new JLabel("Minecraft " + gameVersion);
        version.setFont(preferredFont(Font.BOLD, 11f));
        version.setForeground(new Color(197, 210, 232));
        version.setOpaque(true);
        version.setBackground(new Color(37, 47, 65));
        version.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        version.setBounds(20, 108, 132, 27);
        card.add(version);

        JLabel source = new JLabel(tr("source", "Available on Modrinth"));
        source.setFont(preferredFont(Font.PLAIN, 11f));
        source.setForeground(new Color(135, 151, 178));
        source.setBounds(164, 111, 220, 20);
        card.add(source);

        RoundedButton download = new RoundedButton(downloadText, true, medium);
        download.setBounds(26, 262, width - 52, 44);
        download.addActionListener(event -> openSite(url));
        root.add(download);

        RoundedButton direct = new RoundedButton(directText, false, medium);
        direct.setBounds(26, 314, width - 52, 38);
        root.add(direct);

        JLabel status = new JLabel(" ", SwingConstants.CENTER);
        status.setFont(preferredFont(Font.PLAIN, 11f));
        status.setForeground(MUTED);
        status.setBounds(26, 358, width - 52, 18);
        root.add(status);

        direct.addActionListener(event -> downloadDirect(gameVersion, direct, status));

        JLabel continueText = new JLabel(tr("closing", "LumLib is required — the game will close."), SwingConstants.CENTER);
        continueText.setFont(preferredFont(Font.PLAIN, 11f));
        continueText.setForeground(MUTED);
        continueText.setBounds(0, 378, width, 20);
        root.add(continueText);

        Point[] drag = {null};
        header.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { drag[0] = event.getLocationOnScreen(); }
            @Override public void mouseReleased(MouseEvent event) { drag[0] = null; }
        });
        header.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseDragged(MouseEvent event) {
                if (drag[0] == null) return;
                Point next = event.getLocationOnScreen();
                Point location = dialog.getLocation();
                dialog.setLocation(location.x + next.x - drag[0].x, location.y + next.y - drag[0].y);
                drag[0] = next;
            }
        });

        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(download);
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setVisible(true);
    }

    private static JComponent logoMark() {
        Image image = null;
        try (java.io.InputStream in = LumLibMissingDialog.class.getResourceAsStream("/assets/dragarmor/lumlib_logo.png")) {
            if (in != null) image = javax.imageio.ImageIO.read(in);
        } catch (Exception ignored) {
        }
        if (image == null) {
            JLabel fallback = new JLabel("L", SwingConstants.CENTER);
            fallback.setFont(preferredFont(Font.BOLD, 23f));
            fallback.setForeground(Color.WHITE);
            fallback.setOpaque(true);
            fallback.setBackground(new Color(82, 116, 208));
            return fallback;
        }
        final Image logo = image;
        return new JComponent() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(logo, 0, 0, getWidth(), getHeight(), null);
                g.dispose();
            }
        };
    }

    private static void openSite(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception exception) {
            LOG.warn("Could not open the LumLib download page", exception);
        }
    }

    /**
     * Downloads the jar from Modrinth's official CDN (the same origin as the website's Download
     * button, so it counts as a regular download), verifies the SHA-512 and saves it to mods/.
     */
    private static void downloadDirect(String gameVersion, JButton button, JLabel status) {
        button.setEnabled(false);
        setStatus(status, tr("downloading", "Downloading..."), MUTED);
        Thread thread = new Thread(() -> {
            try {
                HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(10)).build();
                String api = "https://api.modrinth.com/v2/project/lumlib/version?loaders="
                        + URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8)
                        + "&game_versions=" + URLEncoder.encode("[\"" + gameVersion + "\"]", StandardCharsets.UTF_8);
                HttpResponse<String> list = http.send(request(api), HttpResponse.BodyHandlers.ofString());
                if (list.statusCode() != 200) throw new IllegalStateException("API HTTP " + list.statusCode());
                Matcher m = FILE.matcher(list.body());
                String sha = null, fileUrl = null;
                while (m.find()) {
                    String candidate = m.group(2);
                    if (candidate.contains("sources") || candidate.contains("-dev")) continue;
                    sha = m.group(1);
                    fileUrl = candidate;
                    break;
                }
                if (fileUrl == null) throw new IllegalStateException("no LumLib build for Minecraft " + gameVersion);
                HttpResponse<byte[]> file = http.send(request(fileUrl), HttpResponse.BodyHandlers.ofByteArray());
                if (file.statusCode() != 200) throw new IllegalStateException("CDN HTTP " + file.statusCode());
                String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(file.body()));
                if (!actual.equals(sha)) throw new IllegalStateException("SHA-512 mismatch");
                String name = URLDecoder.decode(fileUrl.substring(fileUrl.lastIndexOf('/') + 1), StandardCharsets.UTF_8);
                Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
                Files.createDirectories(mods);
                Path part = mods.resolve(name + ".part");
                Files.write(part, file.body());
                Files.move(part, mods.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                setStatus(status, tr("done", "Done! Restart the game to load LumLib."), new Color(120, 220, 150));
            } catch (Exception exception) {
                LOG.warn("LumLib direct download failed", exception);
                setStatus(status, tr("failed", "Failed. Use the button above (website)."), new Color(255, 130, 130));
                SwingUtilities.invokeLater(() -> button.setEnabled(true));
            }
        }, "lumlib-download");
        thread.setDaemon(true);
        thread.start();
    }

    private static HttpRequest request(String uri) {
        return HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(30))
                .header("User-Agent", "lumyrix/dragarmor (LumLib setup prompt)").GET().build();
    }

    private static void setStatus(JLabel label, String text, Color color) {
        SwingUtilities.invokeLater(() -> { label.setText(text); label.setForeground(color); });
    }

    private static Font preferredFont(int style, float size) {
        if (dialogFont) return new Font("Dialog", style, Math.round(size));
        for (String name : new String[]{"Inter", "Segoe UI", "Ubuntu", "Cantarell", "Noto Sans"}) {
            Font candidate = new Font(name, style, Math.round(size));
            if (!candidate.getFamily().equals("Dialog")) return candidate.deriveFont(style, size);
        }
        return new Font("Dialog", style, Math.round(size));
    }

    private static final class RoundedButton extends JButton {
        private final boolean primary;
        private boolean hovered;

        RoundedButton(String text, boolean primary, Font font) {
            super(text);
            this.primary = primary;
            setFont(font);
            setForeground(Color.WHITE);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { hovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent event) { hovered = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color top = primary ? (hovered ? new Color(132, 166, 255) : ACCENT)
                    : (hovered ? new Color(54, 65, 86) : new Color(42, 51, 68));
            Color bottom = primary ? new Color(90, 112, 218) : new Color(34, 42, 57);
            if (!isEnabled()) { top = new Color(42, 51, 68); bottom = new Color(34, 42, 57); }
            g.setPaint(new GradientPaint(0, 0, top, 0, getHeight(), bottom));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g.setFont(getFont());
            g.setColor(isEnabled() ? getForeground() : MUTED);
            FontMetrics fm = g.getFontMetrics();
            String text = getText();
            g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }
}
