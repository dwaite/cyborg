package us.alksol.cyborg.implant;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Consumer;

import us.alksol.cyborg.implant.DataType.InfoFormat;
import us.alksol.cyborg.implant.DataType.Major;

/** The CborInput interface provides a low-level API for reading CBOR data from a document or stream as a sequence
 *  of {@link DataType} instances and associated data, rather than as a hierarchical document model or data 
 *  structure.
 *  
 *  This API operates with a forward-only cursor model, with `peekXXX` and any getter methods inspecting the
 *  current cursor position and `readXXX` methods attempting to advance the cursor to the start of a future data
 *  item. For all methods, an exception of type {@link CborException} or a runtime exception like 
 *  {@link ArithmeticException} does not advance the cursor. So for example, a failing 
 *  {@link CborInput#readFloat()} due to the data item being a double might be recovered by a
 *  {@link CborInput#readDouble()}. However, other errors (such as {@link IOException}) not only may advance the 
 *  cursor, but may leave the underlying stream at an undefined position and cause undefined behavior.
 *  
 * While whole number/integer values are represented as a single type, there is no lossy "narrowing" operations 
 * permitted by the API. For instance, attempting to use {@link CborInput#readInteger()} (which returns a java 
 * `int`) when the data item's value only fits within a java `long` will result in an exception.
 * 
 * Likewise, no attempt is made at converting between floating point types. Attempt to read a float when the data
 * item is a double will fail, likewise vice-versa will fail.
 */
public interface CborInput {

	/** peek the {@link DataType} the cursor is currently pointing at. 
	 * Note that the underlying implementation **may** need to consume the data type (1-5 bytes) in order to read
	 * ahead if not based on an {@link InputStream} with {@link InputStream#markSupported()}, but 
	 * in these cases should still cache the data type so that repeated calls return the same value without
	 * further consuming data. */
	DataType peekDataType() throws IOException, CborException;

	/**
	 * Read the data type the cursor points at, advancing the cursor. This only works for types which
	 * are never followed by binary data - attempts to call this for a binary or text stream will result
	 * in a {@link CborException}.
	 * 
	 * @return data type the element points at
	 * @throws IOException on issue with underlying data stream
	 * @throws CborException if the data type is a binary or text stream.
	 */
	DataType consumeDataType() throws IOException, CborException;
	
	/** peek {@link LogicalType} the cursor is currently pointing at
	 * Note that the underlying implementation **may** need to consume the major byte (1 byte) or 
	 * data type (1-5 bytes) in order to read ahead if not based on an {@link InputStream} with 
	 * {@link InputStream#markSupported()}, but in these cases should still cache the data type so
	 * that repeated calls return the same value without further consuming data. */
	default LogicalType peekType() throws IOException, CborException {
		return peekDataType().getLogicalType();
	}

	/** Read the value if the CBOR data item is a major type 0 {@link Major#UNSIGNED_INT}. Will return
	 * the value as an unsigned 64-bit value within a java signed long, which will result in the value
	 * appearing negative if greater than {@link Long#MAX_VALUE}.
	 * 
	 * @throws CborException if data type is not {@link Major#UNSIGNED_INT}
	 */
	default long readUnsignedLong() throws IOException, CborException {
		peekDataType().assertMajorType(Major.UNSIGNED_INT);
		return consumeDataType().getValue();
	}

	/** Read the value if the CBOR data item is a major type 0 {@link Major#NEGATIVE_INT}. This value is
	 * equivalent to -(result + 1), or the ones-complement value (e.g. ~result). Will return the value
	 * as an unsigned 64-bit value within a java signed long, which will result in the value
	 * appearing negative if greater than {@link Long#MAX_VALUE}.
	 * 
	 * @throws CborException if data type is not {@link Major#NEGATIVE_INT}
	 */
	default long readNegativeLong() throws IOException, CborException {
		peekDataType().assertMajorType(Major.NEGATIVE_INT);
		return consumeDataType().getValue();
	}
	
	/** Read the value if the CBOR data item is an unsigned or negative integral value.
	 * 
	 *  The value is returned as a 64-bit signed long, which cannot represent all the integral values that
	 *  CBOR is capable of. If the value is outside the range representable by a signed long, an
	 *  ArithmeticException will be raised rather than narrowing the value.
	 *  
	 * @throws CborException if the data type is not {@value Major#UNSIGNED_INT} or {@value Major#NEGATIVE_INT}
	 * @throws ArithmeticException if the value cannot fit within a signed 64-bit long integer.
	 */
	default long readLong() throws IOException, CborException, ArithmeticException {
		DataType dataType = peekDataType();
		dataType.assertMajorType(Major.UNSIGNED_INT, Major.NEGATIVE_INT);
		// reading the value by default will throw ArithmeticException if it doesn't fit within a signed long
		long rawValue = dataType.getValue();
		consumeDataType();
		switch (dataType.getMajorType()) {
		case UNSIGNED_INT:
			return rawValue;
		case NEGATIVE_INT:
			return ~rawValue;
		default:
			throw new IllegalStateException();
		}
	}
	
	/** Read the value if the CBOR data item is an unsigned or negative integral value.
	 * 
	 *  The value is returned as a 32-bit signed long, which cannot represent all the integral values that
	 *  CBOR is capable of. If the value is outside the range representable by a signed int, an
	 *  ArithmeticException will be raised rather than narrowing the value.
	 *  
	 * @throws CborException if the data type is not {@value Major#UNSIGNED_INT} or {@value Major#NEGATIVE_INT}
	 * @throws ArithmeticException if the value cannot fit within a signed 32-bit integer.
	 */
	default int readInteger() throws IOException, CborException, ArithmeticException {
		DataType dataType = peekDataType();
		dataType.assertMajorType(Major.UNSIGNED_INT, Major.NEGATIVE_INT);
		int rawValue = dataType.getValueAsInt();
		consumeDataType();
		switch (dataType.getMajorType()) {
		case UNSIGNED_INT:
			return rawValue;
		case NEGATIVE_INT:
			return ~rawValue;
		default:
			throw new IllegalStateException();
		}
	}

	default BigInteger readBigInteger() throws IOException, CborException {
		DataType dataType = peekDataType();
		dataType.assertMajorType(Major.UNSIGNED_INT, Major.NEGATIVE_INT);
		consumeDataType();
		switch (dataType.getMajorType()) {
		case UNSIGNED_INT:
			return dataType.getValueAsBigInteger();
		case NEGATIVE_INT:
			return dataType.getValueAsBigInteger().negate();
		default:
			throw new IllegalStateException();
		}
	}
	
	// read binary data. The consumer will be called once for a byte string of defined length, or one time for each
	// chunk in an indeterminate length byte string.
	void readBytes(Consumer<byte[]> byteBlockConsumer) throws CborException, IOException;

	// read binary data. Indeterminate length byte strings will be concatenated and returned as a single array
	default byte[] readBytes() throws IOException, CborException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		readBytes((block) -> bos.write(block, 0, block.length));
		return bos.toByteArray();
	}

	// read text data. The consumer will be called once for a text string of defined length, or one time for each
	// chunk in an indeterminate length text string
	default String readText() throws IOException, CborException {
		StringWriter sw = new StringWriter();
		readText((block) -> sw.write(block));
		return sw.toString();
	}

	// read text data. Indeterminate length text strings will be concatenated and returned as a single String
	void readText(Consumer<String> textBlockConsumer) throws CborException, IOException;

	// read a boolean value, returning true or false. Will not attempt to interpret values such as zero or `null`
	// as a boolean value, instead throwing a CborException
	default boolean readBoolean() throws IOException, CborException {
		DataType dataType = peekDataType();
		dataType.assertMajorType(Major.ETC);
		boolean result = dataType.getValueAsBoolean();
		consumeDataType();
		return result;
	}
	
	// read a null value to advance the cursor
	default void readNull() throws IOException, CborException {
		DataType dataType = peekDataType();
		dataType.assertMajorType(Major.ETC);

		if (dataType.equals(DataType.NULL)) {
			consumeDataType();
			return;
		}
		throw new CborException("Expected null type, got " + dataType);
	}

	// read an undefined value to advance the cursor
	default void readUndefined() throws IOException, CborException {
		DataType dataType = peekDataType();
		dataType.assertMajorType(Major.ETC);

		if (dataType.equals(DataType.UNDEFINED)) {
			consumeDataType();
			return;
		}
		throw new CborException("Expected undefined type, got " + dataType);
	}

	// read a simple value (including predefined types like booleans, null, and undefined)
	// as a simple value index.
	default int readSimpleValue() throws IOException, CborException {
		DataType dataType = peekDataType();
		dataType.assertMajorType(Major.ETC);
		switch (dataType.getAdditionalInfo()) {
		case IMMEDIATE:
		case BYTE:
			int value = (int) dataType.getValue();
			consumeDataType();
			return value;
		default:
			throw new CborException("Expected simple type, got " + dataType);
		}
	}

	// read a IEEE binary32 aka Java <code>float</code> floating-point number. No narrowing or widening is done
	// between other floating point formats, instead <code>ArithmeticException</code> will be thrown
	default float readFloat() throws IOException, CborException {
		DataType dataType = peekDataType();
		dataType.assertMajorType(Major.ETC);
		long value = dataType.getValue();
		if (dataType.getAdditionalInfo() != InfoFormat.INT) {
			throw new CborException("data type " + dataType + " does not represent a binary32 A.K.A. float");
		}
		float floatValue = Float.intBitsToFloat((int)value);
		consumeDataType();
		return floatValue;
	}

	// read a IEEE binary64 aka Java <code>double</code> floating-point number. No narrowing or widening is done
	// between other floating point formats, instead <code>ArithmeticException</code> will be thrown
	default double readDouble() throws IOException, CborException, ArithmeticException {
		DataType dataType = peekDataType();
		dataType.assertMajorType(Major.ETC);
		long value = dataType.getValue();
		if (dataType.getAdditionalInfo() != InfoFormat.LONG) {
			throw new CborException("data type " + dataType + " does not represent a binary64 A.K.A. double");
		}
		double doubleValue = Double.longBitsToDouble(value);
		consumeDataType();
		return doubleValue;
	}

	// read a IEEE binary16 floating-point number as a binary value. As Java does not support binary16 numbers,
	// this value is left within the bits of a 16-bit Java <code>short</code>.  No narrowing or widening is done
	// between other floating point formats, instead <code>ArithmeticException</code> will be thrown
	default short readHalfFloat() throws IOException, CborException, ArithmeticException {
		DataType dataType = peekDataType();
		dataType.assertMajorType(Major.ETC);
		long value = dataType.getValue();
		if (dataType.getAdditionalInfo() != InfoFormat.SHORT) {
			throw new CborException("data type " + dataType + " does not represent a binary16 half float");
		}
		consumeDataType();
		return (short) value;
	}

	// read a semantic tag that will apply to the next data item in the CBOR stream. If the tag does not fit within
	// a java integer <code>ArithmeticException</code> will be thrown
	default int readIntTag() throws IOException, CborException, ArithmeticException {
		DataType dataType = peekDataType();
		dataType.assertMajorType(Major.TAG);
		long value = dataType.getValue();
		if (value < 0 || value > Integer.MAX_VALUE) {
			throw new ArithmeticException("value " + value + " larger than " + Integer.MAX_VALUE);
		}
		consumeDataType();
		return (int) value;
	}
	
	// read a semantic tag that will apply to the next data item in the CBOR stream. If the tag does not fit within
	// a java integer <code>ArithmeticException</code> will be thrown
	default long readTag() throws IOException, CborException {
		DataType dataType = peekDataType();
		dataType.assertMajorType(Major.TAG);
		long value = dataType.getValue();
		if (value < 0) {
			throw new ArithmeticException("value " + value + " larger than " + Long.MAX_VALUE);
		}
		consumeDataType();
		return value;
	}

	// read a semantic tag that will apply to the next data item in the CBOR stream.
	default long readUnsignedTag() throws IOException, CborException {
		DataType dataType = peekDataType();
		dataType.assertMajorType(Major.TAG);
		return consumeDataType().getValue();
	}

	// read the count of array elements which are children of an array data item. Will throw a CborException if
	// the array is not of a definite length
	default int readArrayCount() throws IOException, CborException {
		DataType dataType = peekDataType();
		dataType.assertMajorType(Major.ARRAY);
		if (dataType.isIndeterminate()) {
			throw new CborIndeterminateLengthException(dataType);
		}
		int value = dataType.getValueAsInt();
		consumeDataType();
		return value;
	}

	// read the count of array elements which are children of an array data item. Will return an empty Optional to
	// represent an indefinite length array
	default Optional<Integer> readPossiblyIndefiniteArrayCount() throws IOException, CborException {
		DataType dataType = peekDataType();
		dataType.assertMajorType(Major.ARRAY);
		int value = dataType.getValueAsInt();
		if (dataType.isIndeterminate()) {
			consumeDataType();
			return Optional.empty();
		}
		consumeDataType();
		return Optional.of(value);
	}

	// read the count of alternating key/value elements pairs which are children of an map data item. This will be
	// half of the actual element count to accomodate the presence of both keys and values. Will throw a
	// CborException if the map is not of a definite length
	default int readMapPairCount() throws IOException, CborException {
		DataType dataType = peekDataType();
		dataType.assertMajorType(Major.MAP);
		if (dataType.isIndeterminate()) {
			throw new CborIndeterminateLengthException(dataType);
		}
		int value = dataType.getValueAsInt();
		consumeDataType();
		return value;
	}

	// read the count of alternating key/value elements pairs which are children of an map data item. This will be
	// half of the actual element count to accomodate the presence of both keys and values. Will return an empty
	// Optional to represent an indefinite length map
	default Optional<Integer> readPossiblyIndefiniteMapPairCount() throws IOException, CborException {
		DataType dataType = peekDataType();
		dataType.assertMajorType(Major.MAP);
		int value = dataType.getValueAsInt();
		if (dataType.isIndeterminate()) {
			consumeDataType();
			return Optional.empty();
		}
		consumeDataType();
		return Optional.of(value);
	}

	// read a break terminating a indefinite length array or map. Indefinite length binary and text strings have any
	// terminating break consumed by their read methods.
	default void readBreak() throws IOException, CborException {
		DataType dataType = peekDataType();
		dataType.assertMajorType(Major.ETC);
		if (dataType.equals(DataType.BREAK)) {
			consumeDataType();
			return;
		}
		throw new CborException("Expected break, got " + dataType);
	}

	// return true if the type is an array, map, binary string or text string of indeterminate length. Return false
	// for other types, including the <code>break</code> terminator.
	default boolean isIndeterminate() throws IOException, CborException {
		return peekDataType().isIndeterminate();
	}
	
	// read the data item as a binary CBOR block. This means:
	// - consuming all descendent data items in an array or map
	// - consuming all blocks in a indefinite-length binary or text string
	// - consuming both a tag and the tagged item
	default void readCBOR(CborOutput output) throws IOException, CborException {
		CborInputOutput io = new CborInputOutput(this, output);
		io.readDataItem();
	}
	
	default byte[] readCBOR() throws IOException, CborException {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				DataOutputStream dos = new DataOutputStream(baos)) {
			CborDataOutput output = new CborDataOutput(dos);
			readCBOR(output);
			dos.close();
			return baos.toByteArray();
		}
	}
}
