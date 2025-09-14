/* irb
 * IrbBlockType Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.irb;

public enum IrbBlockType {

	/** empty block - ignored for now */
	EMPTY(0),

	/** actual image data; has its own header */
	IMAGE(1),

	/** preview image: fixed 80x60 pixels */
	PREVIEW(2),

	/** text info block + additional meta-data? */
	TEXT_INFO(3),

	/** frame header block for video files - marks additional video frames after first "front-matter" image */
	FRAME_HEADER(4),

	/** unknown - maybe non-uniformity correction ??? */
	TODO_MYSTERY_5(5),

	/** unknown */
	TODO_MYSTERY_6(6),

	/** unknown - additional audio recording? */
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
		throw new RuntimeException(String.format("unknown IrbBlockType: %d", val));
	}

	public int value() {
		return value;
	}
}
