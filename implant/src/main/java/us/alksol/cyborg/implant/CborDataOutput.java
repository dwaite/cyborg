package us.alksol.cyborg.implant;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.Charset;

import us.alksol.cyborg.implant.DataType.Major;

public class CborDataOutput implements CborOutput {
	DataOutput dataOutput;

	public CborDataOutput(DataOutput dataOutput) {
		this.dataOutput = dataOutput;
	}
	
	@Override
	public CborDataOutput writeBoolean(boolean v) throws IOException {
		DataType type = v ? DataType.TRUE : DataType.FALSE;
		type.write(dataOutput);
		return this;
	}

	@Override
	public CborDataOutput writeNull() throws IOException {
		DataType.NULL.write(dataOutput);
		return this;
	}

	@Override
	public CborDataOutput writeUndefined() throws IOException {
		DataType.UNDEFINED.write(dataOutput);
		return this;
	}

	@Override
	public CborDataOutput writeSimpleValue(int value) throws IOException {
		DataType.simpleValue(value).write(dataOutput);
		return this;
	}

	@Override
	public CborDataOutput writeInteger(int v) throws IOException {
		DataType.integerValue(v).write(dataOutput);
		return this;
	}

	@Override
	public CborDataOutput writeLong(long v) throws IOException {
		DataType.longValue(v).write(dataOutput);
		return this;
	}

	@Override
	public CborDataOutput writeBytes(byte[] b) throws IOException {
		DataType.canonicalFromMajorAndLongValue(Major.BYTE_STRING, b.length).write(dataOutput);
		dataOutput.write(b);
		return this;
	}

	@Override
	public CborDataOutput writeText(String s) throws IOException {
		byte[] rawText = s.getBytes(Charset.forName("UTF-8"));
		DataType.canonicalFromMajorAndLongValue(Major.TEXT_STRING, rawText.length).write(dataOutput);
		dataOutput.write(rawText);
		return this;
	}

	@Override
	public CborDataOutput writeBytes(Iterable<byte[]> s) throws IOException {
		DataType.canonicalFromMajorAndLongValue(Major.BYTE_STRING, DataType.INDETERMINATE).write(dataOutput);
		for (byte chunk[] : s) {
			writeBytes(chunk);
		}
		DataType.BREAK.write(dataOutput);
		return this;
	}

	@Override
	public CborDataOutput writeTextStream(Iterable<String> r) throws IOException {
		DataType.canonicalFromMajorAndLongValue(Major.TEXT_STRING, DataType.INDETERMINATE).write(dataOutput);
		for (String chunk : r) {
			writeText(chunk);
		}
		DataType.BREAK.write(dataOutput);
		return this;
	}

	@Override
	public CborDataOutput writeFloat(float v) throws IOException {
		DataType.floatValue(v).write(dataOutput);
		return this;
	}

	@Override
	public CborDataOutput writeDouble(double v) throws IOException {
		DataType.doubleValue(v).write(dataOutput);
		return this;
	}

	@Override
	public CborDataOutput writeHalfFloat(int v) throws IOException {
		DataType.halfFloatValue(v).write(dataOutput);
		return this;
	}

	@Override
	public CborDataOutput writeTag(long tag) throws IOException {
		DataType.canonicalFromMajorAndLongValue(Major.TAG, tag).write(dataOutput);
		return this;
	}

	@Override
	public CborDataOutput writeStartArray(int count) throws IOException {
		DataType.canonicalFromMajorAndLongValue(Major.ARRAY, count).write(dataOutput);
		return this;
	}

	@Override
	public CborDataOutput writeStartIndefiniteArray() throws IOException {
		DataType.canonicalFromMajorAndLongValue(Major.ARRAY, DataType.INDETERMINATE).write(dataOutput);
		return this;
	}

	@Override
	public CborDataOutput writeStartMap(int count) throws IOException {
		DataType.canonicalFromMajorAndLongValue(Major.MAP, count).write(dataOutput);
		return this;
	}

	@Override
	public CborDataOutput writeStartIndefiniteMap() throws IOException {
		DataType.canonicalFromMajorAndLongValue(Major.MAP, DataType.INDETERMINATE).write(dataOutput);
		return this;
	}

	@Override
	public CborDataOutput writeBreak() throws IOException {
		DataType.BREAK.write(dataOutput);
		return this;
	}	
}
