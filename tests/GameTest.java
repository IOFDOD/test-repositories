import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class GameTest {
    private static Field field(String name) throws Exception {
        Field f = MazeGame.class.getDeclaredField(name); f.setAccessible(true); return f;
    }
    public static void main(String[] args) throws Exception {
        // A radially farther sprite may still be closer to the camera plane.
        double[] nearOffAxis = {2, 2, 'C'};
        double[] farOnAxis = {2.5, 0, 'E'};
        java.util.List<double[]> sprites = new java.util.ArrayList<>();
        sprites.add(nearOffAxis); sprites.add(farOnAxis);
        MazeGame.sortSprites(sprites, 1, 0);
        if (sprites.get(0) != farOnAxis) throw new AssertionError("Sprites must be sorted by camera depth");
        MazeGame.Level level = MazeGame.Level.load(java.nio.file.Paths.get(args[0]));
        SwingUtilities.invokeAndWait(() -> {
            try {
                Constructor<MazeGame> constructor = MazeGame.class.getDeclaredConstructor(MazeGame.Level.class);
                constructor.setAccessible(true);
                MazeGame game = constructor.newInstance(level);
                ((Timer)field("timer").get(game)).stop();
                game.setSize(800, 600);
                BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics(); game.paint(graphics); graphics.dispose();
                if (image.getRGB(400, 300) == image.getRGB(400, 120)) throw new AssertionError("Rendering did not draw a wall");
                char[][] map = (char[][])field("map").get(game);
                for (int y = 0; y < map.length; y++) for (int x = 0; x < map[y].length; x++) {
                    if (map[y][x] == 'C') {
                        field("x").setDouble(game, x + .5); field("y").setDouble(game, y + .5);
                        game.actionPerformed(null);
                    }
                }
                if (field("remaining").getInt(game) != 0) throw new AssertionError("Crystals were not collected");
                for (int y = 0; y < map.length; y++) for (int x = 0; x < map[y].length; x++) {
                    if (map[y][x] == 'E') {
                        field("x").setDouble(game, x + .5); field("y").setDouble(game, y + .5);
                        game.actionPerformed(null);
                    }
                }
                if (!field("won").getBoolean(game)) throw new AssertionError("Exit did not win the game");
                System.out.println("Headless rendering, crystal collection and victory: OK");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
}
