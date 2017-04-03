package us.alksol.cyborg.implant;

import us.alksol.cyborg.implant.DataType.Major;

/** 
 * Represents a logical CBOR input type.
 * 
 * This is different from @link {@link Major} in that it combines both integer formats, and splits the 7th major
 * type into logical constituents. Each type is meant to correspond to a `readXXX` method on the {@link CborInput}
 * interface or a `writeXXX` method on the @link {@link CborOutput} interface.
 */
public enum LogicalType {
	/** represents all whole number values, positive and negative, fitting within a signed 64-bit integer */
	INTEGER,
	
	/** represents a byte string, in one or more chunks */
	BYTES,
	
	/** represents a text string, in one or more chunks */
	TEXT,
	
	/** represents the start of an array, of possibly indeterminate length */
	ARRAY,
	
	/** represents the start of a map/associative array, of possibly indeterminate length */
	MAP,
	
	/** represents a semantic tag for the following item */
	TAG,
	
	/** represents a boolean true or false */
	BOOLEAN,
	
	/** represents the concept of a null or nil value */
	NULL,
	
	/** represents an undefined value */
	UNDEFINED,
	
	/** represents a simple value other than a boolean, NULL, and UNDEFINED */
	SIMPLE,
	
	/** represents a binary16 value */
	HALF_FLOAT,

	/** represents a binary32 value */
	FLOAT,

	/** represents a binary64 value */
	DOUBLE,
	
	/** represents the end of a indeterminate array or map. Indeterminate length byte and text strings
	 * automatically consume any necessary BREAK
	 */
	BREAK;

	/**
	 * Determine logical type from provided data type
	 * @param type data type
	 * @return logical type
	 */
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
		case ETC:
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