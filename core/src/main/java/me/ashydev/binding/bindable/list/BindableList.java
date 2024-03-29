/*
 * Copyright (c) 2024 Ashley (uwuvern) <uwuvern@outlook.com>
 *
 * This project is licensed under the MIT license, check the root of the project for
 * more information.
 */

package me.ashydev.binding.bindable.list;

import me.ashydev.binding.IBindableList;
import me.ashydev.binding.IUnbindable;
import me.ashydev.binding.action.Action;
import me.ashydev.binding.action.ValuedAction;
import me.ashydev.binding.action.event.ValueChangedEvent;
import me.ashydev.binding.action.queue.ActionQueue;
import me.ashydev.binding.action.queue.ValuedActionQueue;
import me.ashydev.binding.bindable.Bindable;
import me.ashydev.binding.common.reference.LockedWeakList;
import me.ashydev.binding.event.collection.CollectionEvent;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class BindableList<T> implements IBindableList<T> {
    protected static <V> V source(V source, V self) {
        return source != null ? source : self;
    }

    private transient final WeakReference<BindableList<T>> weakReference = new WeakReference<>(this);

    private transient final ActionQueue<CollectionEvent<T>> collectionChanged = new ActionQueue<>();
    private transient final ValuedActionQueue<Boolean> disabledChanged = new ValuedActionQueue<>();

    private transient final LockedWeakList<BindableList<T>> bindings = new LockedWeakList<>();
    private final List<T> collection = new ArrayList<>();
    private transient boolean disabled;

    public BindableList(Collection<T> items) {
        if (items != null)
            collection.addAll(items);

        this.disabled = false;
    }

    public BindableList() {
        this(null);
    }

    protected void propagate(Action<BindableList<T>> propagation, BindableList<T> source) {
        Iterator<WeakReference<BindableList<T>>> iterator = bindings.iterator();

        while (iterator.hasNext()) {
            WeakReference<BindableList<T>> binding = iterator.next();

            if (binding.refersTo(source)) continue;

            BindableList<T> bindable = binding.get();

            if (bindable == null) {
                iterator.remove();

                continue;
            }

            propagation.accept(bindable);
        }
    }

    @Override
    public void onCollectionChanged(Action<CollectionEvent<T>> action, boolean runOnceImmediately) {
        collectionChanged.add(action);

        if (runOnceImmediately) {
            action.accept(
                    new CollectionEvent<>(CollectionEvent.Type.ADD,
                            collection.stream()
                                    .map(e -> new CollectionEvent.Element<>(e, collection.indexOf(e)))
                                    .toList(),
                            Collections.emptyList()
                    )
            );
        }
    }

    @Override
    public ActionQueue<CollectionEvent<T>> getCollectionChanged() {
        return collectionChanged;
    }

    @Override
    public T set(int index, T element) {
        return set(index, element, new HashSet<>());
    }

    protected T set(int index, T element, Set<BindableList<T>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances)) return null;

        ensureMutationAllowed();

        T previous = collection.set(index, element);

        propagate((BindableList<T> bindable) -> bindable.set(index, element, appliedInstances), this);

        collectionChanged.execute(
                new CollectionEvent<>(CollectionEvent.Type.REPLACE,
                        Collections.singletonList(
                                new CollectionEvent.Element<>(element, index)
                        ),
                        Collections.singletonList(
                                new CollectionEvent.Element<>(previous, index)
                        )
                )
        );

        return previous;
    }

    @Override
    public boolean add(T element) {
        return add(element, new HashSet<>());
    }

    protected boolean add(T element, Set<BindableList<T>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances)) return false;

        ensureMutationAllowed();

        collection.add(element);

        propagate((BindableList<T> bindable) -> bindable.add(element, appliedInstances), this);

        collectionChanged.execute(
                new CollectionEvent<>(CollectionEvent.Type.ADD,
                        Collections.singletonList(
                                new CollectionEvent.Element<>(element, collection.size() - 1)
                        ),
                        Collections.emptyList()
                )
        );

        return true;
    }

    @Override
    public void clear() {
        clear(new HashSet<>());
    }

    protected void clear(Set<BindableList<T>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances)) return;

        ensureMutationAllowed();

        List<T> oldCollection = new ArrayList<>(collection);

        collection.clear();

        propagate((BindableList<T> bindable) -> bindable.clear(appliedInstances), this);

        collectionChanged.execute(
                new CollectionEvent<>(CollectionEvent.Type.REMOVE,
                        Collections.emptyList(),
                        oldCollection.stream()
                                .map(e -> new CollectionEvent.Element<>(e, oldCollection.indexOf(e)))
                                .toList()
                )
        );
    }

    @Override
    public boolean remove(Object o) {
        return remove(o, new HashSet<>());
    }


    @SuppressWarnings("SuspiciousMethodCalls")
    protected boolean remove(Object o, Set<BindableList<T>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances)) return false;

        ensureMutationAllowed();

        int index = collection.indexOf(o);

        if (index == -1) return false;

        T removed = collection.remove(index);

        propagate((BindableList<T> bindable) -> bindable.remove(o, appliedInstances), this);

        collectionChanged.execute(
                new CollectionEvent<>(CollectionEvent.Type.REMOVE,
                        Collections.emptyList(),
                        Collections.singletonList(
                                new CollectionEvent.Element<>(removed, index)
                        )
                )
        );

        return true;
    }

    @Override
    public boolean contains(Object o) {
        return collection.contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        return collection.iterator();
    }

    @Override
    public Object[] toArray() {
        return collection.toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        return collection.toArray(a);
    }

    @Override
    public boolean containsAll(Collection<?> collection) {
        return new HashSet<>(this.collection).containsAll(collection);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        return addAll(c, new HashSet<>());
    }

    protected boolean addAll(Collection<? extends T> c, Set<BindableList<T>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances)) return false;

        ensureMutationAllowed();

        boolean changed = collection.addAll(c);

        propagate((BindableList<T> bindable) -> bindable.addAll(c, appliedInstances), this);

        collectionChanged.execute(
                new CollectionEvent<>(CollectionEvent.Type.ADD,
                        c.stream()
                                .map(e -> new CollectionEvent.Element<T>(e, collection.indexOf(e)))
                                .toList(),
                        Collections.emptyList()
                )
        );

        return changed;
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        return addAll(index, c, new HashSet<>());
    }

    protected boolean addAll(int index, Collection<? extends T> c, Set<BindableList<T>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances)) return false;

        ensureMutationAllowed();

        boolean changed = collection.addAll(index, c);

        propagate((BindableList<T> bindable) -> bindable.addAll(index, c, appliedInstances), this);

        collectionChanged.execute(
                new CollectionEvent<>(CollectionEvent.Type.ADD,
                        c.stream()
                                .map(e -> new CollectionEvent.Element<T>(e, collection.indexOf(e)))
                                .toList(),
                        Collections.emptyList()
                )
        );

        return changed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return removeAll(c, new HashSet<>());
    }

    @SuppressWarnings({"SuspiciousMethodCalls", "unchecked"})
    protected boolean removeAll(Collection<?> c, Set<BindableList<T>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances)) return false;

        ensureMutationAllowed();

        boolean changed = collection.removeAll(c);

        propagate((BindableList<T> bindable) -> bindable.removeAll(c, appliedInstances), this);

        collectionChanged.execute(
                new CollectionEvent<>(CollectionEvent.Type.REMOVE,
                        Collections.emptyList(),
                        c.stream()
                                .map(e -> new CollectionEvent.Element<>((T) e, collection.indexOf(e)))
                                .toList()
                )
        );

        return changed;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return retainAll(c, new HashSet<>());
    }

    protected boolean retainAll(Collection<?> c, Set<BindableList<T>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances)) return false;

        ensureMutationAllowed();

        List<T> removed = new ArrayList<>(collection);
        removed.removeAll(c);

        boolean changed = collection.retainAll(c);

        propagate((BindableList<T> bindable) -> bindable.retainAll(c, appliedInstances), this);

        collectionChanged.execute(
                new CollectionEvent<>(CollectionEvent.Type.REMOVE,
                        Collections.emptyList(),
                        removed.stream()
                                .map(e -> new CollectionEvent.Element<T>(e, collection.indexOf(e)))
                                .toList()
                )
        );

        return changed;
    }

    @Override
    public T get(int index) {
        return collection.get(index);
    }

    @Override
    public void add(int index, T element) {
        add(index, element, new HashSet<>());
    }

    protected void add(int index, T element, Set<BindableList<T>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances)) return;

        ensureMutationAllowed();

        collection.add(index, element);

        propagate((BindableList<T> bindable) -> bindable.add(index, element, appliedInstances), this);

        collectionChanged.execute(
                new CollectionEvent<>(CollectionEvent.Type.ADD,
                        Collections.singletonList(
                                new CollectionEvent.Element<>(element, index)
                        ),
                        Collections.emptyList()
                )
        );
    }

    @Override
    public T remove(int index) {
        return remove(index, new HashSet<>());
    }

    protected T remove(int index, Set<BindableList<T>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances)) return null;

        ensureMutationAllowed();

        T removed = collection.remove(index);

        propagate((BindableList<T> bindable) -> bindable.remove(index, appliedInstances), this);

        collectionChanged.execute(
                new CollectionEvent<>(CollectionEvent.Type.REMOVE,
                        Collections.emptyList(),
                        Collections.singletonList(
                                new CollectionEvent.Element<>(removed, index)
                        )
                )
        );

        return removed;
    }

    @Override
    public int indexOf(Object o) {
        return collection.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return collection.lastIndexOf(o);
    }

    @Override
    public ListIterator<T> listIterator() {
        return collection.listIterator();
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        return collection.listIterator(index);
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return collection.subList(fromIndex, toIndex);
    }

    @Override
    public void replaceAll(UnaryOperator<T> operator) {
        replaceAll(operator, new HashSet<>());
    }

    protected void replaceAll(UnaryOperator<T> operator, Set<BindableList<T>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances)) return;

        ensureMutationAllowed();

        collection.replaceAll(operator);

        propagate((BindableList<T> bindable) -> bindable.replaceAll(operator, appliedInstances), this);

        collectionChanged.execute(
                new CollectionEvent<>(CollectionEvent.Type.REPLACE,
                        collection.stream()
                                .map(e -> new CollectionEvent.Element<>(e, collection.indexOf(e)))
                                .toList(),
                        collection.stream()
                                .map(e -> new CollectionEvent.Element<>(e, collection.indexOf(e)))
                                .toList()
                )
        );
    }

    @Override
    public void sort(Comparator<? super T> c) {
        sort(c, new HashSet<>());
    }

    protected void sort(Comparator<? super T> c, Set<BindableList<T>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances)) return;

        ensureMutationAllowed();

        collection.sort(c);

        propagate((BindableList<T> bindable) -> bindable.sort(c, appliedInstances), this);

        collectionChanged.execute(
                new CollectionEvent<>(CollectionEvent.Type.REPLACE,
                        collection.stream()
                                .map(e -> new CollectionEvent.Element<>(e, collection.indexOf(e)))
                                .toList(),
                        collection.stream()
                                .map(e -> new CollectionEvent.Element<>(e, collection.indexOf(e)))
                                .toList()
                )
        );
    }

    @Override
    public Spliterator<T> spliterator() {
        return collection.spliterator();
    }

    @Override
    public void addFirst(T t) {
        addFirst(t, new HashSet<>());
    }

    protected void addFirst(T t, Set<BindableList<T>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances)) return;

        ensureMutationAllowed();

        collection.addFirst(t);

        propagate((BindableList<T> bindable) -> bindable.addFirst(t, appliedInstances), this);

        collectionChanged.execute(
                new CollectionEvent<>(CollectionEvent.Type.ADD,
                        Collections.singletonList(
                                new CollectionEvent.Element<>(t, 0)
                        ),
                        Collections.emptyList()
                )
        );
    }

    @Override
    public void addLast(T t) {
        addLast(t, new HashSet<>());
    }

    protected void addLast(T t, Set<BindableList<T>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances)) return;

        ensureMutationAllowed();

        collection.add(t);

        propagate((BindableList<T> bindable) -> bindable.addLast(t, appliedInstances), this);

        collectionChanged.execute(
                new CollectionEvent<>(CollectionEvent.Type.ADD,
                        Collections.singletonList(
                                new CollectionEvent.Element<>(t, collection.size() - 1)
                        ),
                        Collections.emptyList()
                )
        );
    }


    @Override
    public T getFirst() {
        return collection.getFirst();
    }

    @Override
    public T getLast() {
        return collection.getLast();
    }

    @Override
    public T removeFirst() {
        return removeFirst(new HashSet<>());
    }

    protected T removeFirst(Set<BindableList<T>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances)) return null;

        ensureMutationAllowed();

        T removed = collection.removeFirst();

        propagate((BindableList<T> bindable) -> bindable.removeFirst(appliedInstances), this);

        collectionChanged.execute(
                new CollectionEvent<>(CollectionEvent.Type.REMOVE,
                        Collections.emptyList(),
                        Collections.singletonList(
                                new CollectionEvent.Element<>(removed, 0)
                        )
                )
        );

        return removed;
    }

    @Override
    public T removeLast() {
        return removeLast(new HashSet<>());
    }

    protected T removeLast(Set<BindableList<T>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances)) return null;

        ensureMutationAllowed();

        T removed = collection.removeLast();

        propagate((BindableList<T> bindable) -> bindable.removeLast(appliedInstances), this);

        collectionChanged.execute(
                new CollectionEvent<>(CollectionEvent.Type.REMOVE,
                        Collections.emptyList(),
                        Collections.singletonList(
                                new CollectionEvent.Element<>(removed, collection.size() - 1)
                        )
                )
        );

        return removed;
    }

    @Override
    public List<T> reversed() {
        return collection.reversed();
    }

    @Override
    public boolean isEmpty() {
        return collection.isEmpty();
    }

    @Override
    public int size() {
        return collection.size();
    }

    @Override
    public BindableList<T> createInstance() {
        return new BindableList<>();
    }

    private boolean checkAlreadyApplied(Set<BindableList<T>> appliedInstances) {
        if (appliedInstances.contains(this)) {
            return true;
        }

        appliedInstances.add(this);
        return false;
    }

    private void ensureMutationAllowed() {
        if (isDisabled()) {
            throw new IllegalStateException(String.format("Cannot mutate the %s while it is disabled.", getClass().getSimpleName()));
        }
    }


    @Override
    public ValuedActionQueue<Boolean> getDisabledChanged() {
        return disabledChanged;
    }

    protected void setDisabled(boolean value, boolean bypassChecks, BindableList<T> source) {
        boolean oldValue = this.disabled;
        disabled = value;

        triggerDisabledChange(oldValue, value, source(source, this));
    }

    protected void triggerDisabledChange(
            boolean beforePropagation,
            boolean value,
            BindableList<T> source
    ) {
        triggerDisabledChange(
                beforePropagation,
                value,
                false,
                true,
                source
        );
    }

    protected void triggerDisabledChange(
            boolean beforePropagation,
            boolean value,
            boolean bypassChecks,
            boolean propagateToBindings,
            BindableList<T> source
    ) {
        if (propagateToBindings || bypassChecks) {
            propagate((BindableList<T> bindable) -> bindable.setDisabled(value, bypassChecks, source), source);
        }

        if (beforePropagation != value || bypassChecks) {
            disabledChanged.execute(new ValueChangedEvent<>(beforePropagation, value));
        }
    }


    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public void setDisabled(boolean disabled) {
        if (disabled == this.disabled) return;

        setDisabled(disabled, false, null);
    }

    @Override
    public void onDisabledChanged(ValuedAction<Boolean> action, boolean runOnceImmediately) {
        disabledChanged.add(action);

        if (runOnceImmediately) {
            action.accept(new ValueChangedEvent<>(disabled, disabled));
        }
    }

    @Override
    public BindableList<T> copy() {
        BindableList<T> copy = createInstance();

        copy.bindTo(this);

        return copy;
    }

    @Override
    public BindableList<T> copyTo(IBindableList<T> other) {
        if (!(other instanceof BindableList<T> copy)) return null;

        copy.collection.clear();
        copy.collection.addAll(collection);

        copy.setDisabled(disabled, true, null);

        return copy;
    }

    @Override
    public BindableList<T> getBoundCopy() {
        return (BindableList<T>) IBindableList.create(this);
    }

    @Override
    public BindableList<T> getUnboundCopy() {
        return copy();
    }

    @Override
    public BindableList<T> getWeakCopy() {
        return (BindableList<T>) IBindableList.createWeak(this);
    }

    @Override
    public BindableList<T> bindTo(IBindableList<T> other) {
        if (!(other instanceof BindableList<T> bindable)) return null;

        if (bindings.contains(bindable.weakReference))
            throw new IllegalArgumentException(String.format("Attempted to bind %s to %s, but it was already bound", this.getClass().getSimpleName(), other.getClass().getSimpleName()));

        BindableList<T> source = bindable.getBoundCopy();

        source.copyTo(this);

        refer(bindable);
        source.refer(this);

        return source;
    }

    @Override
    public BindableList<T> weakBind(IBindableList<T> other) {
        if (!(other instanceof BindableList<T> bindable)) return null;

        if (bindings.contains(bindable.weakReference))
            throw new IllegalArgumentException(String.format("Attempted to bind %s to %s, but it was already bound", this.getClass().getSimpleName(), other.getClass().getSimpleName()));

        BindableList<T> source = bindable.getWeakCopy();

        source.copyTo(this);
        source.refer(this);

        return source;
    }

    private void refer(BindableList<T> bindable) {
        WeakReference<BindableList<T>> reference = bindable.weakReference;

        if (bindings.contains(reference))
            throw new IllegalArgumentException(String.format("Attempted to add a binding to %s from %s, but it was already bound", this.getClass().getSimpleName(), bindable.getClass().getSimpleName()));

        bindings.add(reference);
    }

    private void unrefer(BindableList<T> bindable) {
        WeakReference<BindableList<T>> reference = bindable.weakReference;

        if (!bindings.contains(reference))
            throw new IllegalArgumentException(String.format("Attempted to remove a binding to %s from %s, but it was not bound", this.getClass().getSimpleName(), bindable.getClass().getSimpleName()));

        bindings.remove(reference);
    }

    @Override
    public void unbindEvents() {
        collectionChanged.clear();
        disabledChanged.clear();
    }

    @Override
    public void unbindWeak() {
        for (WeakReference<BindableList<T>> binding : new ArrayList<>(bindings)) {
            final BindableList<T> bindable = binding.get();

            if (bindable == null) {
                bindings.remove(binding);
                continue;
            }

            unbindWeakFrom(bindable);
        }

        bindings.clear();
    }

    @Override
    public void unbindBindings() {
        for (WeakReference<BindableList<T>> binding : new ArrayList<>(bindings)) {
            final BindableList<T> bindable = binding.get();

            if (bindable == null) {
                bindings.remove(binding);
                continue;
            }

            unbindFrom(bindable);
        }
    }

    @Override
    public void unbind() {
        unbindBindings();
        unbindWeak();
        unbindEvents();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void unbindFrom(IUnbindable other) {
        if (!(other instanceof BindableList)) return;

        final BindableList<T> bindable = (BindableList<T>) other;

        unrefer(bindable);
        bindable.unrefer(this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void unbindWeakFrom(IUnbindable other) {
        if (!(other instanceof BindableList)) return;

        final BindableList<T> bindable = (BindableList<T>) other;

        bindable.unrefer(this);
    }

    @Override
    public String toString() {
        return "BindableList{" +
                ", collection=" + collection +
                ", disabled=" + disabled +
                '}';
    }
}
