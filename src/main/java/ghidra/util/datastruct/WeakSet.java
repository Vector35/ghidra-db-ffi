/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.util.datastruct;

import java.util.*;
import java.util.stream.Stream;


public abstract class WeakSet<T> implements Set<T> {

	protected WeakHashMap<T, T> weakHashStorage;

	public WeakSet() {
		weakHashStorage = new WeakHashMap<>();
	}

//==================================================================================================
// Interface Methods
//==================================================================================================

	/**
	 * Add the given object to the set
	 * @param t the object to add
	 */
	@Override
	public abstract boolean add(T t);

	/**
	 * Remove the given object from the data structure
	 * @param t the object to remove
	 *
	 */
	@Override
	public abstract boolean remove(Object t);

	/**
	 * Returns true if the given object is in this data structure
	 * @param t the object
	 * @return true if the given object is in this data structure
	 */
	@Override
	public abstract boolean contains(Object t);

	/**
	 * Remove all elements from this data structure
	 */
	@Override
	public abstract void clear();

	/**
	 * Return the number of objects contained within this data structure
	 * @return the size
	 */
	@Override
	public abstract int size();

	/**
	 * Return whether this data structure is empty
	 * @return whether this data structure is empty
	 */
	@Override
	public abstract boolean isEmpty();

	/**
	 * Returns a Collection view of this set.  The returned Collection is backed by this set.
	 *
	 * @return a Collection view of this set.  The returned Collection is backed by this set.
	 */
	public abstract Collection<T> values();

	@Override
	public Object[] toArray() {
		return weakHashStorage.keySet().toArray();
	}

	// <T> is hiding the class declaration; it is needed to satisfy the interface
	@SuppressWarnings("hiding")
	@Override
	public <T> T[] toArray(T[] a) {
		return weakHashStorage.keySet().toArray(a);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return weakHashStorage.keySet().containsAll(c);
	}

	@Override
	public abstract boolean addAll(Collection<? extends T> c);

	@Override
	public abstract boolean retainAll(Collection<?> c);

	@Override
	public abstract boolean removeAll(Collection<?> c);

	/**
	 * Returns a stream of the values of this collection.
	 * @return a stream of the values of this collection.
	 */
	@Override
	public abstract Stream<T> stream();
}
