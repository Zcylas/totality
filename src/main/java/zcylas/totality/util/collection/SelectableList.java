package zcylas.totality.util.collection;

import com.google.common.collect.Lists;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;

/**
 * A {@link List} that tracks a selected index and provides {@link #getSelected()}.
 * Out-of-bounds access returns the configured default value instead of throwing.
 *
 * <p>Ported from ResourcefulLib (TeamResourceful).</p>
 *
 * <p>Usage:
 * <pre>{@code
 * SelectableList<String> tabs = SelectableList.of(null, "Overview", "Stats", "Skills");
 * tabs.setSelectedIndex(1);
 * String active = tabs.getSelected(); // "Stats"
 * }</pre>
 */
public class SelectableList<E> extends AbstractList<E> {

    protected @Nullable E defaultValue;
    protected List<E> list;
    protected int index = 0;

    @SafeVarargs
    public static <E> SelectableList<E> of(@Nullable E defaultValue, E... elements) {
        return new SelectableList<>(defaultValue, Lists.newArrayList(elements));
    }

    public SelectableList(@Nullable E defaultValue, List<E> list) {
        this.defaultValue = defaultValue;
        this.list = list;
    }

    /** Set the selected index, clamping to [0, size-1] and wrapping if &ge; size. */
    public void setSelectedIndex(int index) {
        if (index >= size()) this.index = 0;
        else this.index = Math.max(index, 0);
    }

    /** @return the currently selected element, or the default value if the list is empty. */
    public @Nullable E getSelected() {
        return get(this.index);
    }

    public int getSelectedIndex() {
        return this.index;
    }

    @Override
    public void add(int index, E element) {
        Objects.requireNonNull(element);
        list.add(index, element);
    }

    @Override
    public @Nullable E get(int index) {
        if (index < 0 || index >= size()) return defaultValue;
        E item = list.get(index);
        return item == null ? defaultValue : item;
    }

    @Override
    public E set(int index, E value) {
        Objects.requireNonNull(value);
        return this.list.set(index, value);
    }

    @Override
    public E remove(int index) {
        if (index == this.index) this.index = 0;
        return list.remove(index);
    }

    @Override
    public int size() {
        return list.size();
    }
}
