package com.overkill.client.setting;

public class NumberSetting extends Setting<Double> {
	public final double min;
	public final double max;
	public final double step;

	public NumberSetting(String name, double value, double min, double max, double step) {
		super(name, value);
		this.min = min;
		this.max = max;
		this.step = step;
	}

	@Override
	public void set(Double value) {
		double snapped = Math.round(value / step) * step;
		super.set(Math.clamp(snapped, min, max));
	}

	/** Position of the value between min and max, 0..1. */
	public double fraction() {
		return (value - min) / (max - min);
	}

	public String display() {
		return step >= 1 ? String.valueOf(Math.round(value)) : String.format("%.2f", value);
	}
}
