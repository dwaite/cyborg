package us.alksol.cyborg.electrode;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import javax.validation.constraints.NotNull;

public final class InitialByte implements Comparable<InitialByte> {
	// convenience accessors of some of the cached single-byte data values which are also complete CBOR data items	
	
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
	
	static {
		INITIAL_BYTES = new InitialByte[256];
		for (int i = 0; i < 256; i++) {
			Major major = Major.fromInitialByte(i);
			AdditionalInfoFormat format = AdditionalInfoFormat.fromInitialByte(i);
			// reserved format without defined semantics, skip
			if (format == null) {
				continue;
			}
			if (format == AdditionalInfoFormat.INDEFINITE && !major.isIndefiniteAllowed()) {
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

	// the initial byte
	private final byte byteValue;
	
	// The major type based on the initial byte
	private final @NotNull Major major;
	
	// The informational format, and minor order bits for representing indefinite length containers and minor
	// values of Major#ETC
	private final @NotNull AdditionalInfoFormat format;

	/** Construct a DataType from the constituent types. This performs minimal validation, and thus may either
	 * contain illegal state if misused or result in non-well-formed CBOR output
	 * 
	 * @param initialByteValue
	 */
	private InitialByte(byte initialByteValue) {
		this.major = Major.fromInitialByte(initialByteValue & 0xff);
		this.format = AdditionalInfoFormat.fromInitialByte(initialByteValue);
		this.byteValue = initialByteValue;
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
	 * Pull any additional info value from data input, as indicated by the minor value (format) of the initial byte.
	 * 
	 * This may be the value of an integer or tag, the length of a string, count of array items or of key, value 
	 * pairs of map entries, or one of several values (including a floating point number) from {@link Major#ETC}
	 * 
	 * The result is a raw unsigned 64-bit (long) value. This means that values greater than {@link Long#MAX_VALUE} are represented by Java as negative numbers,
	 * and that {@link Float#intBitsToFloat(int)} or {@link Double#longBitsToDouble(long)} can be used to cast to a floating-point value.
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
			rawValue = AdditionalInfoFormat.maskBits(byteValue);
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
	public Major getMajorType() {
		return major;
	}

	/**
	 * Get the minor type/additional info format of this CBOR data item.
	 */
	public AdditionalInfoFormat getAdditionalInfoFormat() {
		return format;
	}

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

	/**
	 * return if the data type is indefinite, including if it is a `break` signal.
	 */
	public boolean isIndefinite() {
		return this.format == AdditionalInfoFormat.INDEFINITE;
	}


	public void assertMajorType(Major... expected) throws CborIncorrectMajorTypeException {
		for (Major potential : expected) {
			if (this.major == potential) {
				return;
			}
		}
		throw new CborIncorrectMajorTypeException(this, expected);
	}

	/**
	 * return the data type for the specified indefinite-length container
	 * @throws IllegalArgumentException if the major type does not represent a container
	 */
	public static InitialByte indefinite(Major major) {
		if (major.isContainer()) {
			return INITIAL_BYTES[major.getHighOrderBits() | AdditionalInfoFormat.INDEFINITE.getLowOrderBits()];
		}
		throw new IllegalArgumentException("major type does not represent a container");
	}
	
	public static InitialByte fromMajorAndFormat(Major major, AdditionalInfoFormat format) throws IllegalArgumentException {
		Objects.requireNonNull(major);
		Objects.requireNonNull(format);
		if (format == AdditionalInfoFormat.IMMEDIATE) {
			throw new IllegalArgumentException("additional info format cannot be immediate. Try 'immediate' factory method");
		}
		InitialByte type = INITIAL_BYTES[major.getHighOrderBits() | format.getLowOrderBits()];
		if (type == null && format == AdditionalInfoFormat.INDEFINITE) {
			throw new IllegalArgumentException("major type '" + major + "' does not support indefinite info format");
		}
		if (type == null) {
			throw new IllegalStateException(major + ", " + format);
		}
		return type;
	}
	public static InitialByte immediate(Major major, int immediateValue) {
		Objects.requireNonNull(major);
		if (immediateValue < 0 || immediateValue >= 24) {
			throw new IllegalArgumentException("immediate value must be from 0 through 23");
		}
		return INITIAL_BYTES[major.getHighOrderBits() | immediateValue];
	}
	public static InitialByte forCanonicalLongValue(Major major, long rawLong) {
		Objects.requireNonNull(major);
		AdditionalInfoFormat format = AdditionalInfoFormat.canonicalFromLongValue(rawLong);
		if(format == AdditionalInfoFormat.IMMEDIATE) {
			return INITIAL_BYTES[major.getHighOrderBits() | (int) rawLong];
		}
		if (major == Major.ETC && format != AdditionalInfoFormat.BYTE) {
			throw new IllegalArgumentException("raw long is too large to represent a simple value");
		}
		return INITIAL_BYTES[major.getHighOrderBits() | format.getLowOrderBits()];
	}
	
}