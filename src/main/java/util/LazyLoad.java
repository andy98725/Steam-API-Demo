package util;

import java.io.Serializable;

// Similar to a cache, but with no clear
// Allows for better performance.
public abstract class LazyLoad<T> implements Serializable {
	private static final long serialVersionUID = 1L;

	private transient T instance;

	public abstract T make();

	public T get() {
		if (instance == null)
			synchronized (this) {
				if (instance == null)
					instance = make();
			}

		return instance;
	}


	public synchronized boolean has() {
		return instance != null;
	}

	public synchronized void set(T set) {
		instance = set;
	}

	public synchronized void clone(LazyLoad<T> other) {
		if (other.instance != null)
			instance = other.instance;
	}
}
