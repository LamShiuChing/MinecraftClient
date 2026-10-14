package com.overkill.client.setting;

public abstract class Setting<T> {
	public final String name;
	public final T defaultValue;
	protected T value;

	protected Setting(String name, T value) {
		this.name = name;
		this.defaultValue = value;
		this.value = value;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		this.value = value;
	}

	public void reset() {
		set(defaultValue);
	}
}
