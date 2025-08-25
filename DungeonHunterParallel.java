import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;


public class DungeonHunterParallel {

    private static final boolean DEBUG = false;   // Ttoggle debugging output
    private static final int SEQ_CUTOFF = 256;    // seq cutoff for Fork/Join 

    // timing
    private static long t0, t1;
    private static void tick(){ t0 = System.currentTimeMillis(); }
    private static void tock(){ t1 = System.currentTimeMillis(); }



    static final class Result {
        final int bestVal;    
        final int bestIndex;  
        Result(int bestVal, int bestIndex){ this.bestVal = bestVal; this.bestIndex = bestIndex; }
        
        // compare two results and return the higher one
        static Result better(Result a, Result b){ 
            return (a.bestVal >= b.bestVal) ? a : b; 
        }
    }

    static final class HuntRangeTask extends RecursiveTask<Result> {
        private final HuntParallel[] hunts;
        private final int lo, hi;

        HuntRangeTask(HuntParallel[] hunts, int lo, int hi) {
            this.hunts = hunts; 
            this.lo = lo; 
            this.hi = hi;
        }

        @Override 
        protected Result compute() {
            int len = hi - lo;

            // Base case -  solve seq if range is small
            if (len <= SEQ_CUTOFF) {
                int bestVal = Integer.MIN_VALUE, bestIdx = -1;
                for (int i = lo; i < hi; i++) {
                    int val = hunts[i].findManaPeak(DEBUG);
                    if (val > bestVal) { 
                        bestVal = val; 
                        bestIdx = i; 
                    }
                }
                return new Result(bestVal, bestIdx);
            }

            // Recursive case -  split into two
            int mid = lo + (len >> 1);
            HuntRangeTask left = new HuntRangeTask(hunts, lo, mid);
            HuntRangeTask right = new HuntRangeTask(hunts, mid, hi);

            // Fork left subtask, compute right subtask directly
            left.fork();                    
            Result rRight = right.compute(); 
            Result rLeft  = left.join();    

            // Combine 
            return Result.better(rLeft, rRight);
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Incorrect number of command line arguments provided.");
            System.exit(0);
        }

        int gateSize, seed; 
        double density;
        try {
            gateSize = Integer.parseInt(args[0]);   // Dungeon size
            density  = Double.parseDouble(args[1]); // search density factor
            seed     = Integer.parseInt(args[2]);   // Random
            
            if (gateSize <= 0) throw new IllegalArgumentException("Grid size must be greater than 0.");
            if (seed < 0) throw new IllegalArgumentException("Random seed must be non-negative.");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1); 
            return;
        }

        double xmin = -gateSize, xmax = gateSize, ymin = -gateSize, ymax = gateSize;
        DungeonMapParallel dungeon = new DungeonMapParallel(xmin, xmax, ymin, ymax, seed);
        int rows = dungeon.getRows(), cols = dungeon.getColumns();

        int numSearches = (int)(density * (gateSize * 2) * (gateSize * 2));

        Random rand = new Random(seed);
        HuntParallel[] hunts = new HuntParallel[numSearches];
        for (int i = 0; i < numSearches; i++) {
            int r = rand.nextInt(rows);
            int c = rand.nextInt(cols);
            hunts[i] = new HuntParallel(i, r, c, dungeon);
        }

        // parallel search
        ForkJoinPool pool = new ForkJoinPool();

        tick();
        Result res = pool.invoke(new HuntRangeTask(hunts, 0, hunts.length));
        tock();


        System.out.printf("\t dungeon size: %d,\n", gateSize);
        System.out.printf("\t rows: %d, columns: %d\n", rows, cols);
        System.out.printf("\t x: [%f, %f], y: [%f, %f]\n", xmin, xmax, ymin, ymax);
        System.out.printf("\t Number searches: %d\n\n", numSearches);

        // Execution time
        System.out.printf("\t time: %d ms\n", (t1 - t0));

        // % of grid 
        int eval = dungeon.getGridPointsEvaluated();
        double pct = (eval * 1.0) / (rows * cols) * 100.0;
        System.out.printf("\tnumber dungeon grid points evaluated: %d  (%.0f%%)\n", eval, pct);

        // best hunt
        if (res != null && res.bestIndex >= 0) {
            HuntParallel winner = hunts[res.bestIndex];
            System.out.printf("Dungeon Master (mana %d) found at:  x=%.1f y=%.1f\n",
                    res.bestVal,
                    dungeon.getXcoord(winner.getRow()),
                    dungeon.getYcoord(winner.getCol()));
        } else {
            System.out.println("Dungeon Master (mana N/A) found at:  x=N/A y=N/A");
        }

        // visualisations 
        try {
            dungeon.visualisePowerMap("visualiseSearch.png", false);
            dungeon.visualisePowerMap("visualiseSearchPath.png", true);
        } catch (Exception ignored) {}
    }
}