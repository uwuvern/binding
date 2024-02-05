/*
 * Copyright (c) 2024 Ashley (uwuvern) <uwuvern@outlook.com>
 *
 * This project is licensed under the MIT license, check the root of the project for
 * more information.
 */

package me.ashydev.bindable.types;

import me.ashydev.bindable.action.Action;
import me.ashydev.bindable.action.queue.ActionQueue;
import me.ashydev.bindable.event.ValueChangedEvent;

public interface ILeaser<T> {
    ActionQueue<ValueChangedEvent<LeaseState>> getLeaseChanged();

    T begin(boolean revertValueOnReturn);
    default T begin() {
        return begin(true);
    }

    void end(T bindable);

    void onLeaseChanged(Action<ValueChangedEvent<LeaseState>> action, boolean runOnceImmediately);
    default void onLeaseChanged(Action<ValueChangedEvent<LeaseState>> action) {
        onLeaseChanged(action, false);
    }

    enum LeaseState {
        LEASED,
        RETURNED,
        NONE
    }

}