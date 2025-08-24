import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class DungeonHunterParallel {

    private static final boolean DEBUG = false;
    private static final int SEQ_CUTOFF = 256; // tune if needed

    private static long t0, t1;
    private static void tick(){ t0 = System.currentTimeMillis(); }
    private static void tock(){ t1 = System.currentTimeMillis(); }

    static final class Result {
        final int bestVal;
        final int bestIndex;
        Result(int bestVal, int bestIndex){ this.bestVal = bestVal; this.bestIndex = bestIndex; }
        static Result better(Result a, Result b){ return (a.bestVal >= b.bestVal) ? a : b; }
    }

    /** Divide-and-conquer with explicit fork/join. */
    static final class HuntRangeTask extends RecursiveTask<Result> {
        private final HuntParallel[] hunts;
        private final int lo, hi;

        HuntRangeTask(HuntParallel[] hunts, int lo, int hi) {
            this.hunts = hunts; this.lo = lo; this.hi = hi;
        }

        @Override protected Result compute() {
            int len = hi - lo;
            if (len <= SEQ_CUTOFF) {
                int bestVal = Integer.MIN_VALUE, bestIdx = -1;
                for (int i = lo; i < hi; i++) {
                    int val = hunts[i].findManaPeak(DEBUG);
                    if (val > bestVal) { bestVal = val; bestIdx = i; }
                }
                return new Result(bestVal, bestIdx);
            }
            int mid = lo + (len >> 1);
            HuntRangeTask left = new HuntRangeTask(hunts, lo, mid);
            HuntRangeTask right = new HuntRangeTask(hunts, mid, hi);

            left.fork();                   // explicit fork
            Result rRight = right.compute(); // compute right
            Result rLeft  = left.join();     // join left
            return Result.better(rLeft, rRight);
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Incorrect number of command line arguments provided.");
            System.exit(0);
        }

        int gateSize, seed; double density;
        try {
            gateSize = Integer.parseInt(args[0]);
            density  = Double.parseDouble(args[1]);
            seed     = Integer.parseInt(args[2]);
            if (gateSize <= 0) throw new IllegalArgumentException("Grid size must be greater than 0.");
            if (seed < 0) throw new IllegalArgumentException("Random seed must be non-negative.");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1); return;
        }

        double xmin = -gateSize, xmax = gateSize, ymin = -gateSize, ymax = gateSize;
        DungeonMapParallel dungeon = new DungeonMapParallel(xmin, xmax, ymin, ymax, seed);
        int rows = dungeon.getRows(), cols = dungeon.getColumns();

        // Match serial: Number searches = density * (2*gateSize)^2
        int numSearches = (int)(density * (gateSize * 2) * (gateSize * 2));

        // Deterministic starts
        Random rand = new Random(seed);
        HuntParallel[] hunts = new HuntParallel[numSearches];
        for (int i = 0; i < numSearches; i++) {
            int r = rand.nextInt(rows);
            int c = rand.nextInt(cols);
            hunts[i] = new HuntParallel(i, r, c, dungeon);
        }

        ForkJoinPool pool = new ForkJoinPool();

        tick();
        Result res = pool.invoke(new HuntRangeTask(hunts, 0, hunts.length));
        tock();

        // === Output (match serial formatting) ===
        System.out.printf("\t dungeon size: %d,\n", gateSize);
        System.out.printf("\t rows: %d, columns: %d\n", rows, cols);
        System.out.printf("\t x: [%f, %f], y: [%f, %f]\n", xmin, xmax, ymin, ymax);
        System.out.printf("\t Number searches: %d\n\n", numSearches);

        System.out.printf("\t time: %d ms\n", (t1 - t0));
        int eval = dungeon.getGridPointsEvaluated();
        double pct = (eval * 1.0) / (rows * cols) * 100.0;
        System.out.printf("\tnumber dungeon grid points evaluated: %d  (%.0f%%)\n", eval, pct);

        if (res != null && res.bestIndex >= 0) {
            HuntParallel winner = hunts[res.bestIndex];
            System.out.printf("Dungeon Master (mana %d) found at:  x=%.1f y=%.1f\n",
                    res.bestVal,
                    dungeon.getXcoord(winner.getRow()),
                    dungeon.getYcoord(winner.getCol()));
        } else {
            System.out.println("Dungeon Master (mana N/A) found at:  x=N/A y=N/A");
        }

        // Image output guarded to avoid noisy IO failures
        try {
            dungeon.visualisePowerMap("visualiseSearch.png", false);
            dungeon.visualisePowerMap("visualiseSearchPath.png", true);
        } catch (Exception ignored) {}
    }
}