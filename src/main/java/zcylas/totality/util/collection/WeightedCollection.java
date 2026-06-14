package zcylas.totality.util.collection;

import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 * A weighted random collection backed by a {@link NavigableMap}.
 * Each element is assigned a weight; {@link #next()} draws a random element
 * proportional to that weight.
 *
 * <p>Ported from ResourcefulLib (TeamResourceful). ByteCodec removed.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * WeightedCollection<String> pool = new WeightedCollection<>();
 * pool.add(10, "common");
 * pool.add(3,  "rare");
 * pool.add(1,  "legendary");
 * String result = pool.next(level.random);
 * }</pre>
 */
public class WeightedCollection<E> implements Collection<E> {

    private final NavigableMap<Double, E> map = new TreeMap<>();
    private WeightedRandom random;
    private double total = 0;

    public WeightedCollection() {
        this(new Random());
    }

    public WeightedCollection(Random random) {
        this(random::nextDouble);
    }

    public WeightedCollection(RandomSource random) {
        this(random::nextDouble);
    }

    public WeightedCollection(WeightedRandom random) {
        this.random = random;
    }

    // -----------------------------------------------------------------------
    // Mutation
    // -----------------------------------------------------------------------

    /** Add an element with the given weight. Weights &lt;= 0 are ignored. */
    public WeightedCollection<E> add(double weight, E result) {
        if (weight <= 0) return this;
        total += weight;
        map.put(Math.min(total, Double.MAX_VALUE), result);
        return this;
    }

    // -----------------------------------------------------------------------
    // Access
    // -----------------------------------------------------------------------

    /** Get the element at a specific insertion index (0-based, insertion order). */
    public E get(int index) {
        Double key = new LinkedList<>(map.keySet()).get(index);
        return map.get(key);
    }

    /** Draw a random element weighted by each element's weight. */
    public E next() {
        double value = random.nextDouble() * total;
        return map.higherEntry(value).getValue();
    }

    /** Convenience overload — use the provided {@link RandomSource} for this draw only. */
    public E next(RandomSource source) {
        double value = source.nextDouble() * total;
        return map.higherEntry(value).getValue();
    }

    public NavigableMap<Double, E> getMap() {
        return this.map;
    }

    public double getTotal() {
        return this.total;
    }

    /** Returns what fraction of the total weight this element's weight represents. */
    public double getAdjustedWeight(double weight) {
        return weight / total;
    }

    public void forEachWithSelf(BiConsumer<WeightedCollection<E>, ? super E> action) {
        forEach(element -> action.accept(this, element));
    }

    // -----------------------------------------------------------------------
    // Random setters
    // -----------------------------------------------------------------------

    public void setRandom(WeightedRandom random) {
        this.random = random;
    }

    public void setRandom(Random random) {
        setRandom(random::nextDouble);
    }

    public void setRandom(RandomSource random) {
        setRandom(random::nextDouble);
    }

    // -----------------------------------------------------------------------
    // Collection
    // -----------------------------------------------------------------------

    @Override
    public Stream<E> stream() {
        return map.values().stream();
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        try {
            return map.containsValue(o);
        } catch (Exception e) {
            return false;
        }
    }

    @NotNull
    @Override
    public Iterator<E> iterator() {
        return map.values().iterator();
    }

    @Override
    public Object @NotNull [] toArray() {
        return map.values().toArray();
    }

    @Override
    public <T> T @NotNull [] toArray(T @NotNull [] a) {
        return map.values().toArray(a);
    }

    @Override
    public boolean add(E e) {
        throw new UnsupportedOperationException("Use add(double weight, E element) instead.");
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("Removal is not supported.");
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        for (Object o : c) if (!contains(o)) return false;
        return true;
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends E> c) {
        throw new UnsupportedOperationException("Use add(double weight, E element) instead.");
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        throw new UnsupportedOperationException("Removal is not supported.");
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        throw new UnsupportedOperationException("Removal is not supported.");
    }

    @Override
    public void clear() {
        map.clear();
        total = 0;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        return o instanceof WeightedCollection && map.equals(((WeightedCollection<?>) o).map);
    }

    @Override
    public int hashCode() {
        return Objects.hash(total, map);
    }

    // -----------------------------------------------------------------------
    // Static factories
    // -----------------------------------------------------------------------

    public static <T> WeightedCollection<T> of(Collection<T> collection, ToDoubleFunction<T> weightGetter) {
        return collection.stream().collect(getCollector(weightGetter));
    }

    public static <T> Collector<T, ?, WeightedCollection<T>> getCollector(ToDoubleFunction<T> weightGetter) {
        return Collector.of(WeightedCollection::new, (c, t) -> c.add(weightGetter.applyAsDouble(t), t), (left, right) -> {
            left.forEach(item -> right.add(weightGetter.applyAsDouble(item), item));
            return right;
        });
    }

    // -----------------------------------------------------------------------
    // Functional interface
    // -----------------------------------------------------------------------

    @FunctionalInterface
    public interface WeightedRandom {
        double nextDouble();
    }
}
