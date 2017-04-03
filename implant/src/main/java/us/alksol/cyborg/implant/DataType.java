package us.alksol.cyborg.implant;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Objects;

/** A Data type represents the description of a CBOR data item, minus binary streams or contained data items. This
 *  might be considered somewhat like the 'header' of a data item, although some data types (such as integers 
 *  and simple types) actually contain all the necessary information to be data items.
 *  
 *  A data type contains the following parts:
 *  
 *  * The major type ({@link DataType.Major}), representing the kind of CBOR data item
 *  * The additional info value ({@link DataType#getValue()}), containing additional information for the CBOR data
 *    item, such as the value of an integer or the length of an array
 *  * An information format ({@link DataType.InfoFormat}) giving details on the format of the value. This is needed
 *    both to fully represent the binary format of the data type, and to provide minor type information in the case
 *    of {@link Major#ETC}.
 *  
 *                                                
 * ```                                                   
 *                    Data Item                        
 * +--------------------------------------------------+
 * |                                                  |
 *            Data Type                                
 * +----------------------------+                      
 * |                            |    + - - - - - - - - 
 *                                     Bytes...       |
 * +-------------+--------------+    + - - - - - - - - 
 * |  Major Type | Addl. Info + |    + - - - - - - - - 
 * |             | Info Format  |      Child Items... |
 * +-------------+--------------+    + - - - - - - - - 
 * 	```
 * 
 *  Where the term <em>data item</em> is used in the documentation of this class, it is meant to indicate a data
 *  type which is not a container or tag, thus actually representing a fully formed CBOR data item.
 */
public final class DataType {
	
	/** 
	 * Represents the major type of a data item in CBOR. 
	 * 
	 * @see [CBOR Major Data Types](https://tools.ietf.org/html/rfc7049#section-2.1) 
	 */
	public enum Major {
		/** major 0, unsigned integers from 0 to 2^^64-1. */
		UNSIGNED_INT(false, true, false),
		
		/** major 1, negative integers from -1 to -2^^64. */
		NEGATIVE_INT(false, true, false),
		
		/** major 2, a binary sequence. The value of the data major represents the byte length of the sequence */
		BYTE_STRING(true, false, true),
		
		/** major 3, a text string. The value of the data major represents the byte length of the string, 
		 * represented in UTF-8. */
		TEXT_STRING(true, false, true),
		
		/** major 4, an array of data items. The value of the data major represents the number of child items in 
		 * this array. */
		ARRAY(true, false, true),
		
		/** major 5, a map of data items. The value of the data major represents the number of key value pairs in 
		 * this map, where each key and value are allowed to be arbitrary CBOR data items. This is effectively 
		 * doubled to represent the number of child data items.
		 */
		MAP(true, false, true),
		
		/** major 6, a semantic tag for a data item. The value of the data major represents semantic interpretation
		 * of the single child data item.
		 */
		TAG(false, false, false),
		
		/** 
		 * major 7, shared between:
		 * 
		 * - simple values
		 * - floating point numbers
		 * - 'break' for terminating indeterminate length sequences, arrays and maps.
		 * 
		 * Simple values represent non-integer data values that require no additional content:
		 * 
		 * - boolean values
		 * - the concept of `null` or `nil`
		 * - undefined value
		 * 
		 * Additional simple types may be added in the future by standards and registered with the IANA
		 * 
		 * Floating point number formats for float and double are supported as well as half-width floating point, 
		 * which has traditionally been used mostly for graphical and GPU computation work.
		 *
		 * finally, the `break` signal can be represented by this major type. The `break` does not represent data,
		 * but rather the termination of an indefinite length binary / text string, array, or map
		 * 
		 * The @link {@link LogicalType} enumeration can be used to differentiate all of these options.
		 */
		ETC(false, true, true);

		private final boolean isContainer;
		private final boolean isCompleteDataItem;
		private final boolean isIndeterminateAllowed;

		private Major(boolean isContainer, boolean isCompleteDataItem, boolean isIndeterminateAllowed) {
			this.isContainer = isContainer;
			this.isCompleteDataItem = isCompleteDataItem;
			this.isIndeterminateAllowed = isIndeterminateAllowed;
		}
		
		/**
		 * Determine the appropriate major major based on the content of the initial byte of a data
		 * 
		 * @param initialByte initial byte of the CBOR data item
		 * @return Major type corresponding to initial byte
		 * @throws ArrayIndexOutOfBoundsException initialByte is not an unsigned byte (0x00 - 0xff)
		 */
		public static Major fromInitialByte(int initialByte) {
			// simply rely on the declaration order above
			return values()[initialByte >> 5];
		}

		/** determine the high order bits that this major represents when computing an initial byte */
		public int getHighOrderBits() {
			return ordinal() << 5;
		}
		
		/**
		 * Represents that this is can contain data items (including an indefinite # of items). A tag is not 
		 * considered a container.
		 */
		public boolean isContainer() {
			return this.isContainer;
		}
		
		/**
		 *  represents that this major type is self-contained within the additional information and format, and
		 *  does not contain any following data to form a complete data item.
		 */
		public boolean isCompleteDataItem() {
			return isCompleteDataItem;
		}

		/**
		 * represents that this major type allows the lower order bits to indicate the indeterminate value (e.g.
		 * either an indeterminate-length container or the `break` signal
		 */
		public boolean isIndeterminateAllowed() {
			return isIndeterminateAllowed;
		}
	}

	/**
	 * Represents the format of additional information, as well as differentiation of minor types.
	 * 
	 * The initial byte contains not just a major type in its high order bits, but additional information in the
	 * low order bits. This may be an immediate integer or special value, or may indicate the real additional 
	 * information follows in the next several bytes.
	 * 
	 * * For integer and tag types, this value lets us know if the integer was encoded canonically.
	 * * For container types (binary/text strings, arrays and maps) this also lets us know if the container
	 *   represents an indefinite number of items.
	 * * For {@link Major#ETC}, this helps us differentiate from the various possible items represented - simple
	 *   values, the various floating point number formats, and the `break` signal
	 */
	public enum InfoFormat {
		/** The integer values 0 through 23 can be encoded as immediate values within the initial byte itself. */
		IMMEDIATE(0),
		
		/** The value is encoded in a single byte following the initial byte */
		BYTE(24),
		
		/** The value is encoded in two bytes following the initial byte, in network byte order (big-endian). */
		SHORT(25),
		
		/** The value is encoded in four bytes following the initial byte, in network byte order (big-endian). */
		INT(26),
		
		/** The value is encoded in eight bytes following the initial byte, in network byte order (big-endian). */
		LONG(27),
		
		/** For container types, this indicates an indeterminate number of contained data items. For 
		 * {@link Major#ETC}, this indicates the `break` signal terminating such an indeterminate container. */
		INDETERMINATE(31);
		
		private final int lowOrderBits;
		
		private InfoFormat(int lowOrderBits) {
			this.lowOrderBits = lowOrderBits;
		}
		
		/** mask the low order bits representing the `InfoFormat` and any immediate value off from the initial byte */
		public static int maskBits(int initialByte) {
			return initialByte & 0x1f;
		}
		
		/** return the low order bits of the initial byte represented by this `InfoFormat` type. All immediate values are
		 * represented by the return value of `0`
		 */
		public int getLowOrderBits() {
			return lowOrderBits;
		}
		
		/** extract an InfoFormat from the initial byte.
		 * 
		 * @param initialByte initial byte of the CBOR data item
		 * @return InfoFormat, or null if the InfoFormat would be one of the three reserved values (28, 29, and 30)
		 */
		public static InfoFormat fromInitialByte(int initialByte) {
			final int lowOrderBits = maskBits(initialByte);
			if (lowOrderBits < 24) {
				return IMMEDIATE;
			} else {
				switch(lowOrderBits) {
				case 24:
					return BYTE;
				case 25:
					return SHORT;
				case 26:
					return INT;
				case 27:
					return LONG;
				case 31:
					return INDETERMINATE;
				default:
					return null;
				}
			}
		}
		
		/** 
		 * Determine the appropriate canonical (smallest) encoding of a given long value. 
		 * 
		 * @param value integer value to encode.	Note that while the long primitive type in Java is signed, this 
		 * long is interpreted as an unsigned long - all negative values are larger than {@link Long#MAX_VALUE}
		 * @return InfoFormat which holds the value in the canonical/smallest stream size
		 */
		public static InfoFormat canonicalFromLongValue(long value) {
			if (value < 0) {
				return LONG;
			}
			if (value < 24) {
				return IMMEDIATE;
			}
			if (value < 0x100) {
				return BYTE;
			}
			if (value < 0x10000) {
				return SHORT;
			}
			if (value < 0x100000000L)  {
				 return INT;
			}
			return LONG;
		}
	}
	
	// The major type
	private final Major major;
	
	// The informational format, and minor order bits for representing indefinite length containers and minor
	// values of Major#ETC
	private final InfoFormat format;
	
	// The additional info of the data item as a 64 bit binary value. This value may represent:
	// 
	// - an unsigned long integer value
	// - an unsigned long count of bytes
	// - an unsigned long number of data items or pairs of data items as immediate children
	// - an unsigned long tag identifier
	// - an unsigned byte index of a simple value
	// - a binary16 half-float
	// - a binary32 float
	// - a binary64 double
	// - for indefinite-length containers and the `break` signal, it will be set to zero
	//
	// Note that as the long primitive type in Java is signed, this long must be carefully interpreted for integer
	// values larger than {@link Long#MAX_VALUE}, which will be represented as negative numbers.
	private final long value;

	/** Construct a DataType from the constituent types. This performs minimal validation, and thus may either
	 * contain illegal state if misused or result in non-well-formed CBOR output
	 * 
	 * @param type Major type of document
	 * @param infoFormat information format for value
	 * @param value additional information represented as a 64-bit value
	 */
	private DataType(Major type, InfoFormat infoFormat, long value) {
		Objects.requireNonNull(type);
		Objects.requireNonNull(infoFormat);
		this.major = type;
		this.format = infoFormat;
		this.value = value;
	}

	// cache for all single-byte data types and data values (~ 200 entries). This represents all single byte values
	// with defined semantics, even simple types which aren't currently defined in specs.
	private static final DataType IMMEDIATES[];
	
	// convenience accessors of some of the cached single-byte data values
	
	/** Data item of the integer value zero */
	public static final DataType ZERO;

	/** Data item of the integer value one */
	public static final DataType ONE;
	
	/** Data item of the simple boolean value `false` */
	public static final DataType FALSE;
	
	/** Data item of the simple boolean value `true` */
	public static final DataType TRUE;
	
	/** Data item of the simple value `null`, representing the absence of value */
	public static final DataType NULL;
	
	/** Data item of the simple value `undefined`, representing a value is neither defined as having a value or 
	 * explicitly as having no value. */
	public static final DataType UNDEFINED;
	
	/** Data item representing the `break` signal, terminating an indefinite-length container. */
	public static final DataType BREAK;

	static {
		IMMEDIATES = new DataType[256];
		for (int i = 0; i < 256; i++) {
			Major major = Major.fromInitialByte(i);
			InfoFormat format = InfoFormat.fromInitialByte(i);
			// reserved format without defined semantics, skip
			if (format == null) {
				continue;
			}
			switch (format) {
			case IMMEDIATE:
				IMMEDIATES[i] = new DataType(major, format, InfoFormat.maskBits(i));
				break;
			case INDETERMINATE:
				// types like unsigned integers do not define the behavior if the minor bits are set to
				// indeterminate, so only handle cases where indeterminate is defined.
				if (major.isIndeterminateAllowed()) {
					IMMEDIATES[i] = new DataType(major, format, 0);
				}
				break;
			default:
				break;
			}
		}
		ZERO  = IMMEDIATES[0x00];
		ONE   = IMMEDIATES[0x01];
		FALSE = IMMEDIATES[0xf4];
		TRUE  = IMMEDIATES[0xf5];
		NULL  = IMMEDIATES[0xf6];
		UNDEFINED = IMMEDIATES[0xf7];
		BREAK = IMMEDIATES[0xff];
	}
	
	// constant big integer to convert values above Long#MAX_VALUE to a Java-compatible numeric
	private static final BigInteger TWO_TO_THE_64TH = 
			new BigInteger(1, new byte[] {0x1, 0, 0, 0, 0, 0, 0, 0, 0});

	/**
	 * Helper method to read a data type from a binary data input.
	 * 
	 * If one wishes to create their own compatible implementation of DataInput, the following methods are used on
	 * the interface:
	 * 
	 * - {@link DataInput#readUnsignedByte()}
	 * - {@link DataInput#readUnsignedShort()}
     * - {@link DataInput#readInt()}
     * - {@link DataInput#readLong()}
     * 
	 * @param input data input
	 * @return DataType
	 * @throws IOException - error reading from provided input
	 * @throws IllegalStateException - initial byte represents a reserved additional info format/minor value.
	 */
	public static DataType readDataType(DataInput input) throws IOException {
		int ib = input.readUnsignedByte();
		DataType type = IMMEDIATES[ib];
		if (type != null) {
			return type;
		}
		Major major = Major.fromInitialByte(ib);
		InfoFormat format = InfoFormat.fromInitialByte(ib);
		long value = readAdditionalInfo(input, format, ib);
		return new DataType(major, format, value);
	}

	/**
	 * Construct a data type solely from an initial byte, if possible
	 * @param ib initial unsigned byte of CBOR data item
	 * @return data type value, or `null` if a single byte representation isn't allowed.
	 * 
	 * @throws IllegalArgumentException if ib value is not an unsigned byte.
	 */
	public static DataType immediate(int ib) {
		if (ib > 0xff) {
			throw new IllegalArgumentException("ib must be an unsigned byte");
		}
		return IMMEDIATES[ib];
	}

	/** Construct a Data item representing a simple value 
	 * 
	 * @param value simple value as an unsigned byte, excluding the values from 24 through 31, which are reserved
	 * to avoid confusion.
	 * @return Data item representing simple value
	 * @throws IllegalArgumentException given value is not in the range appropriate for simple values
	 */
	public static DataType simpleValue(int value) {
		if (value < 0 || value > 255) {
			throw new IllegalArgumentException("value is outside expected ranges");
		}
		// attempt to return the static instances if possible
		if (value < 24) {
			return IMMEDIATES[0xe0 + value];
		}
		if (value >= 24 && value < 32) {
			throw new IllegalArgumentException("values between 24 and 32 are not legal");
		}
		InfoFormat format = value < 24 ? 
				InfoFormat.IMMEDIATE :
				InfoFormat.BYTE;
		return new DataType(Major.ETC, format, value);
	}

	/**
	 * Construct a data type from a major type and value. Note that the value must fit within a signed long for
	 * this method to be used, and {@link Major#ETC} cannot be used due to the various minor types.
	 * 
	 * @param major major type of data type to create
	 * @param value non-negative signed long value to embed in data type
	 * @return created data type
	 * @throws IllegalArgumentException if Major.ETC is used, or the value is negative (which may also indicate it
	 *         was an unsigned long above {@link Long#MAX_VALUE}
	 */
	public static DataType canonicalFromMajorAndLongValue(Major major, long value) {
		Objects.requireNonNull(major, "major");
		if (major == Major.ETC) {
			throw new IllegalArgumentException(
					"major cannot be a simple or float type, as the value is not to be interpreted as an integer");
		}
		if (value < 0) {
			throw new ArithmeticException("value must be non-negative / fit within a signed long");
		}
		if (value < 24) {
			return IMMEDIATES[major.getHighOrderBits() | (int) value];
		}
		InfoFormat format = InfoFormat.canonicalFromLongValue(value);
		return new DataType(major, format, value);
	}
	
	// pull any additional value from the data input necessary to represent the value of the data item
	private static long readAdditionalInfo(DataInput input, InfoFormat format, int initialByte) throws IOException {
		long length;
		switch(format) {
		case IMMEDIATE:
			length = InfoFormat.maskBits(initialByte);
			break;
		case BYTE:
			length = input.readUnsignedByte();
			break;
		case SHORT:
			length = input.readUnsignedShort();
			break;
		case INT:
			length = ((long)input.readInt()) & 0xffffffffL;
			break;
		case LONG:
			length = input.readLong();
			break;
		case INDETERMINATE:
			length = 0;
			break;
		default:
			throw new IllegalStateException("unknown minor value of " + InfoFormat.maskBits(initialByte) + " seen");					
		}
		return length;
	}

	/**
	 * Get the major type of this CBOR data item
	 */
	public Major getMajorType() {
		return major;
	}

	/**
	 * Get the minor type/additional info format of this CBOR data item.
	 */
	public InfoFormat getAdditionalInfo() {
		return format;
	}

	/**
	 * Get the value as a non-negative signed java long.
	 * @throws IllegalArgumentException for negative values, which may indicate the value is actually an unsigned
	 * long larger than {@link Long#MAX_VALUE}
	 */
	public long getValue() {
		if (value < 0) {
			throw new IllegalArgumentException("value is negative or does not fit in a signed long");
		}
		return value;
	}

	/**
	 * Get the value as a non-negative signed java 32-bit integer.
	 * @throws IllegalArgumentException for negative values, or values larger than {@link Integer#MAX_VALUE}
	 */
	public int getValueAsInt() {
		if (value < 0) {
			throw new IllegalArgumentException("value is negative");
		}
		if (value > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("value does not fit in a signed integer");
		}
		return (int) value;
	}
	/**
	 * Get the value interpreted as a IEEE binary16 floating-point number. As java does not have native binary16 
	 * support, the binary data will be returned in a short.
	 */
	public short getValueAsHalfFloat() {
		return (short)value;
	}
	
	/**
	 * Get the value interpreted as a IEEE binary32 floating-point number
	 */
	public float getValueAsFloat() {
		return Float.intBitsToFloat((int)value);
	}
	
	/**
	 * Get the value interpreted as a IEEE binary64 floating-point number
	 */
	public double getValueAsDouble() {
		return Double.longBitsToDouble(value);
	}
	
	/** This method allows reading an integer value or count larger than Long#MAX_VALUE. It is significantly more
	 * expensive than reading a signed long, which is recommended in cases where you know the value fits within a
	 * non-negative java signed long.
	 */
	public BigInteger getValueAsBigInteger() {
		if (value >= 0)
			return BigInteger.valueOf(value);
		return BigInteger.valueOf(Long.MAX_VALUE).add(TWO_TO_THE_64TH);
	}
	
	/** This method allows reading the 'raw' value, for usage in cases where it can be correctly interpreted as
	 * an unsigned long.
	 */
	public long getValueAsUnsignedLong() {
		return value;
	}
	/** Get the value as a simple type `true` or `false`. 
	 * @throws CborException value is not `true` or `false` 
	 */
	public boolean getValueAsBoolean() throws CborException {
		if (this.equals(TRUE)) {
			return true;
		}
		if (this.equals(FALSE)) {
			return false;
		}
		throw new CborException("Non-boolean data type");
	}
	
	/**
	 *  Write this data item to the provided output.	 
	 *  
	 *  If one wishes to create their own compatible implementation of DataOutput, the following methods are used 
	 *  on the interface:
	 * 
	 * - {@link DataOutput#writeByte(int)}
	 * - {@link DataOutput#writeShort(int)}
     * - {@link DataOutput#writeInt(int)}
     * - {@link DataOutput#writeLong(long)}
     * 
     * @throws IOException from provided DataOutput 
	 */
	public void write(DataOutput output) throws IOException {
		if (format == InfoFormat.IMMEDIATE) {
			output.writeByte(major.getHighOrderBits() | (int)value);
		} else {
			output.writeByte(major.getHighOrderBits() | format.getLowOrderBits());
			switch (format) {
			case BYTE:
				output.writeByte((int)value);
				break;
			case SHORT:
				output.writeShort((int)value);
				break;
			case INT:
				output.writeInt((int)value);
				break;
			case LONG:
				output.writeLong(value);
				break;
			case IMMEDIATE:
			case INDETERMINATE:
				break;
			}
		}
	}

	/** Create a data type from the provided signed long value.
	 * 
	 * @param v long value
	 * @return constructed unsigned or negative integer data type
	 */
	public static DataType longValue(long v) {
		if (v >= 0) {
			if (v < 24) {
				return IMMEDIATES[Major.UNSIGNED_INT.getHighOrderBits() | ((int)v)];
			}
			return canonicalFromMajorAndLongValue(Major.UNSIGNED_INT, v);
		}
		if (~v < 24) {
			return IMMEDIATES[Major.NEGATIVE_INT.getHighOrderBits() | ((int)~v)];
		}
		return canonicalFromMajorAndLongValue(Major.NEGATIVE_INT, ~v);
	}
	
	/** Create a data type from the provided signed integer value.
	 * 
	 * @param v integer value
	 * @return constructed unsigned or negative integer data type
	 */
	public static DataType integerValue(int v) {
		return longValue(v);
	}

	/** Create a data type from the provided IEEE binary32 floating-point value.
	 * 
	 * @param v floating point value
	 * @return constructed float data type
	 */
	public static DataType floatValue(float v) {
		int f = Float.floatToIntBits(v);
		return new DataType(Major.ETC, InfoFormat.INT, f);
	}

	/** Create a data type from the provided IEEE binary64 floating-point value.
	 * 
	 * @param v double floating point value
	 * @return constructed double data type
	 */
	public static DataType doubleValue(double v) {
		long d = Double.doubleToLongBits(v);
		return new DataType(Major.ETC, InfoFormat.LONG, d);
	}

	/** Create a data type from the provided IEEE binary16 floating-point value.
	 * 
	 * @param hf half float value
	 * @return constructed half-float data type
	 */
	public static DataType halfFloatValue(short hf) {
		return new DataType(Major.ETC, InfoFormat.SHORT, hf);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((format == null) ? 0 : format.hashCode());
		result = prime * result + ((major == null) ? 0 : major.hashCode());
		result = prime * result + (int) (value ^ (value >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DataType other = (DataType) obj;
		if (format != other.format)
			return false;
		if (major != other.major)
			return false;
		if (value != other.value)
			return false;
		return true;
	}
	
	// convert bytes to hex for toString()
	private static String bytesToHex(byte[] in) {
	    final StringBuilder builder = new StringBuilder();
	    for(byte b : in) {
	        builder.append(String.format("%02x", b));
	    }
	    return builder.toString();
	}
	
	/**
	 * Debug string output.
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder("[DataType ");
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream(5);
			DataOutputStream dos = new DataOutputStream(baos)) {
			this.write(dos);
			dos.close();
			builder.append(bytesToHex(baos.toByteArray()));
		} catch (IOException e) {
			builder.append("exception");
		}
		builder.append(" ").append(major).append(":").append(format).append(" ");
		switch (major) {
		case UNSIGNED_INT:
			builder.append(value);
			break;
		case NEGATIVE_INT:
			builder.append(~value);
			break;
		case BYTE_STRING:
		case TEXT_STRING:
		case ARRAY:
		case MAP:
			builder.append("...");
			break;
		case TAG:
			builder.append("tag#").append(value);
			break;
		case ETC:
			switch (format) {
			case IMMEDIATE:
			case BYTE:
				switch((int)value) {
				case 20:
					builder.append("FALSE");
					break;
				case 21:
					builder.append("TRUE");
					break;
				case 22:
					builder.append("NULL");
					break;
				case 23:
					builder.append("UNDEFINED");
					break;
				default:
					builder.append("simple(").append(value).append(")");
					break;
				}
				break;
			case INDETERMINATE:
				builder.append("BREAK");
				break;
			case SHORT:
				short half = (short) value;
				builder.append("binary16(").append(String.format("%4x", half)).append(")");
				break;
			case INT:
				builder.append("float ").append(Float.intBitsToFloat((int)value));
				break;
			case LONG:
				builder.append("double ").append(Double.longBitsToDouble(value));
				break;				
			}
		}
		builder.append("]");
		return builder.toString();
	}

	/**
	 * return a logical type from this data type. This is used to differentiate the different logical meanings of
	 * the value (integer, simple type, floating point number, etc)
	 */
	public LogicalType getLogicalType() {
		return LogicalType.fromDataType(this);
	}

	/**
	 * return if the data type is indeterminate, including if it is a `break` signal.
	 */
	public boolean isIndeterminate() {
		return this.format == InfoFormat.INDETERMINATE;
	}

	/**
	 * return the data type for the specified indeterminate-length container
	 * @throws IllegalArgumentException if the major type does not represent a container
	 */
	public static DataType indeterminate(Major major) {
		if (major.isContainer()) {
			return IMMEDIATES[major.getHighOrderBits() | InfoFormat.INDETERMINATE.getLowOrderBits()];
		}
		throw new IllegalArgumentException("major type does not represent a container");
	}
}