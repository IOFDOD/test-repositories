import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** A dependency-free ray casting game. Map coordinates are x = column, y = row. */
public final class MazeGame extends JPanel implements ActionListener, KeyListener {
    private static final long serialVersionUID = 1L;
    private static final int WIDTH = 800, HEIGHT = 600, STRIPE = 2;
    private static final double FOV = Math.PI / 3, RADIUS = 0.19;
    private final Level original;
    private char[][] map;
    private double x, y, angle;
    private int remaining;
    private boolean won;
    private final boolean[] keys = new boolean[256];
    private final BufferedImage frame = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    private final double[] depth = new double[WIDTH / STRIPE];
    private long lastTime;
    private final Timer timer;

    static final class Level {
        final char[][] cells;
        final int startX, startY, crystals;
        Level(char[][] cells, int startX, int startY, int crystals) {
            this.cells = cells; this.startX = startX; this.startY = startY; this.crystals = crystals;
        }
        static Level load(Path path) throws IOException {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.size() < 3 || lines.size() > 256 || lines.get(0).length() < 3 || lines.get(0).length() > 256)
                throw new IllegalArgumentException("Map dimensions must be between 3 and 256 cells.");
            int h = lines.size(), w = lines.get(0).length(), sx = -1, sy = -1, starts = 0, exits = 0, crystals = 0;
            char[][] cells = new char[h][w];
            for (int row = 0; row < h; row++) {
                if (lines.get(row).length() != w) throw new IllegalArgumentException("Map must be rectangular.");
                for (int col = 0; col < w; col++) {
                    char c = lines.get(row).charAt(col);
                    if ("#.SCE".indexOf(c) < 0) throw new IllegalArgumentException("Unknown map character: " + c);
                    if ((row == 0 || col == 0 || row == h - 1 || col == w - 1) && c != '#')
                        throw new IllegalArgumentException("Map must be enclosed by walls.");
                    cells[row][col] = c;
                    if (c == 'S') { starts++; sx = col; sy = row; }
                    if (c == 'E') exits++;
                    if (c == 'C') crystals++;
                }
            }
            if (starts != 1 || exits != 1 || crystals < 1) throw new IllegalArgumentException("Map needs one S, one E and at least one C.");
            boolean[][] visited = new boolean[h][w];
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(sy * w + sx); visited[sy][sx] = true;
            int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
            while (!queue.isEmpty()) {
                int cell = queue.remove(), cx = cell % w, cy = cell / w;
                for (int i = 0; i < 4; i++) {
                    int nx = cx + dx[i], ny = cy + dy[i];
                    if (nx >= 0 && ny >= 0 && nx < w && ny < h && !visited[ny][nx] && cells[ny][nx] != '#') {
                        visited[ny][nx] = true; queue.add(ny * w + nx);
                    }
                }
            }
            for (int row = 0; row < h; row++) for (int col = 0; col < w; col++)
                if ((cells[row][col] == 'C' || cells[row][col] == 'E') && !visited[row][col])
                    throw new IllegalArgumentException("All crystals and the exit must be reachable from S.");
            return new Level(cells, sx, sy, crystals);
        }
    }

    private MazeGame(Level level) {
        original = level;
        setPreferredSize(new Dimension(WIDTH, HEIGHT)); setFocusable(true); addKeyListener(this);
        // Prevent stuck keys when the window loses focus.
        addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent e) { Arrays.fill(keys, false); } });
        reset();
        timer = new Timer(16, this); timer.start();
    }
    private void reset() {
        map = new char[original.cells.length][];
        for (int i = 0; i < map.length; i++) map[i] = original.cells[i].clone();
        x = original.startX + .5; y = original.startY + .5; angle = 0;
        remaining = original.crystals; won = false; lastTime = System.nanoTime();
        Arrays.fill(keys, false);
    }
    private boolean wall(int cx, int cy) {
        return cy < 0 || cx < 0 || cy >= map.length || cx >= map[0].length || map[cy][cx] == '#';
    }
    private boolean clear(double px, double py) {
        return !wall((int)Math.floor(px - RADIUS), (int)Math.floor(py - RADIUS)) &&
               !wall((int)Math.floor(px + RADIUS), (int)Math.floor(py - RADIUS)) &&
               !wall((int)Math.floor(px - RADIUS), (int)Math.floor(py + RADIUS)) &&
               !wall((int)Math.floor(px + RADIUS), (int)Math.floor(py + RADIUS));
    }
    @Override public void actionPerformed(ActionEvent event) {
        long now = System.nanoTime(); double dt = Math.min((now - lastTime) / 1e9, 0.05); lastTime = now;
        if (!won) {
            int turn = (pressed(KeyEvent.VK_RIGHT) ? 1 : 0) - (pressed(KeyEvent.VK_LEFT) ? 1 : 0);
            angle += turn * 2.2 * dt;
            double forward = (pressed(KeyEvent.VK_W) || pressed(KeyEvent.VK_UP) ? 1 : 0) - (pressed(KeyEvent.VK_S) || pressed(KeyEvent.VK_DOWN) ? 1 : 0);
            double side = (pressed(KeyEvent.VK_D) ? 1 : 0) - (pressed(KeyEvent.VK_A) ? 1 : 0);
            double length = Math.hypot(forward, side);
            if (length > 0) {
                double step = 2.5 * dt / Math.max(1, length);
                double mx = (Math.cos(angle) * forward - Math.sin(angle) * side) * step;
                double my = (Math.sin(angle) * forward + Math.cos(angle) * side) * step;
                if (clear(x + mx, y)) x += mx;
                if (clear(x, y + my)) y += my;
            }
            int cx = (int)x, cy = (int)y;
            if (map[cy][cx] == 'C') { map[cy][cx] = '.'; remaining--; }
            if (map[cy][cx] == 'E' && remaining == 0) won = true;
        }
        repaint();
    }
    private boolean pressed(int code) { return code < keys.length && keys[code]; }

    static void sortSprites(List<double[]> sprites, double dirX, double dirY) {
        // Painter's order must use camera depth, not radial distance.
        sprites.sort(Comparator.comparingDouble((double[] s) -> s[0] * dirX + s[1] * dirY).reversed());
    }

    private void render() {
        Graphics2D g = frame.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(12, 18, 40), 0, HEIGHT / 2, new Color(54, 74, 99)));
        g.fillRect(0, 0, WIDTH, HEIGHT / 2);
        g.setPaint(new GradientPaint(0, HEIGHT / 2, new Color(44, 49, 58), 0, HEIGHT, new Color(13, 17, 25)));
        g.fillRect(0, HEIGHT / 2, WIDTH, HEIGHT / 2);
        double dirX = Math.cos(angle), dirY = Math.sin(angle), plane = Math.tan(FOV / 2);
        double planeX = -dirY * plane, planeY = dirX * plane;
        for (int column = 0; column < depth.length; column++) {
            double cameraX = 2.0 * (column + .5) / depth.length - 1;
            double rayX = dirX + planeX * cameraX, rayY = dirY + planeY * cameraX;
            int mapX = (int)x, mapY = (int)y;
            double deltaX = rayX == 0 ? 1e30 : Math.abs(1 / rayX), deltaY = rayY == 0 ? 1e30 : Math.abs(1 / rayY);
            int stepX = rayX < 0 ? -1 : 1, stepY = rayY < 0 ? -1 : 1;
            double sideX = (rayX < 0 ? x - mapX : mapX + 1 - x) * deltaX;
            double sideY = (rayY < 0 ? y - mapY : mapY + 1 - y) * deltaY;
            boolean side = false;
            while (!wall(mapX, mapY)) {
                if (sideX < sideY) { sideX += deltaX; mapX += stepX; side = false; }
                else { sideY += deltaY; mapY += stepY; side = true; }
            }
            double distance = Math.max(.01, side ? sideY - deltaY : sideX - deltaX);
            depth[column] = distance;
            int height = Math.min(HEIGHT * 4, (int)(HEIGHT / distance));
            double hit = side ? x + distance * rayX : y + distance * rayY;
            double texture = hit - Math.floor(hit);
            double shade = Math.max(.18, 1 / (1 + distance * .13)) * (side ? .72 : 1) * (texture < .035 || texture > .965 ? .62 : 1);
            g.setColor(new Color((int)(84 * shade), (int)(153 * shade), (int)(165 * shade)));
            g.fillRect(column * STRIPE, (HEIGHT - height) / 2, STRIPE, height);
        }
        // Project collectibles and exit using the same camera basis as walls; depth test each stripe.
        List<double[]> sprites = new ArrayList<>();
        for (int row = 0; row < map.length; row++) for (int col = 0; col < map[row].length; col++) {
            if (map[row][col] == 'C' || map[row][col] == 'E') {
                double sx = col + .5 - x, sy = row + .5 - y;
                sprites.add(new double[]{sx, sy, map[row][col]});
            }
        }
        sortSprites(sprites, dirX, dirY);
        for (double[] s : sprites) {
            double inv = 1.0 / (planeX * dirY - dirX * planeY);
            double tx = inv * (dirY * s[0] - dirX * s[1]);
            double ty = inv * (-planeY * s[0] + planeX * s[1]);
            if (ty <= .05) continue;
            int center = (int)(WIDTH / 2.0 * (1 + tx / ty));
            int size = Math.min(HEIGHT * 3, (int)(HEIGHT / ty * .42));
            int left = Math.max(0, (center - size / 2) / STRIPE), right = Math.min(depth.length - 1, (center + size / 2) / STRIPE);
            boolean crystal = s[2] == 'C';
            for (int col = left; col <= right; col++) {
                if (ty >= depth[col]) continue;
                double u = Math.abs((col * STRIPE - center) / (size / 2.0));
                if (u > 1 || size < 1) continue;
                int half = crystal ? (int)(size * .5 * (1 - u)) : (int)(size * .65);
                g.setColor(crystal ? new Color(100, 245, 240) : (remaining == 0 ? new Color(125, 255, 150) : new Color(255, 179, 90)));
                g.fillRect(col * STRIPE, HEIGHT / 2 - half, STRIPE, half * 2);
            }
        }
        g.dispose();
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics); render();
        Graphics2D g = (Graphics2D)graphics.create();
        g.drawImage(frame, 0, 0, getWidth(), getHeight(), null);
        g.scale(getWidth() / (double)WIDTH, getHeight() / (double)HEIGHT);
        g.setColor(new Color(5, 10, 20, 185)); g.fillRoundRect(18, 18, 410, 76, 14, 14);
        g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.drawString("LABYRINTH  /  Кристаллы: " + (original.crystals - remaining) + " / " + original.crystals, 32, 48);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString(remaining == 0 ? "Найдите зелёный выход" : "Соберите кристаллы и найдите выход", 32, 74);
        g.setColor(new Color(5, 10, 20, 180)); g.fillRoundRect(18, HEIGHT - 47, 525, 29, 10, 10);
        g.setColor(Color.WHITE); g.drawString("WASD — движение   ← → — поворот   R — заново   Esc — выход", 29, HEIGHT - 27);
        if (won) {
            g.setColor(new Color(4, 10, 20, 215)); g.fillRoundRect(190, 220, 420, 145, 18, 18);
            g.setColor(new Color(130, 255, 190)); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
            g.drawString("Лабиринт пройден!", 237, 278);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18)); g.setColor(Color.WHITE); g.drawString("Нажмите R, чтобы сыграть снова", 255, 320);
        }
        g.dispose();
    }
    @Override public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode(); if (code < keys.length) keys[code] = true;
        if (code == KeyEvent.VK_R) reset();
        if (code == KeyEvent.VK_ESCAPE) { timer.stop(); SwingUtilities.getWindowAncestor(this).dispose(); }
    }
    @Override public void keyReleased(KeyEvent e) { int code = e.getKeyCode(); if (code < keys.length) keys[code] = false; }
    @Override public void keyTyped(KeyEvent e) { }

    public static void main(String[] args) {
        if (args.length != 1 && !(args.length == 2 && args[0].equals("--check"))) {
            System.err.println("Usage: java MazeGame [--check] <level.map>"); System.exit(1);
        }
        boolean check = args.length == 2;
        try {
            Level level = Level.load(Paths.get(args[check ? 1 : 0]));
            if (check) { System.out.println("Map OK: " + level.cells[0].length + "x" + level.cells.length + ", crystals: " + level.crystals); return; }
            if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("A graphical desktop is required to play. Use --check to validate a map.");
            SwingUtilities.invokeLater(() -> {
                JFrame window = new JFrame("Labyrinth 3D"); window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                MazeGame game = new MazeGame(level); window.setContentPane(game); window.pack();
                window.setLocationRelativeTo(null); window.setVisible(true); game.requestFocusInWindow();
            });
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            System.err.println("Error: " + e.getMessage()); System.exit(1);
        }
    }
}
