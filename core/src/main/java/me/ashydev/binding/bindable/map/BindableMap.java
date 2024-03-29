/*
 * Copyright (c) 2024 Ashley (uwuvern) <uwuvern@outlook.com>
 *
 * This project is licensed under the MIT license, check the root of the project for
 * more information.
 */

package me.ashydev.binding.bindable.map;

import me.ashydev.binding.IBindableMap;
import me.ashydev.binding.IUnbindable;
import me.ashydev.binding.action.Action;
import me.ashydev.binding.action.ValuedAction;
import me.ashydev.binding.action.event.ValueChangedEvent;
import me.ashydev.binding.action.queue.ActionQueue;
import me.ashydev.binding.action.queue.ValuedActionQueue;
import me.ashydev.binding.bindable.list.BindableList;
import me.ashydev.binding.common.reference.LockedWeakList;
import me.ashydev.binding.event.map.IMapEvent;
import me.ashydev.binding.event.map.MapEvent;

import java.lang.ref.WeakReference;
import java.util.*;

public class BindableMap<K, V> implements IBindableMap<K, V> {
    protected static <V> V source(V source, V self) {
        return source != null ? source : self;
    }

    private transient final WeakReference<BindableMap<K, V>> weakReference = new WeakReference<>(this);

    private transient final ActionQueue<MapEvent<K, V>> collectionChanged = new ActionQueue<>();
    private transient final ValuedActionQueue<Boolean> disabledChanged = new ValuedActionQueue<>();

    private transient final LockedWeakList<BindableMap<K, V>> bindings = new LockedWeakList<>();
    private final Map<K, V> map;
    private transient boolean disabled;


    public BindableMap(MapType type, Map<K, V> items) {
        this.map = switch (type) {
            case HASH -> new HashMap<>();
            case LINKED -> new LinkedHashMap<>();
            case IDENTITY -> new IdentityHashMap<>();
            case WEAK -> new WeakHashMap<>();
        };

        if (items != null)
            map.putAll(items);

        this.disabled = false;
    }

    public BindableMap(Map<K, V> items) {
        this(MapType.HASH, items);
    }

    public BindableMap() {
        this(MapType.HASH, null);
    }

    protected void propagate(Action<BindableMap<K, V>> propagation, BindableMap<K, V> source) {
        Iterator<WeakReference<BindableMap<K, V>>> iterator = bindings.iterator();

        while (iterator.hasNext()) {
            WeakReference<BindableMap<K, V>> binding = iterator.next();

            if (binding.refersTo(source)) continue;

            BindableMap<K, V> bindable = binding.get();

            if (bindable == null) {
                iterator.remove();

                continue;
            }

            propagation.accept(bindable);
        }
    }


    @Override
    public void onCollectionChanged(Action<MapEvent<K, V>> action, boolean runOnceImmediately) {
        collectionChanged.add(action);

        if (runOnceImmediately) {
            action.accept(
                    new MapEvent<>(
                            MapEvent.Type.ADD,
                            getElements(map),
                            Collections.emptyList()
                    )
            );
        }
    }

    @Override
    public ActionQueue<MapEvent<K, V>> getCollectionChanged() {
        return collectionChanged;
    }

    @Override
    public BindableMap<K, V> createInstance() {
        return new BindableMap<>();
    }

    private boolean checkAlreadyApplied(Set<BindableMap<K, V>> appliedInstances) {
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

    protected void setDisabled(boolean value, boolean bypassChecks, BindableMap<K, V> source) {
        boolean oldValue = this.disabled;
        disabled = value;

        triggerDisabledChange(oldValue, value, bypassChecks, true, source);
    }

    private void triggerDisabledChange(boolean beforePropagation, boolean value, boolean bypassChecks, boolean propagateToBindings, BindableMap<K, V> source) {
        if (propagateToBindings || bypassChecks) {
            propagate((bindable) -> bindable.setDisabled(disabled, bypassChecks, source), source);
        }

        if (beforePropagation != value || bypassChecks) {
            disabledChanged.execute(new ValueChangedEvent<>(bypassChecks, value));
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
    public BindableMap<K, V> copy() {
        BindableMap<K, V> copy = createInstance();

        copy.bindTo(this);

        return copy;
    }

    @Override
    public BindableMap<K, V> copyTo(IBindableMap<K, V> other) {
        if (!(other instanceof BindableMap<K, V> copy)) return null;

        copy.map.clear();
        copy.map.putAll(map);

        copy.setDisabled(disabled, true, null);

        return copy;
    }

    @Override
    public BindableMap<K, V> getBoundCopy() {
        return (BindableMap<K, V>) IBindableMap.create(this);
    }

    @Override
    public BindableMap<K, V> getUnboundCopy() {
        return copy();
    }

    @Override
    public BindableMap<K, V> getWeakCopy() {
        return (BindableMap<K, V>) IBindableMap.createWeak(this);
    }

    @Override
    public BindableMap<K, V> bindTo(IBindableMap<K, V> other) {
        if (!(other instanceof BindableMap<K, V> bindable)) return null;

        if (bindings.contains(bindable.weakReference))
            throw new IllegalArgumentException(String.format("Attempted to bind %s to %s, but it was already bound", this.getClass().getSimpleName(), other.getClass().getSimpleName()));

        BindableMap<K, V> source = bindable.getBoundCopy();

        source.copyTo(this);

        refer(bindable);
        source.refer(this);

        return source;
    }

    @Override
    public BindableMap<K, V> weakBind(IBindableMap<K, V> other) {
        if (!(other instanceof BindableMap<K, V> bindable)) return null;

        if (bindings.contains(bindable.weakReference))
            throw new IllegalArgumentException(String.format("Attempted to bind %s to %s, but it was already bound", this.getClass().getSimpleName(), other.getClass().getSimpleName()));

        BindableMap<K, V> source = bindable.getWeakCopy();

        source.copyTo(this);
        source.refer(this);

        return source;
    }

    private void refer(BindableMap<K, V> bindable) {
        WeakReference<BindableMap<K, V>> reference = bindable.weakReference;

        if (bindings.contains(reference))
            throw new IllegalArgumentException(String.format("Attempted to add a binding to %s from %s, but it was already bound", this.getClass().getSimpleName(), bindable.getClass().getSimpleName()));

        bindings.add(reference);
    }

    private void unrefer(BindableMap<K, V> bindable) {
        WeakReference<BindableMap<K, V>> reference = bindable.weakReference;

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
        for (WeakReference<BindableMap<K, V>> binding : new ArrayList<>(bindings)) {
            final BindableMap<K, V> bindable = binding.get();

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
        for (WeakReference<BindableMap<K, V>> binding : new ArrayList<>(bindings)) {
            final BindableMap<K, V> bindable = binding.get();

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
        if (!(other instanceof BindableMap)) return;

        final BindableMap<K, V> bindable = (BindableMap<K, V>) other;

        unrefer(bindable);
        bindable.unrefer(this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void unbindWeakFrom(IUnbindable other) {
        if (!(other instanceof BindableMap)) return;

        final BindableMap<K, V> bindable = (BindableMap<K, V>) other;

        bindable.unrefer(this);
    }

    @Override
    public String toString() {
        return "BindableMap{" +
                "map=" + map +
                ", disabled=" + disabled +
                '}';
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
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return map.get(key);
    }

    @Override
    public V put(K key, V value) {
        return put(key, value, new HashSet<>());
    }

    protected V put(K key, V value, Set<BindableMap<K, V>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances))
            return null;

        ensureMutationAllowed();

        V oldValue = map.put(key, value);

        MapEvent.Type type = oldValue == null ? MapEvent.Type.ADD : MapEvent.Type.REPLACE;

        propagate((bindable) -> bindable.put(key, value, appliedInstances), this);

        collectionChanged.execute(
                new MapEvent<>(
                        type,
                        getElements(key, value),
                        getElements(key, oldValue)
                )
        );

        return oldValue;
    }


    @Override
    public V remove(Object key) {
        return remove(key, new HashSet<>());
    }

    @SuppressWarnings("unchecked")
    protected V remove(Object key, Set<BindableMap<K, V>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances))
            return null;

        ensureMutationAllowed();

        V oldValue = map.remove(key);

        propagate((bindable) -> bindable.remove(key, appliedInstances), this);

        collectionChanged.execute(
                new MapEvent<>(
                        MapEvent.Type.REMOVE,
                        Collections.emptyList(),
                        getElements((K) key, oldValue)
                )
        );

        return oldValue;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        putAll(m, new HashSet<>());
    }

    @SuppressWarnings("unchecked")
    protected void putAll(Map<? extends K, ? extends V> m, Set<BindableMap<K, V>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances))
            return;

        ensureMutationAllowed();

        map.putAll(m);

        propagate((bindable) -> bindable.putAll(m, appliedInstances), this);

        collectionChanged.execute(
                new MapEvent<>(
                        MapEvent.Type.ADD,
                        getElements((Map<K, V>) m),
                        Collections.emptyList()
                )
        );
    }

    @Override
    public void clear() {
        clear(new HashSet<>());
    }

    protected void clear(Set<BindableMap<K, V>> appliedInstances) {
        if (checkAlreadyApplied(appliedInstances))
            return;

        ensureMutationAllowed();

        map.clear();

        propagate((bindable) -> bindable.clear(appliedInstances), this);

        collectionChanged.execute(
                new MapEvent<>(
                        MapEvent.Type.REMOVE,
                        Collections.emptyList(),
                        getElements(map)
                )
        );
    }


    @Override
    public Set<K> keySet() {
        return map.keySet();
    }


    @Override
    public Collection<V> values() {
        return map.values();
    }


    @Override
    public Set<Entry<K, V>> entrySet() {
        return map.entrySet();
    }

    private Collection<IMapEvent.Element<K, V>> getElements(Map<K, V> elements) {
        Collection<IMapEvent.Element<K, V>> collection = new ArrayList<>();

        for (Map.Entry<K, V> entry : elements.entrySet()) {
            collection.add(new IMapEvent.Element<>(entry.getKey(), entry.getValue()));
        }

        return collection;
    }

    private Collection<IMapEvent.Element<K, V>> getElements(K key, V value) {
        Collection<IMapEvent.Element<K, V>> collection = new ArrayList<>();

        collection.add(new IMapEvent.Element<>(key, value));

        return collection;
    }

    public enum MapType {
        HASH, LINKED, IDENTITY, WEAK
    }
}
