package rubiks.ipl;

import java.io.*;
import java.util.Random;

/**
 * @author Niels Drost
 *
 *         A rubik's cube.
 *
 */
public class Cube implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Enum representing axes
     */
    enum Axis {
        X, Y, Z
    }

    // indexes for sides of the cube

    public static final int TOP = 0;

    public static final int LEFT = 1;

    public static final int FRONT = 2;

    public static final int RIGHT = 3;

    public static final int BACK = 4;

    public static final int BOTTOM = 5;

    public static final int SIDES = 6; // # sizes of a cube

    // colors of elements

    public static final byte WHITE = 0; // value for white element

    public static final byte BLUE = 1; // value for blue element

    public static final byte ORANGE = 2; // value for orange element

    public static final byte GREEN = 3; // value for green element

    public static final byte RED = 4; // value for red element

    public static final byte YELLOW = 5; // value for yellow element

    private int size; // size of this cube

    /*
     * content of the cube. Data format is an array of sides, with all the
     * elements of each side as an array of bytes.
     *
     * expanded view of cube (with starting colors, top = white):
     *
     * W BOGR Y
     */
    private final byte[][] data;

    private int twists; // number of twists this cube is a result of

    private int bound; // bound, useful for limiting the search depth

    /**
     * Creates a "solved" cube of a given size
     *
     * @param size
     *            size of the cube (standard rubik's cube is 3x3x3).
     */
    public Cube(int size) {
        this.size = size;

        // init data arrays
        data = new byte[SIDES][size * size];

        twists = 0;
        bound = 0;

        // init state. side 0 should be white, side 1 is yellow, etc.
        for (byte side = 0; side < SIDES; side++) {
            for (int element = 0; element < size * size; element++) {
                data[side][element] = side;
            }
        }
    }

    /**
     * Creates a random cube by randomly twisting it.
     *
     * @param size
     *            size of the cube (standard rubik's cube is 3x3x3).
     * @param twists
     *            number of random twists.
     * @param seed
     *            seed to use for the random number generator. Using the same
     *            seed will make the cube generator deterministic.
     */
    public Cube(int size, int twists, long seed) {
        this(size);

        Random random = new Random(seed);

        // do some random twists
        for (int i = 0; i < twists; i++) {
            int axis = random.nextInt(3);
            // select a row ( 0 < row < size )
            int row = random.nextInt(size - 1) + 1;
            boolean direction = random.nextBoolean();

            switch (axis) {
            case 0:
                twistX(row, direction);
                break;
            case 1:
                twistY(row, direction);
                break;
            case 2:
                twistZ(row, direction);
                break;
            }
        }

        // set twists back to 0
        this.twists = 0;

        // just in case
        checkIfConsistent();

    }

    /**
     * Copy constructor.
     *
     * @param original
     *            original cube to copy
     */
    public Cube(Cube original) {
        this.size = original.size;
        this.twists = original.twists;
        this.bound = original.bound;

        // init data arrays
        data = new byte[SIDES][size * size];

        // init state.
        for (byte i = 0; i < SIDES; i++) {
            for (int j = 0; j < size * size; j++) {
                data[i][j] = original.data[i][j];
            }
        }

    }

    public Cube(String fileName) throws Exception {
        File file = new File(fileName);
        if (!file.exists()) {
            throw new Exception(fileName + "does not exist");
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(fileName));
            String sizeString = reader.readLine();

            try {
                this.size = Integer.parseInt(sizeString);
            } catch (NumberFormatException e) {
                throw new Exception(
                        "expected size at first line of file, got: "
                                + sizeString);
            }

            // init data arrays
            data = new byte[SIDES][size * size];

            for (int i = 0; i < SIDES; i++) {
                for (int x = 0; x < size; x++) {
                    String line = reader.readLine();

                    if (line.length() != size) {
                        throw new Exception("Expected line of size " + size
                                + ", got " + line);
                    }

                    for (int y = 0; y < size; y++) {
                        byte color = parseElement(line.charAt(y));
                        int elementIndex = element(x, y);

                        data[i][elementIndex] = color;
                    }

                }
            }
        } finally {
            if(reader != null) {
                reader.close();
            }
        }
    }

    /**
     * Copy contents of this cube into a given target.
     *
     * @param target
     *            target for data of this cube.
     */
    public void copyTo(Cube target) {
        target.size = size;
        target.twists = twists;
        target.bound = bound;

        // init state.
        for (byte i = 0; i < SIDES; i++) {
            System.arraycopy(data[i], 0, target.data[i], 0, data[i].length);
        }
    }

    /**
     * Returns the size of this cube. Default rubik's cube is of size 3
     *
     * @return the size of this cube.
     */
    public int getSize() {
        return size;
    }

    /**
     * Number of times this cube has been twisted
     *
     * @return the number of twists
     */
    public int getTwists() {
        return twists;
    }

    /**
     * Drop the number of twists
     *
     * @return the number of twists
     */
    public void dropTwists() {
        twists = 0;
    }


    /**
     * Get the bound of this cube. Not actually used in this class, but useful
     * in search algorithm.
     *
     * @return the current bound
     */
    public int getBound() {
        return bound;
    }

    /**
     * Set the bound of this cube. Not actually used in this class, but useful
     * in in search algorithm.
     *
     * @param bound
     *            the new bound
     */
    public void setBound(int bound) {
        this.bound = bound;
    }

    /**
     * Returns if this cube is solved or not.
     *
     * @return true if solved, false if not
     */
    public boolean isSolved() {
        for (byte i = 0; i < SIDES; i++) {
            // all elements must be the same color as first element
            byte color = data[i][0];
            for (int j = 1; j < size * size; j++) {
                if (data[i][j] != color) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Twists this cube in each way possible.
     *
     * @param cache
     *            cache used to get new cube objects from.
     *
     * @return all the possible children of this cube.
     */
    public Cube[] generateChildren(CubeCache cache) {
        // number of possible twists is constant for each size cube
        Cube[] result = new Cube[6 * (size - 1)];
        int next = 0;

        for (Axis axis : Axis.values()) {
            for (int row = 1; row < size; row++) {
                result[next++] = twist(axis, row, true, cache);
                result[next++] = twist(axis, row, false, cache);
            }
        }

        return result;
    }

    /**
     * Twist the cube, returning the result as a new cube.
     *
     * @param axis
     *            axis to turn cube on
     * @param row
     *            first row which to turn ( 0 < row < size )
     * @param direction
     *            direction to turn, either positive or negative
     * @param cache
     *            cache used to get new cube objects from.
     *
     * @return the resulting cube after twisting
     */
    public Cube twist(Axis axis, int row, boolean direction, CubeCache cache) {
        if (row <= 0) {
            throw new Error("first row to twist must be greater then zero");
        }

        if (row >= size) {
            throw new Error(
                    "first row to twist must be smaller then size of cube: "
                            + size);
        }

        Cube result = cache.get();
        this.copyTo(result);

        switch (axis) {
        case X:
            result.twistX(row, direction);
            break;
        case Y:
            result.twistY(row, direction);
            break;
        case Z:
            result.twistZ(row, direction);
            break;
        default:
            // should not happen, but you never know :)
            throw new Error("unknown axis in twist");
        }
        return result;
    }

    /**
     * Print cube.
     *
     * @param out
     *            stream to use as output
     */
    public void print(PrintStream out) {
        out.println("Cube of size " + size + ", solved = " + isSolved());
        // print top
        for (int row = 0; row < size; row++) {
            printSpaces(size * 3, out);
            for (int column = 0; column < size; column++) {
                printElement(TOP, row, column, out);
            }
            out.println();
        }

        // print left,front,right and back
        for (int row = 0; row < size; row++) {
            for (int side = LEFT; side <= BACK; side++) {
                for (int column = 0; column < size; column++) {
                    printElement(side, row, column, out);
                }
            }
            out.println();
        }

        // print bottom
        for (int row = 0; row < size; row++) {
            printSpaces(size * 3, out);
            for (int column = 0; column < size; column++) {
                printElement(BOTTOM, row, column, out);
            }
            out.println();
        }

    }

    // *** Only private functions ahead. No need to understand those ;) ***\\

    /**
     * Computes the "inverse" of a row or column (last = first, etc)
     *
     * @param value
     *            original value
     * @return inverse of value (last = first, etc)
     */
    private int inverse(int value) {
        return size - 1 - value;
    }

    /**
     * Computes the position into the data array for each side from the row and
     * column
     *
     * @param row
     *            row of element
     * @param column
     *            column of element
     * @return position in array of element
     */
    private int element(int row, int column) {
        return (row * size) + column;
    }

    /**
     * rotate side of a cube (side effect of twisting)
     *
     * @param side
     *            side to turn
     * @param direction
     *            direction to turn side in (positive or negative)
     */
    private void rotate(int side, boolean direction) {
        byte[] result = new byte[size * size];

        if (direction) {
            for (int row = 0; row < size; row++) {
                int toColumn = inverse(row);
                for (int column = 0; column < size; column++) {
                    int toRow = column;
                    // System.out.println(row + "," + column + " <= " + toRow
                    // + "," + toColumn);
                    result[(toRow * size) + toColumn] = data[side][(row * size)
                            + column];
                }
            }
        } else {
            for (int row = 0; row < size; row++) {
                int fromColumn = inverse(row);
                for (int column = 0; column < size; column++) {
                    int fromRow = column;
                    // System.out.println(row + "," + column + " <= " + fromRow
                    // + "," + fromColumn);
                    result[(row * size) + column] = data[side][(fromRow * size)
                            + fromColumn];
                }
            }
        }

        System.arraycopy(result, 0, data[side], 0, result.length);
    }

    /**
     * Twist cube on the X-axis.
     *
     * @param firstRow
     *            first row which to turn ( 0 < row < size )
     * @param direction
     *            direction to turn, either positive or negative
     */
    private void twistX(int firstRow, boolean direction) {
        // System.out.println("Twisting over X axis, first row " + firstRow
        // + " in positive direction: " + direction);

        int firstElement = firstRow * size;

        // warning: fiddling with cube content ahead :)
        for (int element = firstElement; element < size * size; element++) {

            if (direction) {
                byte tmp = data[BACK][element];
                data[BACK][element] = data[RIGHT][element];
                data[RIGHT][element] = data[FRONT][element];
                data[FRONT][element] = data[LEFT][element];
                data[LEFT][element] = tmp;
            } else {
                byte tmp = data[LEFT][element];
                data[LEFT][element] = data[FRONT][element];
                data[FRONT][element] = data[RIGHT][element];
                data[RIGHT][element] = data[BACK][element];
                data[BACK][element] = tmp;
            }
        }

        rotate(BOTTOM, direction);

        // record this twist
        twists++;
    }

    /**
     * Twist cube on the Y-axis.
     *
     * @param firstColumn
     *            first column which to turn ( 0 < column < size )
     * @param direction
     *            direction to turn, either positive or negative
     */
    private void twistY(int firstColumn, boolean direction) {
        // System.out.println("Twisting over Y axis, first column " +
        // firstColumn
        // + " in positive direction: " + direction);

        for (int row = 0; row < size; row++) {
            for (int column = firstColumn; column < size; column++) {
                int element = (row * size) + column;

                // back is "mirrored"
                int backRow = inverse(row);
                int backColumn = inverse(column);
                int backElement = (backRow * size) + backColumn;

                if (direction) {
                    byte tmp = data[TOP][element];
                    data[TOP][element] = data[FRONT][element];
                    data[FRONT][element] = data[BOTTOM][element];
                    data[BOTTOM][element] = data[BACK][backElement];
                    data[BACK][backElement] = tmp;
                } else {
                    byte tmp = data[TOP][element];
                    data[TOP][element] = data[BACK][backElement];
                    data[BACK][backElement] = data[BOTTOM][element];
                    data[BOTTOM][element] = data[FRONT][element];
                    data[FRONT][element] = tmp;
                }
            }
        }
        rotate(RIGHT, direction);

        // record this twist
        twists++;

    }

    /**
     * Twist cube on the Z-axis.
     *
     * @param firstRow
     *            first row which to turn ( 0 < row < size )
     * @param direction
     *            direction to turn, either positive or negative
     */
    private void twistZ(int firstRow, boolean direction) {
        // System.out.println("Twisting over Z axis, first row " + firstRow
        // + " in positive direction: " + direction);

        for (int row = firstRow; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int topRow = inverse(row);
                int topColumn = column;
                int topElement = element(topRow, topColumn);

                int rightRow = column;
                int rightColumn = row;
                int rightElement = element(rightRow, rightColumn);

                int bottomRow = row;
                int bottomColumn = inverse(column);
                int bottomElement = element(bottomRow, bottomColumn);

                int leftRow = inverse(column);
                int leftColumn = inverse(row);
                int leftElement = element(leftRow, leftColumn);

                if (direction) {
                    byte tmp = data[TOP][topElement];
                    data[TOP][topElement] = data[LEFT][leftElement];
                    data[LEFT][leftElement] = data[BOTTOM][bottomElement];
                    data[BOTTOM][bottomElement] = data[RIGHT][rightElement];
                    data[RIGHT][rightElement] = tmp;
                } else {
                    byte tmp = data[TOP][topElement];
                    data[TOP][topElement] = data[RIGHT][rightElement];
                    data[RIGHT][rightElement] = data[BOTTOM][bottomElement];
                    data[BOTTOM][bottomElement] = data[LEFT][leftElement];
                    data[LEFT][leftElement] = tmp;
                }
            }
        }

        rotate(BACK, !direction);

        // record this twist
        twists++;
    }

    /**
     * Checks if consistent. Not a very good check, as only the absence of
     * pieces is checked. If the cube is in a reachable state is not checked.
     *
     */
    private void checkIfConsistent() {
        int[] counts = new int[SIDES];

        for (int side = 0; side < SIDES; side++) {
            for (int element = 0; element < size * size; element++) {
                int color = data[side][element];

                counts[color]++;
            }
        }

        for (int i = 0; i < SIDES; i++) {
            if (counts[i] != size * size) {
                throw new Error("cube not consistent!");
            }
        }
    }

    private void printSpaces(int spaces, PrintStream out) {
        for (int i = 0; i < spaces; i++) {
            out.print(" ");
        }
    }

    private byte parseElement(char element) throws Exception {
        switch (element) {
        case 'W':
            return WHITE;
        case 'B':
            return BLUE;
        case 'O':
            return ORANGE;
        case 'G':
            return GREEN;
        case 'R':
            return RED;
        case 'Y':
            return YELLOW;
        }

        throw new Exception("Expected color code, got: " + element);
    }

    private void printElement(int side, int row, int column, PrintStream out) {
        // System.err.println("printing side = " + side + " row = " + row
        // + " column = " + column);

        byte color = data[side][(row * size) + column];

        switch (color) {
        case WHITE:
            out.print(" W ");
            break;
        case BLUE:
            out.print(" B ");
            break;
        case ORANGE:
            out.print(" O ");
            break;
        case GREEN:
            out.print(" G ");
            break;
        case RED:
            out.print(" R ");
            break;
        case YELLOW:
            out.print(" Y ");
            break;
        default:
            throw new Error("unknown color: " + color);
        }

    }
}
