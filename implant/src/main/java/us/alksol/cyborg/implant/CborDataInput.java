package us.alksol.cyborg.implant;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.function.Consumer;

import us.alksol.cyborg.implant.DataType.InfoFormat;
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
		if (header.getMajorType() != Major.ETC) {
			throw new CborIncorrectMajorTypeException(header, Major.ETC);
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
		if (header.getMajorType() != Major.ETC) {
			throw new CborIncorrectMajorTypeException(header, Major.ETC);
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
		if (header.getMajorType() != Major.ETC) {
			throw new CborIncorrectMajorTypeException(header, Major.ETC);
		}
		throw new CborException("Expected undefined type, got " + header);
	}

	@Override
	public int readSimpleValue() throws IOException, CborException {
		peekDataType();
		if (header.getMajorType() != Major.ETC) {
			throw new CborIncorrectMajorTypeException(header, Major.ETC);
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
		if (header.getMajorType() != Major.TEXT_STRING) {
			throw new CborIncorrectMajorTypeException(header, Major.TEXT_STRING);
		}
		Charset utf8 = Charset.forName("UTF-8");
		if (peekDataType().isIndeterminate()) {
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
		if (header.getMajorType() != Major.BYTE_STRING) {
			throw new CborIncorrectMajorTypeException(header, Major.BYTE_STRING);
		}
		if (peekDataType().isIndeterminate()) {
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
		if (header.getMajorType() != Major.ETC) {
			throw new CborIncorrectMajorTypeException(header, Major.ETC);
		}
		if (header.getAdditionalInfo() != InfoFormat.INT) {
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
		if (header.getMajorType() != Major.ETC) {
			throw new CborIncorrectMajorTypeException(header, Major.ETC);
		}
		if (header.getAdditionalInfo() != InfoFormat.LONG) {
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
		if (header.getMajorType() != Major.ETC) {
			throw new CborIncorrectMajorTypeException(header, Major.ETC);
		}
		if (header.getAdditionalInfo() != InfoFormat.SHORT) {
			throw new CborException("data type " + header + " does not represent a binary16 half float");
		}
		header = null;
		return (short) value;
	}

	@Override
	public int readIntTag() throws IOException, CborException {
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
	public long readTag() throws IOException, CborException {
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
		if (peekDataType().isIndeterminate()) {
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
		if (peekDataType().isIndeterminate()) {
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
		if (peekDataType().isIndeterminate()) {
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
		if (peekDataType().isIndeterminate()) {
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
	public long readUnsignedLong() throws IOException, CborException {
		throw new UnsupportedOperationException();
	}
	@Override
	public long readNegativeLong() throws IOException, CborException {
		throw new UnsupportedOperationException();
	}
	@Override
	public BigInteger readBigInteger() throws IOException, CborException {
		throw new UnsupportedOperationException();
	}
	@Override
	public void readCBOR(CborOutput output) throws IOException, CborException {
		throw new UnsupportedOperationException();
	}
}
