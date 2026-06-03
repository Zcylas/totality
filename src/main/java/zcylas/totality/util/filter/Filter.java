package zcylas.totality.util.filter;

/**
 * A single-argument predicate with static {@link #and}, {@link #or}, and {@link #not} combinators.
 *
 * Ported from CreativeCore {@code Filter} (team.creative.creativecore).
 */
@FunctionalInterface
public interface Filter<T> {

    boolean is(T t);

    @SafeVarargs
    static <T> Filter<T> and(Filter<T>... filters) {
        return new FilterAnd<>(filters);
    }

    @SafeVarargs
    static <T> Filter<T> or(Filter<T>... filters) {
        return new FilterOr<>(filters);
    }

    static <T> Filter<T> not(Filter<T> filter) {
        return new FilterNot<>(filter);
    }

    record FilterNot<T>(Filter<T> filter) implements Filter<T> {
        @Override
        public boolean is(T t) {
            return !filter.is(t);
        }
    }

    record FilterAnd<T>(Filter<T>[] filters) implements Filter<T> {
        @Override
        public boolean is(T t) {
            for (Filter<T> f : filters)
                if (!f.is(t)) return false;
            return true;
        }
    }

    record FilterOr<T>(Filter<T>[] filters) implements Filter<T> {
        @Override
        public boolean is(T t) {
            for (Filter<T> f : filters)
                if (f.is(t)) return true;
            return false;
        }
    }
}