package us.alksol.cyborg.electrode;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import javax.swing.border.EtchedBorder;
import javax.validation.constraints.NotNull;

/** Represents the first byte of a well-formed CBOR data item, to aid in logic depending on the particular type. 
 * The objects are immutable and singletons, with only well-formed CBOR initial bytes being available. */ 
public final class InitialByte implements Comparable<InitialByte> {
	/** 
	 * Represents the major type of a data item in CBOR. 
	 * 
	 * @see [CBOR Major Data Types](https://tools.ietf.org/html/rfc7049#section-2.1) 
	 */
	public enum Major {
		/** major 0, unsigned integers from 0 to 2^^64-1. */
		INTEGER(false, false, false),
		
		/** major 1, negative integers from -1 to -2^^64. */
		NEGATIVE_INTEGER(false, false, false),
		
		/** major 2, a binary sequence. The additional info represents the byte length of the sequence */
		BYTE_STRING(true, true, true),
		
		/** major 3, a text string. The additional info represents the byte length of the string, in UTF-8. */
		TEXT_STRING(true, true, true),
		
		/** major 4, an array of data items. The additional info represents the number of child items of this array */ 
		ARRAY(true, false, true),
		
		/** major 5, a map of data items. The additional info represents the number of key value pairs in this map, 
		 * where each key and value are allowed to be arbitrary CBOR data items. As pairs, this number is one half the
		 * total number of child items. */
		MAP(true, false, true),
		
		/** major 6, a semantic tag for a data item. The additional info represents semantic interpretation of a single
		 * child data item. */
		TAG(false, false, true),
		
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
		ETC(false, false, true);
	
		private final boolean isPossiblyIndefiniteContainer;
		private final boolean isPossiblyFollowedByBinaryData;
		private final boolean isIndefiniteAdditionalInfoAllowed;
	
		private Major(boolean isPossiblyContainer, 
				boolean isPossiblyFollowedByBinaryData, 
				boolean isIndefiniteAdditionalInfoAllowed) {
			this.isPossiblyIndefiniteContainer = isPossiblyContainer;
			this.isPossiblyFollowedByBinaryData = isPossiblyFollowedByBinaryData;
			this.isIndefiniteAdditionalInfoAllowed = isIndefiniteAdditionalInfoAllowed;
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
		 * Represents that this is can contain data items (including an indefinite # of items).
		 */
		public boolean isPossiblyIndefinateContainer() {
			return this.isPossiblyIndefiniteContainer;
		}
	
		/**
		 * represents whether a binary stream is always followed by another data type, 
		 * or may be followed by binary data.
		 */
		public boolean isPossiblyFollowedByBinaryData() {
			return isPossiblyFollowedByBinaryData;
		}
		
		/**
		 * represents whether this major type allows an 'indefinite' additional info format, such as indefinite length
		 * arrays, chunked arbirary length binary/text data, and the 'break' signal.
		 */
		public boolean isIndefiniteAdditionalInfoAllowed() {
			return isIndefiniteAdditionalInfoAllowed;
		}
	}
	
	/**
	 * Represents the format of additional information, as well as differentiation of minor types for {@link Major#ETC}
	 * 
	 * The initial byte contains not just a major type in its high order bits, but additional information in the
	 * low order bits. This may be an immediate integer or special value, or may indicate the real additional 
	 * information follows in the next several bytes.
	 * 
	 * * For integer and tag types, this value lets us know if the integer was encoded canonically.
	 * * For container types (binary/text strings, arrays and maps) this also lets us know if the container
	 *   represents an indefinite number of items.
	 * * For {@link Major#ETC}, this helps us differentiate from the various possible items represented - simple
	 *   values, the various floating point number formats, and the `break` signal. Since immediate data is not
	 *   captured by this enumeration, this won't tell you the particular simple type (such as true or false)
	 */
	public enum InfoFormat {
		/** The integer values 0 through 23 can be encoded as immediate values within the initial byte itself. */
		IMMEDIATE(0, 0),
		
		/** The value is encoded in a single byte following the initial byte */
		BYTE(24, 1),
		
		/** The value is encoded in two bytes following the initial byte, in network byte order (big-endian). */
		SHORT(25, 2),
		
		/** The value is encoded in four bytes following the initial byte, in network byte order (big-endian). */
		INT(26, 4),
		
		/** The value is encoded in eight bytes following the initial byte, in network byte order (big-endian). */
		LONG(27, 8),
		
		/** For container types, this indicates an indeterminate number of contained data items. For 
		 * {@link Major#ETC}, this indicates the `break` signal terminating such an indeterminate container. */
		INDEFINITE(31, 0);
		
		private final int lowOrderBits;
		private final int additionalInfoBytes;
		
		private InfoFormat(int lowOrderBits, int additionalInfoBytes) {
			this.lowOrderBits = lowOrderBits;
			this.additionalInfoBytes = additionalInfoBytes;
		}
		
		/** mask the low order bits representing the `InfoFormat` and any immediate value off from the initial byte */
		public static int maskBits(int initialByte) {
			return initialByte & 0x1f;
		}
		
		/** return the low order bits of the initial byte represented by this `InfoFormat` type. All immediate values
		 * are represented by the return value of `0`
		 */
		public int getLowOrderBits() {
			return lowOrderBits;
		}

		/**
		 * the number of following bytes taken up by the additional info.
		 */
		public int getAdditionalInfoBytes() {
			return additionalInfoBytes;
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
					return INDEFINITE;
				default:
					return null;
				}
			}
		}
		
		/** 
		 * Determine the appropriate canonical (smallest) encoding of a given long value. 
		 * 
		 * @param value integer value to encode.	Note that while the long primitive type in Java is signed, this 
		 * long is interpreted as an unsigned long - all negative values are considered larger than 
		 * {@link Long#MAX_VALUE}
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
	
	/** represents the logical type of the data, where some majors (like integer and negative integer) are combined, 
	 * while others such as the defined simple values are broken out.
	 */
	public enum LogicalType {
		/** Positive or negative integer type */
		INTEGRAL,

		/** Single known-length sequence of bytes */
		BINARY_CHUNK				(false, false, true, false),

		/** Single known-length sequence of UTF-8 encoded text. */
		TEXT_CHUNK				(false, false, true, false),
		
		/** Start of an arbitrary length sequence of bytes, in the form of multiple {@link LogicalType#BINARY_CHUNK}
		 * values. Terminated by {@link #BREAK}.
		 */
		START_BINARY_CHUNKS		(false, true,  false, true),
		/** Start of an arbitrary length text, in the form of multiple {@link LogicalType#TEXT_CHUNK}
		 * values, each of which required to be valid UTF-8. Terminated by {@link #BREAK}.
		 */
		START_TEXT_CHUNKS		(false, true,  false, true),

		/** Start of a known length array of data items */
		START_ARRAY_ELEMENTS				(false, true,  false, false),

		/** Start of an arbitrary length array of data items. Terminated by {@link #BREAK}. */
		START_INDEFINITE_ARRAY_ELEMENTS	(false, true,  false, true),

		/** Start of a known length map of data item (key, value) pairs */
		START_MAP_PAIRS					(false, true,  false, false),
		/** Start of an arbitrary length map of data item (key, value) pairs. Terminated by {@link #BREAK}. */
		START_INDEFINITE_MAP_PAIRS		(false, true,  false, true),

		/** Semantic tagging of a data item (e.g. a {@link #BINARY_CHUNK} is actually meant to represent an
		 * arbitrary-length integer). */
		TAG						(false, true,  false, false),

		/** Simple value of boolean true or false */
		BOOLEAN					(true,  false, false, false),
		/** Simple value of null, indicating no value */
		NULL						(true,  false, false, false),
		/** Simple value of undefined, indicating missing or unrecognized data. */
		UNDEFINED				(true,  false, false, false),
		/** Another simple value not defined by the CBOR specification */
		OTHER_SIMPLE				(true,  false, false, false),
		/** Any built-in width floating point value */
		FLOATING,
		/** The break signal */
		BREAK;
		
		private final boolean simpleType;
		private final boolean container;
		private final boolean followedByBinaryData;
		private final boolean isStartOfIndefiniteContainer;
		
		private LogicalType(
				boolean isSimple,
				boolean isContainer,
				boolean followedByBinaryData,
				boolean isStartOfIndefiniteContainer) {
			this.simpleType = isSimple;
			this.container = isContainer;
			this.followedByBinaryData = followedByBinaryData;
			this.isStartOfIndefiniteContainer = isStartOfIndefiniteContainer;
		}
		
		private LogicalType() {
			this(false, false,false, false);
		}
		
		/** The data item contains one or more other data items. This includes tags, which act as a single-data-item
		 *  container */
		public boolean isContainer() {
			return container;
		}
		
		/** The data item is a simple type, such as the values 'true', 'false' and 'null'. This includes any simple
		 *  values not defined by the core CBOR specification.
		 */
		public boolean isSimpleType() {
			return simpleType;
		}
		
		/** The data item includes binary data. In this case, the additional info specifies a specific number
		 *  of bytes which are included, following the initial byte and additional info.
		 */
		public boolean isFollowedByBinaryData() {
			return followedByBinaryData;
		}
		
		/** This item is an indefinite length container, terminated by a {@link #BREAK} signal. */
		public boolean isStartOfIndefiniteContainer() {
			return isStartOfIndefiniteContainer;
		}

		private static LogicalType fromInitialByte(InitialByte type) {
			if (type == InitialByte.BREAK) {
				return BREAK;
			}
			if (type == InitialByte.FALSE) {
				return BOOLEAN;
			}
			if (type == InitialByte.NULL) {
				return NULL;
			}
			if (type == InitialByte.TRUE) {
				return BOOLEAN;
			}
			if (type == InitialByte.UNDEFINED) { 
				return UNDEFINED;
			}
			switch(type.getMajor()) {
			case INTEGER:
			case NEGATIVE_INTEGER:
				return INTEGRAL;
			case ARRAY:
				switch (type.getAdditionalInfoFormat()) {
				case INDEFINITE:
					return START_INDEFINITE_ARRAY_ELEMENTS;
				default:
					return START_ARRAY_ELEMENTS;
				}
			case MAP:
				switch (type.getAdditionalInfoFormat()) {
				case INDEFINITE:
					return START_INDEFINITE_MAP_PAIRS;
				default:
					return START_MAP_PAIRS;
				}
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
				case INT:
				case LONG:
					return FLOATING;
				default:
					throw new IllegalArgumentException();
				}
			default:
				throw new IllegalArgumentException();
			}			
		}
	}

	// Convenience accessors of some of the cached single-byte data values, which are also complete CBOR data items.
	// Typically you do not work with initial bytes however, instead using a higher-level type that can represent
	// at least additional info if not binary data and child CBOR data items.
	
	/** Data item of the simple boolean value `false` */
	public static final InitialByte FALSE;
	
	/** Data item of the simple boolean value `true` */
	public static final InitialByte TRUE;
	
	/** Data item of the simple value `null`, representing the absence of value */
	public static final InitialByte NULL;
	
	/** Data item of the simple value `undefined`, representing a value is neither defined as having a value or 
	 * explicitly as having no value. */
	public static final InitialByte UNDEFINED;
	
	/** Data item representing the `break` signal, terminating an indefinite-length container. */
	public static final InitialByte BREAK;

	// immutable singletons for all well-formed initial bytes (229 of 256 possible)
	private static final InitialByte INITIAL_BYTES[];
	
	// initialize singletons
	static {
		INITIAL_BYTES = new InitialByte[256];
		for (int i = 0; i < 256; i++) {
			Major major = Major.fromInitialByte(i);
			InfoFormat format = InfoFormat.fromInitialByte(i);
			// reserved format without defined semantics, skip
			if (format == null) {
				continue;
			}
			if (format == InfoFormat.INDEFINITE && !major.isPossiblyIndefinateContainer()) {
				continue;
			}
			INITIAL_BYTES[i] = new InitialByte((byte)i);
		}
		FALSE		= INITIAL_BYTES[0xf4];
		TRUE			= INITIAL_BYTES[0xf5];
		NULL			= INITIAL_BYTES[0xf6];
		UNDEFINED	= INITIAL_BYTES[0xf7];
		BREAK		= INITIAL_BYTES[0xff];
	}

	// the initial byte. Can be used to determine immediate value additional info.
	private final byte byteValue;
	
	// The major type based on the initial byte
	private final @NotNull Major major;
	
	// The informational format, and minor order bits for representing indefinite length containers and minor
	// values of Major#ETC. Does not include additional info, including immediate values
	private final @NotNull InfoFormat format;

	// The logical type, more useful for software making decisions based on received data
	private final @NotNull LogicalType logicalType;
	
	/** Construct a DataType from the constituent types. This performs minimal validation, and thus may either
	 * contain illegal state if misused or result in non-well-formed CBOR output
	 * 
	 * @param initialByteValue
	 */
	private InitialByte(byte initialByteValue) {
		this.major = Major.fromInitialByte(initialByteValue & 0xff);
		this.format = InfoFormat.fromInitialByte(initialByteValue);
		this.byteValue = initialByteValue;
		this.logicalType = LogicalType.fromInitialByte(this);
	}

	/**
	 * Helper method to read a data type from a binary data input.
	 * 
	 * If one wishes to create their own compatible implementation of DataInput, the following methods are used on
	 * the interface:
	 * 
	 * - {@link DataInput#readUnsignedByte()}
     * 
	 * @param input data input
	 * @return DataType, or null if byte is not well formed.
	 * @throws IOException - error reading from provided input
	 * @throws IllegalStateException - initial byte represents a reserved additional info format/minor value.
	 */
	public static Optional<InitialByte> readDataType(DataInput input) throws IOException {
		return initialByte(input.readUnsignedByte());
	}

	/**
	 * Construct a data type solely from an initial byte, if possible
	 * @param ib initial unsigned byte of CBOR data item
	 * @return data type value, or `null` if a single byte representation isn't allowed.
	 * 
	 * @throws ArrayIndexOutOfBoundsException if ib value is not an unsigned byte.
	 */
	public static Optional<InitialByte> initialByte(int ib) {
		return Optional.ofNullable(INITIAL_BYTES[ib]);
	}
	
	/**
	 * return the data type for the specified indefinite-length container
	 * @throws IllegalArgumentException if the major type does not represent a container
	 */
	public static InitialByte indefinite(Major major) {
		if (major.isPossiblyIndefinateContainer()) {
			return INITIAL_BYTES[major.getHighOrderBits() | InfoFormat.INDEFINITE.getLowOrderBits()];
		}
		throw new IllegalArgumentException("major type does not represent a container");
	}
	
	/**
	 * return an initial byte based on a {@link Major} type and a non-immediate {@link InfoFormat} value.
	 * 
	 * @param major
	 * @param format
	 * @return well-formed initial byte
	 * @throws IllegalArgumentException if any of the following are true:
	 *  - {@link InfoFormat#IMMEDIATE} was requested as the format
	 *  - {@link InfoFormat#INDEFINITE} was requested for a major type which does not support indefinite data, such as
	 *    integers
	 */
	public static InitialByte fromMajorAndFormat(Major major, InfoFormat format) throws IllegalArgumentException {
		Objects.requireNonNull(major);
		Objects.requireNonNull(format);
		if (format == InfoFormat.IMMEDIATE) {
			throw new IllegalArgumentException("additional info format cannot be immediate. Try 'immediate' factory method");
		}
		if (!major.isIndefiniteAdditionalInfoAllowed() && format == InfoFormat.INDEFINITE) {
			throw new IllegalArgumentException("major type '" + major + "' does not support indefinite info format");			
		}
		InitialByte type = INITIAL_BYTES[major.getHighOrderBits() | format.getLowOrderBits()];
		if (type == null) {
			throw new IllegalStateException(major + ", " + format);
		}
		return type;
	}
	
	/**
	 * return an initial byte based on a {@link Major} type and an immediate additional info value.
	 * @param major
	 * @param immediateValue
	 * @return well-formed initial byte
	 * @throws IllegalArgumentException if any of the following are true:
	 *  - immediate value is negative
	 *  - immediate value is over 23
	 */
	public static InitialByte immediate(Major major, int immediateValue) {
		Objects.requireNonNull(major);
		if (immediateValue < 0 || immediateValue >= 24) {
			throw new IllegalArgumentException("immediate value must be from 0 through 23");
		}
		return INITIAL_BYTES[major.getHighOrderBits() | immediateValue];
	}
	/**
	 * return an initial byte which indicates an additional info which can support the given raw/unsigned long
	 * additional info value in the minimum number of bytes.
	 * @param major
	 * @param rawLong an unsigned long value stored within a java signed long primitive.
	 * @return a well-formed initial byte supporting storage of the given unsigned long
	 * @throws IllegalArgumentException if the major type is {@link Major#ETC} and either:
	 * 	- rawLong exceeds byte length, as the ETC type is shared with floating point values for 16/32/64 bit length.
	 *  - rawLong represents a simple type with value from 24 up to 32, which are forbidden by CBOR.
	 */
	public static InitialByte forCanonicalLongValue(Major major, long rawLong) {
		Objects.requireNonNull(major);
		InfoFormat format = InfoFormat.canonicalFromLongValue(rawLong);
		if(format == InfoFormat.IMMEDIATE) {
			return INITIAL_BYTES[major.getHighOrderBits() | (int) rawLong];
		}
		if (major == Major.ETC && format != InfoFormat.BYTE) {
			throw new IllegalArgumentException("raw long is too large to represent a simple value");
		}
		if (major == Major.ETC && rawLong >= 24 && rawLong < 32) {
			throw new IllegalArgumentException("simple values are forbidden with values from 24 up to 32");
		}
		return INITIAL_BYTES[major.getHighOrderBits() | format.getLowOrderBits()];
	}

	/**
	 * Pull any additional info value from data input, as indicated by the minor value (format) of the initial byte.
	 * 
	 * This may be the value of an integer or tag, the length of a string, count of array items or of key, value 
	 * pairs of map entries, or one of several values (including a floating point number) from {@link Major#ETC}
	 * 
	 * The result is a raw unsigned 64-bit (long) value. This means that values greater than {@link Long#MAX_VALUE}
	 * are represented by Java as negative numbers, and that {@link Float#intBitsToFloat(int)} or 
	 * {@link Double#longBitsToDouble(long)} can be used to cast to a floating-point value.
	 * 
	 * @param input for any binary data we need for the additional info
	 * @return raw long value
	 * 
	 * @throws IOException from underlying {@link DataInput} type
	 */
	public long readAdditionalInfo(@NotNull DataInput input) throws IOException {
		long rawValue;
		switch(format) {
		case IMMEDIATE:
			rawValue = InfoFormat.maskBits(byteValue);
			break;
		case BYTE:
			rawValue = input.readUnsignedByte();
			break;
		case SHORT:
			rawValue = input.readUnsignedShort();
			break;
		case INT:
			rawValue = ((long)input.readInt()) & 0xffffffffL;
			break;
		case LONG:
			rawValue = input.readLong();
			break;
		case INDEFINITE:
			rawValue = 0;
			break;
		default:
			throw new IllegalStateException("unknown format of " + format + " seen");					
		}
		return rawValue;
	}

	/**
	 * Get the major type of this CBOR data item
	 */
	public Major getMajor() {
		return major;
	}

	/**
	 * Get the minor type/additional info format of this CBOR data item.
	 */
	public InfoFormat getAdditionalInfoFormat() {
		return format;
	}
	
	/**
	 * Get the logical type of this CBOR data item
	 */
	public LogicalType getLogicalType() {
		return logicalType;
	}

	/**
	 * Get the byte representation of this object
	 */
	public int getRepresentation() {
		return byteValue & 0xff;
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
		output.write(byteValue);
	}

	@Override
	public int hashCode() {
		return byteValue;
	}

	@Override
	public boolean equals(Object obj) {
		return this == obj;
	}

	@Override
	public int compareTo(InitialByte type) {
		return type.byteValue - byteValue;
	}

	/** Assert that the major type is one of the expected enumeration values, or else throw an exception explaining
	 * the issue. */
	public void assertMajorType(Major... expected) throws CborIncorrectMajorTypeException {
		for (Major potential : expected) {
			if (this.major == potential) {
				return;
			}
		}
		if (expected.length == 1) {
			throw new CborIncorrectMajorTypeException(this, expected[0]);
		}
		throw new CborIncorrectMajorTypeException(this, expected);
	}

	/** Assert that the logical type is one of the expected enumeration values, or else throw an exception explaining
	 * the issue. */
	public void assertLogicalType(LogicalType... expected) throws CborIncorrectLogicalTypeException {
		for (LogicalType potential : expected) {
			if (this.logicalType == potential) {
				return;
			}
		}
		if (expected.length == 1) {
			throw new CborIncorrectLogicalTypeException(this, expected[0]);
		}
		throw new CborIncorrectLogicalTypeException(this, expected);
	}

	/** Whether this byte represents a simple value.
	 * @see {@link LogicalType#isSimpleType()} */
	public boolean isSimpleValue() {
		return logicalType.isSimpleType();
	}
	
	/** Whether this byte is followed by binary data. 
	 * @see {@link LogicalType#isFollowedByBinaryData()} */
	public boolean isFollowedByBinaryData() {
		return logicalType.isFollowedByBinaryData();
	}
	
	/** Whether the data item represented by this type is a container
	 * @see {@link LogicalType#isContainer()} */
	public boolean isContainer() {
		return logicalType.isContainer();
	}

	/** Whether the data item represented by this type is an indefinite-length container. 
	 * See {@link LogicalType#isStartOfIndefiniteContainer} */
	public boolean isStartOfIndefiniteContainer() {
		return logicalType.isStartOfIndefiniteContainer;
	}
	/**
	 * return if the data type is indefinite, including both indefinite-length containers and the `break` signal.
	 */
	public boolean isIndefinite() {
		return this.format == InfoFormat.INDEFINITE;
	}
}