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

	public int value() {
		return value;
	}
}
