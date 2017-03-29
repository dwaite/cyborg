package us.alksol.cyborg.implant;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.function.Consumer;

import us.alksol.cyborg.implant.DataType.AdditionalInfoFormat;
import us.alksol.cyborg.implant.DataType.Major;

public class CborDataInput implements CborInput {
	final DataInput input;
	DataType header;
	
	public CborDataInput(DataInput input) {
		this.input = input;
		this.header = null;
	}
	@Override
	public boolean readBoolean() throws IOException, CborException {
		peekDataType();
		if (header.equals(DataType.TRUE)) {
			header = null;
			return true;
		}
		if (header.equals(DataType.FALSE)) {
			header = null;
			return false;
		}
		if (header.getMajorType() != Major.SIMPLE_FLOAT) {
			throw new CborIncorrectMajorTypeException(header, Major.SIMPLE_FLOAT);
		}
		throw new CborException("Expected boolean type, got " + header);
	}

	@Override
	public DataType peekDataType() throws IOException {
		if (header == null) {
			header = DataType.readDataType(input);
		}
		return header;
	}
	
	@Override
	public void readNull() throws IOException, CborException {
		peekDataType();
		if (header.equals(DataType.NULL)) {
			header = null;
			return;
		}
		if (header.getMajorType() != Major.SIMPLE_FLOAT) {
			throw new CborIncorrectMajorTypeException(header, Major.SIMPLE_FLOAT);
		}
		throw new CborException("Expected null type, got " + header);
	}

	@Override
	public void readUndefined() throws IOException, CborException {
		peekDataType();
		if (header.equals(DataType.UNDEFINED)) {
			header = null;
			return;
		}
		if (header.getMajorType() != Major.SIMPLE_FLOAT) {
			throw new CborIncorrectMajorTypeException(header, Major.SIMPLE_FLOAT);
		}
		throw new CborException("Expected undefined type, got " + header);
	}

	@Override
	public int readSimpleValue() throws IOException, CborException {
		peekDataType();
		if (header.getMajorType() != Major.SIMPLE_FLOAT) {
			throw new CborIncorrectMajorTypeException(header, Major.SIMPLE_FLOAT);
		}
		switch (header.getAdditionalInfo()) {
		case IMMEDIATE:
		case BYTE:
			int value = (int) header.getValue();
			header = null;
			return value;
		default:
			throw new CborException("Expected simple type, got " + header);
		}
	}

	@Override
	public int readInteger() throws IOException, CborException {
		peekDataType();
		if (header.getMajorType() == Major.UNSIGNED_INT) {
			long value = header.getValue();
			if (value > Integer.MAX_VALUE) {
				throw new ArithmeticException("value " + value + " is larger than maximum value " + Integer.MAX_VALUE);
			}
			header = null;
			return (int) value;
		}
		else if (header.getMajorType() == Major.NEGATIVE_INT) {
			long value = ~header.getValue();
			if (value < Integer.MIN_VALUE) {
				throw new ArithmeticException("value " + value + " is smaller than minimum value " + Integer.MIN_VALUE);
			}
			header = null;
			return (int) value;
		}
		else {
			throw new CborException("data type " + header + " not an unsigned or negative integer");
		}
	}

	@Override
	public long readLong() throws IOException, CborException {
		peekDataType();
		long value = header.getValue();
		if (header.getMajorType() == Major.UNSIGNED_INT) {
			header = null;
			return value;
		}
		else if (header.getMajorType() == Major.NEGATIVE_INT) {
			header = null;
			return ~value;
		}
		else {
			throw new CborException("data type " + header + " not an unsigned or negative integer");
		}
	}
	public void readText(Consumer<String> textBlockConsumer) throws CborException, IOException {
		peekDataType();
		long length = header.getValue();
		if (header.getMajorType() != Major.TEXT_STRING) {
			throw new CborIncorrectMajorTypeException(header, Major.TEXT_STRING);
		}
		Charset utf8 = Charset.forName("UTF-8");
		if (length == DataType.INDETERMINATE) {
			header = null;
			byte[] block;
			while ((block = readBlock(Major.TEXT_STRING)) != null) {
				String strBlock = new String(block, utf8);
				textBlockConsumer.accept(strBlock);
			}
		} else {
			byte[] block = readBlock(Major.TEXT_STRING);
			String strBlock = new String(block, utf8);
			textBlockConsumer.accept(strBlock);
		}
	}

	public void readBytes(Consumer<byte[]> byteBlockConsumer) throws CborException, IOException {
		peekDataType();
		long length = header.getValue();
		if (header.getMajorType() != Major.BYTE_STRING) {
			throw new CborIncorrectMajorTypeException(header, Major.BYTE_STRING);
		}
		if (length == DataType.INDETERMINATE) {
			header = null;
			byte[] block;
			while ((block = readBlock(Major.BYTE_STRING)) != null) {
				byteBlockConsumer.accept(block);
			}
		} else {
			byte[] block = readBlock(Major.BYTE_STRING);
			byteBlockConsumer.accept(block);
		}
	}
	private byte[] readBlock(Major major) throws IOException {
		peekDataType();
		if (header.equals(DataType.BREAK)) {
			header = null;
			return null;
		}
		if (header.getMajorType() != major) {
			throw new IOException("cbor data not well-formed", new CborIncorrectMajorTypeException(header, major));
		}
		int length = (int) header.getValue();
		byte block[] = new byte[length];
		input.readFully(block);
		header = null;
		return block;
	}
	@Override
	public byte[] readBytes() throws IOException, CborException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		readBytes((block) -> bos.write(block, 0, block.length));
		return bos.toByteArray();
	}

	@Override
	public String readText() throws IOException, CborException {
		StringWriter sw = new StringWriter();
		readText((block) -> sw.write(block));
		return sw.toString();
	}

	@Override
	public float readFloat() throws IOException, CborException {
		peekDataType();
		long value = header.getValue();
		if (header.getMajorType() != Major.SIMPLE_FLOAT) {
			throw new CborIncorrectMajorTypeException(header, Major.SIMPLE_FLOAT);
		}
		if (header.getAdditionalInfo() != AdditionalInfoFormat.INT) {
			throw new CborException("data type " + header + " does not represent a binary32 A.K.A. float");
		}
		float floatValue = Float.intBitsToFloat((int)value);
		header = null;
		return floatValue;
	}

	@Override
	public double readDouble() throws IOException, CborException {
		peekDataType();
		long value = header.getValue();
		if (header.getMajorType() != Major.SIMPLE_FLOAT) {
			throw new CborIncorrectMajorTypeException(header, Major.SIMPLE_FLOAT);
		}
		if (header.getAdditionalInfo() != AdditionalInfoFormat.LONG) {
			throw new CborException("data type " + header + " does not represent a binary64 A.K.A. double");
		}
		double doubleValue = Double.longBitsToDouble(value);
		header = null;
		return doubleValue;
	}

	@Override
	public short readHalfFloat() throws IOException, CborException {
		peekDataType();
		long value = header.getValue();
		if (header.getMajorType() != Major.SIMPLE_FLOAT) {
			throw new CborIncorrectMajorTypeException(header, Major.SIMPLE_FLOAT);
		}
		if (header.getAdditionalInfo() != AdditionalInfoFormat.SHORT) {
			throw new CborException("data type " + header + " does not represent a binary16 half float");
		}
		header = null;
		return (short) value;
	}

	@Override
	public int readTag() throws IOException, CborException {
		peekDataType();
		long value = header.getValue();
		if (header.getMajorType() != Major.TAG) {
			throw new CborIncorrectMajorTypeException(header, Major.TAG);
		}
		if (value > Integer.MAX_VALUE) {
			throw new ArithmeticException("value " + value + " larger than " + Integer.MAX_VALUE);
		}
		header = null;
		return (int) value;
	}
	@Override
	public long readLongTag() throws IOException, CborException {
		peekDataType();
		long value = header.getValue();
		if (header.getMajorType() != Major.TAG) {
			throw new CborIncorrectMajorTypeException(header, Major.TAG);
		}
		header = null;
		return value;
	}

	@Override
	public int readArrayCount() throws IOException, CborException {
		peekDataType();
		long value = header.getValue();
		if (header.getMajorType() != Major.ARRAY) {
			throw new CborIncorrectMajorTypeException(header, Major.ARRAY);
		}
		if (value ==  DataType.INDETERMINATE) {
			throw new CborIndeterminateLengthException(header);
		}
		if (value > Integer.MAX_VALUE) {
			throw new ArithmeticException("length " + value + " larger than " + Integer.MAX_VALUE);
		}
		header = null;
		return (int)value;
	}

	@Override
	public Optional<Integer> readPossiblyIndefiniteArrayCount() throws IOException, CborException {
		peekDataType();
		long value = header.getValue();
		if (header.getMajorType() != Major.ARRAY) {
			throw new CborIncorrectMajorTypeException(header, Major.ARRAY);
		}
		if (value == DataType.INDETERMINATE) {
			header = null;
			return Optional.empty();
		}
		if (value > Integer.MAX_VALUE) {
			throw new ArithmeticException("length " + value + " larger than " + Integer.MAX_VALUE);
		}
		header = null;
		return Optional.of((int)value);
	}

	@Override
	public int readMapPairCount() throws IOException, CborException {
		peekDataType();
		long value = header.getValue();
		if (header.getMajorType() != Major.MAP) {
			throw new CborIncorrectMajorTypeException(header, Major.MAP);
		}
		if (value == DataType.INDETERMINATE) {
			throw new CborIndeterminateLengthException(header);
		}
		if (value > Integer.MAX_VALUE) {
			throw new ArithmeticException("length " + value + " larger than " + Integer.MAX_VALUE);
		}
		header = null;
		return (int)value;	
	}

	@Override
	public Optional<Integer> readPossiblyIndefiniteMapPairCount() throws IOException, CborException {
		peekDataType();
		long value = header.getValue();
		if (header.getMajorType() != Major.MAP) {
			throw new CborIncorrectMajorTypeException(header, Major.MAP);
		}
		if (value == DataType.INDETERMINATE) {
			header = null;
			return Optional.empty();
		}
		if (value > Integer.MAX_VALUE) {
			throw new ArithmeticException("length " + value + " larger than " + Integer.MAX_VALUE);
		}
		header = null;
		return Optional.of((int)value);
	}

	@Override
	public void readBreak() throws IOException, CborException {
		peekDataType();
		if (header.equals(DataType.BREAK)) {
			header = null;
			return;
		}
		throw new CborException("Expected break, got " + header);
	}
	@Override
	public LogicalType peekLogicalType() throws IOException, CborException {
		return LogicalType.fromDataType(peekDataType());
	}
}
