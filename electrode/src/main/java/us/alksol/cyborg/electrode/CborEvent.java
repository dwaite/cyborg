package us.alksol.cyborg.electrode;

import java.math.BigInteger;

import us.alksol.bytestring.Bytes;

public interface CborEvent {
	public enum Type {
		INTEGER,
		NEGATIVE_INTEGER,
		HALF_FLOAT,
		FLOAT,
		DOUBLE,
		TRUE					(true,  false, false),
		FALSE					(true,  false, false),
		NULL					(true,  false, false),
		UNDEFINED				(true,  false, false),
		OTHER_SIMPLE			(true,  false, false),
		TAG						(false, true,  false),
		BINARY_CHUNK			(false, false, true),
		TEXT_CHUNK				(false, false, true),
		START_BINARY_CHUNKS		(false, true,  false),
		START_TEXT_CHUNKS		(false, true,  false),
		START_ARRAY_ELEMENTS	(false, true,  false),
		START_MAP_ENTRIES		(false, true,  false),
		BREAK;
		
		private final boolean simple;
		private final boolean container;
		private final boolean followedByBinaryData;
		
		private Type(boolean isSimple, boolean isContainer, boolean followedByBinaryData) {
			this.simple = isSimple;
			this.container = isContainer;
			this.followedByBinaryData = followedByBinaryData;
		}
		private Type() {
			this(false, false,false);
		}
		
		public boolean isContainer() {
			return container;
		}
		
		public boolean isSimple() {
			return simple;
		}
		
		public boolean isFollowedByBinaryData() {
			return followedByBinaryData;
		}
		
		public static Type fromDataType(InitialByte type) {
			if (type == InitialByte.BREAK) {
				return BREAK;
			}
			if (type == InitialByte.FALSE) {
				return FALSE;
			}
			if (type == InitialByte.NULL) {
				return NULL;
			}
			if (type == InitialByte.TRUE) {
				return TRUE;
			}
			if (type == InitialByte.UNDEFINED) { 
				return UNDEFINED;
			}
			switch(type.getMajorType()) {
			case INTEGER:
				return INTEGER;
			case NEGATIVE_INTEGER:
				return NEGATIVE_INTEGER;
			case ARRAY:
				return START_ARRAY_ELEMENTS;
			case MAP:
				return START_MAP_ENTRIES;
			case TAG:
				return TAG;
			case BYTE_STRING:
				switch (type.getAdditionalInfoFormat()) {
				case INDEFINITE:
					return START_BINARY_CHUNKS;
				default:
					return BINARY_CHUNK;
				}
			case TEXT_STRING:
				switch (type.getAdditionalInfoFormat()) {
				case INDEFINITE:
					return START_TEXT_CHUNKS;
				default:
					return TEXT_CHUNK;
				}
			case ETC:
				switch (type.getAdditionalInfoFormat()) {
				case IMMEDIATE:
				case BYTE:
					return OTHER_SIMPLE;
				case SHORT:
					return HALF_FLOAT;
				case INT:
					return FLOAT;
				case LONG:
					return DOUBLE;
				default:
					throw new IllegalArgumentException();
				}
			default:
				throw new IllegalArgumentException();
			}			
		}
	}
	
	InitialByte getDataType();
	Type getType();

	// works for all types. Value is a 64 bit binary value that may represent
	// - an unsigned integral value
	// - a negated integral value (via ones complement)
	// - a binary64 (double floating point) number
	// - a binary32 (float) or binary16 number (padded with zeros in most significant bits)
	// - the type identifier of a simple data item
	// - the count of a bytes in a binary or text chunk
	// - the count of child data items in an array
	// - the count of key and value data item pairs in a map
	// or zero if the value is the start or end of an indefinite map, array or text/binary chunk
	long rawValue();
	
	// integer, negative integer, tag, simple types
	int intValue() throws IllegalStateException, ArithmeticException;
	long longValue() throws IllegalStateException;
	BigInteger bigIntegerValue() throws IllegalStateException;
	
	// array, map, chunk, tag types. 
	// count returns -1 for invariant. always returns 1 for tags
	boolean isIndefiniteLengthContainer();
	int count() throws CborException;
	
	// half float / binary16
	short halfFloatValue() throws IllegalStateException;
			
	float floatValue() throws IllegalStateException;
	
	double doubleValue() throws IllegalStateException;
	
	// true and false
	boolean booleanValue() throws IllegalStateException;
	
	// all types, true for null
	boolean isNull();

	// all types, true for undefined
	boolean isUndefined();

	// binary and text chunks. Null for other types
	Bytes bytes();
	
	// text chunks (UTF-8 of bytes())
	String asTextValue();
	
	// all types, true for break
	boolean isBreak();

	// all types, true for breaks not synthesized to end fixed-length arrays and maps
	boolean isLiteralBreak();
	
	// view to provide API for handling potentially null values
	CborOptionalValue optional();
}