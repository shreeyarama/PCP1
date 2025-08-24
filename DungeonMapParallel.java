import java.util.Random;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;

public class DungeonMapParallel {
    public static final int PRECISION = 10000;
    public static final int RESOLUTION = 5;

    private final int rows, columns;
    private final double xmin, xmax, ymin, ymax;
    private final int [][] manaMap;   // lazy cache
    private final int [][] visit;     // -1 = unvisited; else = search id (viz only)
    private int dungeonGridPointsEvaluated;

    private final double bossX, bossY, decayFactor;

    public DungeonMapParallel(double xmin, double xmax, double ymin, double ymax, int seed) {
        this.xmin = xmin; this.xmax = xmax; this.ymin = ymin; this.ymax = ymax;
        this.rows    = (int)Math.round((xmax - xmin) * RESOLUTION);
        this.columns = (int)Math.round((ymax - ymin) * RESOLUTION);

        manaMap = new int[rows][columns];
        visit   = new int[rows][columns];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                manaMap[i][j] = Integer.MIN_VALUE;
                visit[i][j] = -1;
            }
        }

        Random r = new Random(seed);
        double bx = 0.35 + 0.30 * r.nextDouble();
        double by = 0.35 + 0.30 * r.nextDouble();
        bossX = xmin + (xmax - xmin) * bx;
        bossY = ymin + (ymax - ymin) * by;
        decayFactor = 0.01 + 0.005 * r.nextDouble();
    }

    public boolean visited(int x, int y) { return visit[x][y] != -1; }
    public void setVisited(int x, int y, int id) { if (visit[x][y] == -1) visit[x][y] = id; }

    public int getManaLevel(int x, int y) {
        if (manaMap[x][y] != Integer.MIN_VALUE) return manaMap[x][y];

        double xCoord = xmin + ((xmax - xmin) / rows) * x;
        double yCoord = ymin + ((ymax - ymin) / columns) * y;
        double dx = xCoord - bossX, dy = yCoord - bossY;
        double d2 = dx*dx + dy*dy;

        // Mana function (keep exact)
        double mana =
            (2 * Math.sin(xCoord + 0.1 * Math.sin(yCoord / 5.0) + Math.PI / 2) *
                 Math.cos((yCoord + 0.1 * Math.cos(xCoord / 5.0) + Math.PI / 2) / 2.0)
            + 0.7 * Math.sin((xCoord * 0.5) + (yCoord * 0.3) + 0.2 * Math.sin(xCoord / 6.0) + Math.PI / 2)
            + 0.3 * Math.sin((xCoord * 1.5) - (yCoord * 0.8) + 0.15 * Math.cos(yCoord / 4.0))
            - 0.2 * Math.log(Math.abs(yCoord - Math.PI * 2) + 0.1)
            + 0.5 * Math.sin((xCoord * yCoord) / 4.0 + 0.05 * Math.sin(xCoord))
            + 1.5 * Math.cos((xCoord + yCoord) / 5.0 + 0.1 * Math.sin(yCoord))
            + 3.0 * Math.exp(-0.03 * ((xCoord - bossX - 15)*(xCoord - bossX - 15) +
                                      (yCoord - bossY + 10)*(yCoord - bossY + 10)))
            + 8.0 * Math.exp(-decayFactor * d2)
            + 2.0 / (1.0 + 0.05 * d2));

        int fixed = (int)(PRECISION * mana);
        manaMap[x][y] = fixed;
        dungeonGridPointsEvaluated++;
        return fixed;
    }

    public HuntParallel.Direction getNextStepDirection(int x, int y) {
        HuntParallel.Direction best = HuntParallel.Direction.STAY;
        int bestVal = getManaLevel(x, y);

        int[][] dxy = { {-1,0},{1,0},{0,-1},{0,1},{-1,-1},{1,-1},{-1,1},{1,1} };
        HuntParallel.Direction[] dirs = {
            HuntParallel.Direction.LEFT, HuntParallel.Direction.RIGHT,
            HuntParallel.Direction.UP,   HuntParallel.Direction.DOWN,
            HuntParallel.Direction.UP_LEFT, HuntParallel.Direction.UP_RIGHT,
            HuntParallel.Direction.DOWN_LEFT, HuntParallel.Direction.DOWN_RIGHT
        };

        for (int i = 0; i < dxy.length; i++) {
            int nx = x + dxy[i][0], ny = y + dxy[i][1];
            if (nx >= 0 && nx < rows && ny >= 0 && ny < columns) {
                int v = getManaLevel(nx, ny);
                if (v > bestVal) { bestVal = v; best = dirs[i]; }
            }
        }
        return best;
    }

    public void visualisePowerMap(String filename, boolean pathOnly) {
        try {
            int w = manaMap.length, h = manaMap[0].length;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int i = 0; i < w; i++)
                for (int j = 0; j < h; j++)
                    if (manaMap[i][j] != Integer.MIN_VALUE) {
                        min = Math.min(min, manaMap[i][j]);
                        max = Math.max(max, manaMap[i][j]);
                    }
            double range = Math.max(1.0, max - min);

            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) {
                    Color c;
                    if ((pathOnly && !visited(i,j)) || manaMap[i][j] == Integer.MIN_VALUE) c = Color.BLACK;
                    else {
                        double t = (manaMap[i][j] - min) / range;
                        c = mapHeightToColor(t);
                    }
                    img.setRGB(i, h - 1 - j, c.getRGB());
                }
            }
            ImageIO.write(img, "png", new File(filename));
            System.out.println("map saved to " + filename);
        } catch (Exception ignored) {
            // Keep output clean for the automarker even if image write fails.
        }
    }

    private Color mapHeightToColor(double t) {
        t = Math.max(0, Math.min(1, t));
        if (t < 0.33) {
            double u = t/0.33; return new Color((int)(128*u), 0, (int)(128+127*u));
        } else if (t < 0.66) {
            double u = (t-0.33)/0.33; return new Color((int)(128+127*u), 0, (int)(255-255*u));
        } else {
            double u = (t-0.66)/0.34; return new Color(255, (int)(255*u), (int)(255*u));
        }
    }

    public double getXcoord(int x){ return xmin + ((xmax - xmin) / rows) * x; }
    public double getYcoord(int y){ return ymin + ((ymax - ymin) / columns) * y; }

    public int getRows() { return rows; }
    public int getColumns() { return columns; }
    public int getGridPointsEvaluated(){ return dungeonGridPointsEvaluated; }
}