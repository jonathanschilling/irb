package de.labathome;

public enum IrbFileType {

	/** single image; identified by "IRBACS\0\0" */
	IMAGE("IRBACS\0\0"),

	/** sequence of images; idenfitied by "IRBIS 3\0" */
	SEQUENCE("IRBIS 3\0"),

	/** specific camera model; identified by "VARIOCAM" */
	VARIOCAM("VARIOCAM");

	private IrbFileType(String content) {
		this.content = content;
	}

	private String content;

	public static IrbFileType fromString(String content) {
		for (IrbFileType t: IrbFileType.values()) {
			if (t.content.equals(content)) {
				return t;
			}
		}
		return null;
	}
}
