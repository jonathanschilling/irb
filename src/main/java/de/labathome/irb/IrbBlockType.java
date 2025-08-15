/* irb
 * IrbBlockType Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.irb;

public enum IrbBlockType {

	UNKNOWN(-1),
	EMPTY(0),
	IMAGE(1),
	PREVIEW(2),
	TEXT_INFO(3),
	HEADER(4),
	TODO_MYSTERY_5(5),
	TODO_MYSTERY_6(6),
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
		throw new RuntimeException(String.format("unknown block type: %d", val));
	}

	public int value() {
		return value;
	}
}
