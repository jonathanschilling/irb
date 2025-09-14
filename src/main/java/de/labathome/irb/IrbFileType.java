/* irb
 * IrbFileType Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.irb;

public enum IrbFileType {

	/** single image; identified by "IRBACS\0\0" */
	IMAGE("IRBACS\0\0"),

	/** sequence of images; identified by "IRBIS 3\0" */
	SEQUENCE("IRBIS 3\0"),

	/** specific camera model; identified by "VARIOCAM"; can also be video! */
	VARIOCAM("VARIOCAM"),

	/** what is this ??? some kind of video file ??? */
	O_SAVE_IRB("oSaveIRB");

	private IrbFileType(String content) {
		this.content = content;
	}

	private String content;

	public static IrbFileType fromString(String content) {
		for (IrbFileType t : IrbFileType.values()) {
			if (t.content.equals(content)) {
				return t;
			}
		}
		throw new RuntimeException("unknown IrbFileType: '" + content + "'");
	}
}
