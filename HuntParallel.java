public class HuntParallel {
    public enum Direction { STAY, LEFT, RIGHT, UP, DOWN, UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT }

    private final int id;
    private int row, col, steps;
    private final DungeonMapParallel dungeon;

    public HuntParallel(int id, int startRow, int startCol, DungeonMapParallel dungeon) {
        this.id = id; this.row = startRow; this.col = startCol; this.dungeon = dungeon;
        this.steps = 0;
    }

    /** Deterministic hill-climb: ignore 'visited' for stopping to avoid interleaving nondeterminism. */
    public int findManaPeak(boolean debug) {
        int val;
        while (true) {
            val = dungeon.getManaLevel(row, col);
            dungeon.setVisited(row, col, id); // viz only
            steps++;
            Direction dir = dungeon.getNextStepDirection(row, col);
            if (dir == Direction.STAY) return val;
            switch (dir) {
                case LEFT: row--; break;
                case RIGHT: row++; break;
                case UP: col--; break;
                case DOWN: col++; break;
                case UP_LEFT: row--; col--; break;
                case UP_RIGHT: row++; col--; break;
                case DOWN_LEFT: row--; col++; break;
                case DOWN_RIGHT: row++; col++; break;
                default: return val;
            }
        }
    }

    public int getRow(){ return row; }
    public int getCol(){ return col; }
}