package us.alksol.cyborg.implant;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

// represents input from some CBOR document or stream as an advancing cursor proceeding depth-first through the 
// CBOR data items.
// 
// Note that for all methods, an exception of type CborException (or sub-types) or ArithmeticException does not 
// advance the cursor. However, other errors (such as IOException) not only may advance the cursor, but may leave
// the underlying stream at an undefined position, yielding undefined behavior
//
// While whole number/integer values are represented as a single type, there is no "narrowing" operations permitted
// by the API. For instance, attempting to use readInteger() (which returns an `int`) when the data item references
// a value which only fits within a long will result in an Arithmetic error
//
// Likewise, no attempt is made at converting between floating point types. Attempt to read a float when the data
// item is a double will fail, likewise vice-versa will fail.
public interface CborInput {
	// represents a logical CBOR input type, combining all integer formats and splitting the 7th major type into 
	// logical constituents. Each type corresponds to a readXXX method on the CborInput interface to consume the
	// data item, advancing the input cursor.
	enum LogicalType {
		// represents all whole number values, positive and negative, fitting within a signed 64-bit integer
		INTEGER,
		// represents a byte string, in one or more chunks
		BYTES,
		// represents a text string, in one or more chunks
		TEXT,
		// represents the start of an array, of possibly indeterminate length
		ARRAY,
		// represents the start of a map/associative array, of possibly indeterminate length
		MAP,
		// represents a semantic tag for the following item
		TAG,
		// represents a boolean true or false
		BOOLEAN,
		// represents the concept of a null or nil value
		NULL,
		// represents an undefined value
		UNDEFINED,
		// represents a simple value other than a boolean, NULL, and UNDEFINED
		SIMPLE,
		// represents a binary16 value
		HALF_FLOAT,
		// represents a binary32 value
		FLOAT,
		// represents a binary64 value
		DOUBLE,
		// represents the end of a indeterminate array or map. Indeterminate length byte and text strings 
		// automatically consume any necessary BREAK
		BREAK;
		
		public static LogicalType fromDataType(DataType type) {
			switch (type.getMajorType()) {
			case UNSIGNED_INT:
			case NEGATIVE_INT:
				return INTEGER;
			case BYTE_STRING:
				return BYTES;
			case TEXT_STRING:
				return TEXT;
			case ARRAY:
				return ARRAY;
			case MAP:
				return MAP;
			case TAG:
				return TAG;
			case SIMPLE_FLOAT:
				switch (type.getAdditionalInfo()) {
				case IMMEDIATE:
				case BYTE:
				case INDETERMINATE:
					if (type.equals(DataType.TRUE) || type.equals(DataType.FALSE)) {
						return BOOLEAN;
					}
					if (type.equals(DataType.NULL)) {
						return NULL;
					}
					if (type.equals(DataType.UNDEFINED)) {
						return UNDEFINED;
					}
					if (type.equals(DataType.BREAK)) {
						return BREAK;
					}
					return SIMPLE;
				case SHORT:
					return HALF_FLOAT;
				case INT:
					return FLOAT;
				case LONG:
					return DOUBLE;
				}
			}
			throw new IllegalStateException();
		}
	}
	
	boolean readBoolean() throws IOException, CborException;
	void readNull() throws IOException, CborException;
	void readUndefined() throws IOException, CborException;
	int readSimpleValue() throws IOException, CborException;
	
	int readInteger() throws IOException, CborException;
	long readLong() throws IOException, CborException;

	byte[] readBytes() throws IOException, CborException;
	String readText() throws IOException, CborException;
	void readText(Consumer<String> textBlockConsumer) throws CborException, IOException;
	void readBytes(Consumer<byte[]> byteBlockConsumer) throws CborException, IOException;

	float  readFloat() throws IOException, CborException;
	double readDouble() throws IOException, CborException;
	short readHalfFloat() throws IOException, CborException;

	int readTag() throws IOException, CborException;
	long readLongTag() throws IOException, CborException;

	int readArrayCount() throws IOException, CborException;
	Optional<Integer> readPossiblyIndefiniteArrayCount() throws IOException, CborException;
	
	int readMapPairCount() throws IOException, CborException;
	Optional<Integer> readPossiblyIndefiniteMapPairCount() throws IOException, CborException;
	
	void readBreak() throws IOException, CborException;

	LogicalType peekLogicalType() throws IOException, CborException;
	DataType peekDataType() throws IOException, CborException;
}
