/*
 * Copyright (c) 2024 Ashley (uwuvern) <uwuvern@outlook.com>
 *
 * This project is licensed under the MIT license, check the root of the project for
 * more information.
 */

package me.ashydev.binding.action.queue;

import me.ashydev.binding.action.Action;
import me.ashydev.binding.action.execution.IExecutable;

import java.util.ArrayDeque;
import java.util.Collection;

public class ActionQueue<E> extends ArrayDeque<Action<E>> implements IExecutable<E> {
    public ActionQueue() {
    }

    public ActionQueue(int numElements) {
        super(numElements);
    }

    public ActionQueue(Collection<? extends Action<E>> c) {
        super(c);
    }

    @Override
    public boolean execute(E event) {
        for (Action<E> e : this) e.accept(event);

        return true;
    }
}
