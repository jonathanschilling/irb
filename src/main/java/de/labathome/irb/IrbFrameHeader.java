package de.labathome.irb;

import java.nio.ByteBuffer;

import com.google.gson.annotations.Expose;

/**
 * 64 bytes of header data for video frames.
 * Interpreted (for now, until we know better) as 16 32-bit integers.
 */
public class IrbFrameHeader {

	/** == 1 */
	@Expose(serialize = true)
	int bitfield0;

	/** 0x65 == 101 */
	@Expose(serialize = true)
	int bitfield1;

	/** 1-based counter; starts at 2 for first additional video frame */
	@Expose(serialize = true)
	int frame_counter;

	/** offset in file at which IrbImage of this frame starts */
	@Expose(serialize = true)
	int offset;

	/** size of IrbImage of this frame */
	@Expose(serialize = true)
	int size;

	/** == 0 */
	@Expose(serialize = true)
	int bitfield2;

	/** == 1 */
	@Expose(serialize = true)
	int bitfield3;

	/** == 0 */
	@Expose(serialize = true)
	int bitfield4;

	/** // == 4 (except last 2 frames) */
	@Expose(serialize = true)
	int bitfield5;

	/** == 0x65 == 101 (except frame -2) */
	@Expose(serialize = true)
	int bitfield6;

	/** == frame_counter, except frame -2 */
	@Expose(serialize = true)
	int frame_counter_2;

	/** ??? */
	@Expose(serialize = true)
	int expected_next_offset;

	/** ??? */
	@Expose(serialize = true)
	int size_2;

	/** == 0 */
	@Expose(serialize = true)
	int bitfield7;

	/** == 1, except frame -2 has 0 */
	@Expose(serialize = true)
	int bitfield8;

	/** == 0 */
	@Expose(serialize = true)
	int bitfield9;

	private IrbFrameHeader() { }

	public static IrbFrameHeader fromBuffer(ByteBuffer buf, int offset, int size) {
		IrbFrameHeader frame_header = new IrbFrameHeader();

		buf.position(offset);
		final int initial_position = buf.position();
		// 0

		frame_header.bitfield0 = buf.getInt();
		// 4

		frame_header.bitfield1 = buf.getInt();
		// 8

		frame_header.frame_counter = buf.getInt();
		// 12

		frame_header.offset = buf.getInt();
		// 16

		frame_header.size = buf.getInt();
		// 20

		frame_header.bitfield2 = buf.getInt();
		// 24

		frame_header.bitfield3 = buf.getInt();
		// 28

		frame_header.bitfield4 = buf.getInt();
		// 32

		frame_header.bitfield5 = buf.getInt();
		// 36

		frame_header.bitfield6 = buf.getInt();
		// 40

		frame_header.frame_counter_2 = buf.getInt();
		// 44

		frame_header.expected_next_offset = buf.getInt();
		// 48

		frame_header.size_2 = buf.getInt();
		// 52

		frame_header.bitfield7 = buf.getInt();
		// 56

		frame_header.bitfield8 = buf.getInt();
		// 60

		frame_header.bitfield9 = buf.getInt();
		// 64

		System.out.printf("# IrbFrameHeader frame_counter=%d offset=%d size=%d\n",
				frame_header.frame_counter, frame_header.offset, frame_header.size);

		if (buf.position() - initial_position != 64) {
			throw new RuntimeException("byte counting error in parsing of IrbFrameHeader");
		}

		return frame_header;
	}
}
