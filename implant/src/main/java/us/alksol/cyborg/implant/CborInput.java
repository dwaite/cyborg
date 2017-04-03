package us.alksol.cyborg.implant;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Consumer;

import us.alksol.cyborg.implant.DataType;

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
	/** peek {@link LogicalType} the cursor is currently pointing at */
	default LogicalType peekType() throws IOException, CborException {
		return peekDataType().getLogicalType();
	}

	/** peek the {@link DataType} the cursor is currently pointing at */
	DataType peekDataType() throws IOException, CborException;

	// if the logical type is an integer, read the value. Will throw Arithmetic exception rather than narrowing to
	// the java type
	int readInteger() throws IOException, CborException, ArithmeticException;
	// if the logical type is an integer, read the value. Will throw Arithmetic exception rather than narrowing to
	// the java type
	long readLong() throws IOException, CborException;
	
	long readUnsignedLong() throws IOException, CborException;
	long readNegativeLong() throws IOException, CborException;
	BigInteger readBigInteger() throws IOException, CborException;
	
	// read binary data. The consumer will be called once for a byte string of defined length, or one time for each
	// chunk in an indeterminate length byte string.
	void readBytes(Consumer<byte[]> byteBlockConsumer) throws CborException, IOException;

	// read binary data. Indeterminate length byte strings will be concatenated and returned as a single array
	byte[] readBytes() throws IOException, CborException;

	// read text data. The consumer will be called once for a text string of defined length, or one time for each
	// chunk in an indeterminate length text string
	String readText() throws IOException, CborException;

	// read text data. Indeterminate length text strings will be concatenated and returned as a single String
	void readText(Consumer<String> textBlockConsumer) throws CborException, IOException;

	// read a IEEE binary32 aka Java <code>float</code> floating-point number. No narrowing or widening is done
	// between other floating point formats, instead <code>ArithmeticException</code> will be thrown
	float  readFloat() throws IOException, CborException, ArithmeticException;
	// read a IEEE binary64 aka Java <code>double</code> floating-point number. No narrowing or widening is done
	// between other floating point formats, instead <code>ArithmeticException</code> will be thrown
	double readDouble() throws IOException, CborException, ArithmeticException;
	// read a IEEE binary16 floating-point number as a binary value. As Java does not support binary16 numbers,
	// this value is left within the bits of a 16-bit Java <code>short</code>.  No narrowing or widening is done
	// between other floating point formats, instead <code>ArithmeticException</code> will be thrown
	short readHalfFloat() throws IOException, CborException, ArithmeticException;

	// read a semantic tag that will apply to the next data item in the CBOR stream. If the tag does not fit within
	// a java integer <code>ArithmeticException</code> will be thrown
	int readIntTag() throws IOException, CborException, ArithmeticException;

	// read a semantic tag that will apply to the next data item in the CBOR stream. If the tag does not fit within
	// a java integer <code>ArithmeticException</code> will be thrown
	long readTag() throws IOException, CborException;

	// read the count of array elements which are children of an array data item. Will throw a CborException if
	// the array is not of a definite length
	int readArrayCount() throws IOException, CborException;

	// read the count of array elements which are children of an array data item. Will return an empty Optional to
	// represent an indefinite length array
	Optional<Integer> readPossiblyIndefiniteArrayCount() throws IOException, CborException;
	
	// read the count of alternating key/value elements pairs which are children of an map data item. This will be
	// half of the actual element count to accomodate the presence of both keys and values. Will throw a
	// CborException if the map is not of a definite length
	int readMapPairCount() throws IOException, CborException;
	
	// read the count of alternating key/value elements pairs which are children of an map data item. This will be
	// half of the actual element count to accomodate the presence of both keys and values. Will return an empty
	// Optional to represent an indefinite length map
	Optional<Integer> readPossiblyIndefiniteMapPairCount() throws IOException, CborException;
	
	// read a boolean value, returning true or false. Will not attempt to interpret values such as zero or `null`
	// as a boolean value, instead throwing a CborException
	boolean readBoolean() throws IOException, CborException;
	
	// read a null value to advance the cursor
	void readNull() throws IOException, CborException;
	
	// read an undefined value to advance the cursor
	void readUndefined() throws IOException, CborException;
	
	// read a simple value (including predefined types like booleans, null, and undefined) as a simple value.
	int readSimpleValue() throws IOException, CborException;
	
	// read a break terminating a indefinite length array or map. Indefinite length binary and text strings have any
	// terminating break consumed by their read methods.
	void readBreak() throws IOException, CborException;

	// read the data item as a binary CBOR block. This means:
	// - consuming all descendent data items in an array or map
	// - consuming all blocks in a indefinite-length binary or text string
	// - consuming both a tag and the tagged item
	void readCBOR(CborOutput output) throws IOException, CborException;
	
	default byte[] readCBOR() throws IOException, CborException {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataOutputStream dos = new DataOutputStream(baos)) {
			CborDataOutput output = new CborDataOutput(dos);
			readCBOR(output);
			dos.close();
			return baos.toByteArray();
		}
	}
	
	// return true if the type is an array, map, binary string or text string of indeterminate length. Return false
	// for other types, including the <code>break</code> terminator.
	default boolean isIndeterminate() throws IOException, CborException {
		return peekDataType().isIndeterminate();
	}
}
