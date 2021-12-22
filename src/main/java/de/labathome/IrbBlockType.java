package de.labathome;

public enum IrbBlockType {

	UNKNOWN(-1),
	EMPTY(0),
	IMAGE(1),
	PREVIEW(2),
	TEXT_INFO(3),
	HEADER(4),
	AUDIO(7);

	private IrbBlockType(int value) {
		this.value = value;
	}
	private int value;

	public static IrbBlockType fromInt(int val) {
		for (IrbBlockType t: IrbBlockType.values()) {
			if (t.value == val) {
				return t;
			}
		}
		return null;
	}
}
