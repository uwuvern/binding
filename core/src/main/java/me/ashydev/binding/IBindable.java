/*
 * Copyright (c) 2024 Ashley (uwuvern) <uwuvern@outlook.com>
 *
 * This project is licensed under the MIT license, check the root of the project for
 * more information.
 */

package me.ashydev.binding;

import me.ashydev.binding.types.*;

import java.io.Serializable;

public interface IBindable<T>
        extends IUnbindable, IContainer<T>, IDisableable,
        ICopyable<IBindable<T>>, InstanceContainer<IBindable<T>>,
        IBindingContainer<IBindable<T>>, ILeaser<ILeasedBindable<T>>,
        Serializable {

    static <T, V extends IBindable<T>> IBindable<T> create(V source) {
        IBindable<T> copy = source.createInstance();

        if (copy.getClass() != source.getClass()) {
            throw new IllegalArgumentException(
                    String.format("Attempted to create a bindable copy of %s, but the returned type was %s", source.getClass().getSimpleName(), copy.getClass().getSimpleName())
                            + String.format("Override %s.createInstance() for GetBoundCopy() to return the correct type.", source.getClass().getSimpleName())
            );
        }

        return copy.bindTo(source);
    }

    static <T, V extends IBindable<T>> IBindable<T> createWeak(V source) {
        IBindable<T> copy = source.createInstance();

        if (copy.getClass() != source.getClass()) {
            throw new IllegalArgumentException(
                    String.format("Attempted to create a bindable copy of %s, but the returned type was %s, ", source.getClass().getSimpleName(), copy.getClass().getSimpleName())
                            + String.format("Override %s.createInstance() for GetBoundCopy() to return the correct type.", source.getClass().getSimpleName())
            );
        }

        return copy.weakBind(source);
    }
}
