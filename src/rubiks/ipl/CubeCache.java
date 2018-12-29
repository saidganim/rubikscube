package rubiks.ipl;

/**
 * Cache for Cube objects. Using this cache instead of "new Cube()" for every
 * move is more efficient. Creating lots and lots of small objects continuously
 * overloads the garbage collector.
 *
 * @author Niels Drost, Timo van Kessel
 *
 */
public class CubeCache {

    public static final int MAX_CACHE_SIZE = 10 * 1024;

    private final int cubeSize;

    private final Cube[] cache;

    private int currentSize;


    /**
     * Constructor.
     *
     * @param cacheSize size of the cache.
     * @param cubeSize size of the cubes in the cache.
     */
    public CubeCache(int cubeSize) {
        this.cubeSize = cubeSize;
        cache = new Cube[MAX_CACHE_SIZE];
        currentSize = 0;
    }

    /**
     * Add given cube to the cache.
     *
     * @param cube cube to add to the cache
     */
    public void put(Cube cube) {
        if (currentSize >= cache.length) {
            // cache full
            return;
        }
        cache[currentSize] = cube;
        currentSize++;
    }

    /**
     * Get a cube from the cache.
     *
     * @return a cube from the cache
     */
    public Cube get() {
        if (currentSize == 0) {
            return new Cube(cubeSize);
        }

        currentSize--;
        return cache[currentSize];
    }

}
