package com.overkill.client.module;

public enum Category {
	COMBAT("Combat"),
	MOVEMENT("Movement"),
	RENDER("Render"),
	PLAYER("Player"),
	WORLD("World"),
	FUN("Fun"),
	CLIENT("Client");

	public final String title;

	Category(String title) {
		this.title = title;
	}
}
