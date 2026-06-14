package zcylas.totality.util.collection;

import java.util.ArrayList;

/**
 * A {@link SelectableList} that can be cycled forward with {@link #next()},
 * wrapping back to index 0 after the last element.
 *
 * <p>Ported from ResourcefulLib (TeamResourceful).</p>
 *
 * <p>Usage:
 * <pre>{@code
 * CycleableList<SideMode> modes = new CycleableList<>();
 * modes.add(SideMode.NONE);
 * modes.add(SideMode.INPUT);
 * modes.add(SideMode.OUTPUT);
 * modes.next(); // advances selection
 * }</pre>
 */
public class CycleableList<E> extends SelectableList<E> {

    public CycleableList() {
        super(null, new ArrayList<>());
    }

    /** Advance to the next element, wrapping to 0 after the last. */
    public void next() {
        setSelectedIndex(getSelectedIndex() + 1);
    }

    /** Step backwards, wrapping to last element from 0. */
    public void previous() {
        int prev = getSelectedIndex() - 1;
        setSelectedIndex(prev < 0 ? size() - 1 : prev);
    }
}
