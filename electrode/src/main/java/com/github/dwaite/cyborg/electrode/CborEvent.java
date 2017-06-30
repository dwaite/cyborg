package com.github.dwaite.cyborg.electrode;

import java.io.DataOutput;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import com.github.dwaite.bytestring.Bytes;
import com.github.dwaite.cyborg.electrode.InitialByte.InfoFormat;
import com.github.dwaite.cyborg.electrode.InitialByte.Major;

/** Represents a data event, which is similar to a CBOR data item but excludes any child data items for containers.
 * This makes the type more suitable for 'walking' a CBOR data item, whether reading or writing. 
 * 
 * Implementations of CborEvent are meant to be immutable, at least over the life of any underlying data source.
 * */
public interface CborEvent {
	// constant big integer to convert values above Long#MAX_VALUE to a Java-compatible numeric
	static final BigInteger TWO_TO_THE_64TH = 
			new BigInteger(1, new byte[] {0x1, 0, 0, 0, 0, 0, 0, 0, 0});
	/** helper for handling when a data event may represent either a piece of data *or* `null`. */
	public class CborOptionalValue {
		private @NotNull CborEvent event;
		
		public CborOptionalValue(@NotNull CborEvent event) {
			Objects.requireNonNull(event);
			this.event = event;
		}
		
		public Optional<Integer> intValue() throws ArithmeticException, CborException {
			if (event.isNull()) {
				return Optional.empty();
			}
			return Optional.of(event.additionalInfoAsInt());
		}

		public Optional<Long> longValue() throws CborException {
			if (event.isNull()) {
				return Optional.empty();
			}
			return Optional.of(event.additionalInfoAsLong());
		}

		public Optional<BigInteger> bigIntegerValue() throws CborException {
			if (event.isNull()) {
				return Optional.empty();
			}
			return Optional.of(event.additionalInfoAsBigInteger());
		}

		public boolean isIndefiniteLengthContainer() {
			return event.isStartOfIndefiniteLengthContainer();
		}

		public Optional<Integer> count() throws CborException {
			if (event.isNull()) {
				return Optional.empty();
			}
			int count = event.additionalInfoAsCount();
			if (count == -1) {
				return Optional.empty();
			}
			return Optional.of(count);
		}

		public Optional<Short> halfFloatValue() throws CborException {
			if (event.isNull()) {
				return Optional.empty();
			}
			return Optional.of(event.additionalInfoAsHalfFloat());
		}
				
		public Optional<Float> floatValue() throws CborException {
			if (event.isNull()) {
				return Optional.empty();
			}
			return Optional.of(event.additionalInfoAsFloat());
		}

		public Optional<Double> doubleValue() throws CborException {
			if (event.isNull()) {
				return Optional.empty();
			}
			return Optional.of(event.additionalInfoAsDouble());
		}

		public Optional<Boolean> booleanValue() throws CborException {
			if (event.isNull()) {
				return Optional.empty();
			}
			return Optional.of(event.booleanValue());

		}

		public boolean isNull() {
			return event.isNull();
		}

		public boolean isUndefined() {
			return event.isUndefined();
		}

		public Optional<Bytes> bytes() {
			return Optional.ofNullable(event.bytes());
		}

		public Optional<String> asTextValue() {
			if (event.isNull()) {
				return Optional.empty();
			}
			return Optional.ofNullable(event.asTextValue());
		}

		public boolean isBreak() {
			return event.isBreak();
		}

		public boolean isLiteralBreak() {
			return event.isBreak();
		}
	}
	/** Get the CBOR initial byte of this data event */
	InitialByte getInitialByte();

	/** Get the additional info value for this data event. This value is meant to be either interpreted as an unsigned
	 * long or as raw data. It may represent several things based on the particular type of the CBOR data item:
	 * 
	 *  - an unsigned integral value
	 *  - a negated integral value (via ones complement)
	 *  - a binary64 (double floating point) number
	 *  - a binary32 (float) or binary16 (half float) number (padded with zeros in most significant bits)
	 *  - the type identifier of a simple data item, such as 'true', 'false' and 'null'
	 *  - the count of a bytes in a binary or text chunk
	 *  - the count of child data items in an array
	 *  - the count of key and value data item pairs in a map
	 *
	 * If the data event represents either the start or end of an indefinite-length container, this value is unused
	 * and will be set to zero. 
	 */
	long getAdditionalInfo();
	
	/** Return the additional info value, interpreted as a signed integer. The following logical types are supported:
	 * 
	 *  - integral (and negative integral) values. Negative integrals will return a negative value
	 *  - tag values
	 *  - simple types
	 *  
	 * @throws IllegalStateException if the logical type is not a integral, tag, or simple value
	 * @throws ArithmeticException if the additional info value overflows or underflows an 32-bit signed integer.
	 */
	default int additionalInfoAsInt() throws IllegalStateException, ArithmeticException {
		InitialByte ib = getInitialByte();
		Major major = ib.getMajor();
		InfoFormat format = ib.getAdditionalInfoFormat();		
		long value = getAdditionalInfo();
		
		switch (major) {
		case INTEGER:
		case TAG:
			if (value < 0 || value > Integer.MAX_VALUE)
				throw new ArithmeticException("value out of bounds of 32-bit signed integer");
			return (int)value;
		case NEGATIVE_INTEGER:
			if (value < 0 || value >= Integer.MAX_VALUE)
				throw new ArithmeticException("value out of bounds of 32-bit signed integer");
			return (int)~value;
		case ETC:
			if (format != InfoFormat.IMMEDIATE && format != InfoFormat.BYTE) {
				throw new IllegalStateException("value not integer, tag, or simple type");
			}
			return (int) value;
		default:
			throw new IllegalStateException(new CborIncorrectMajorTypeException(ib, Major.INTEGER, Major.NEGATIVE_INTEGER, Major.TAG));	
		}
	}
	
	/** Return the additional info value, interpreted as a signed long. The following logical types are supported:
	 * 
	 *  - integral (and negative integral) values. Negative integrals will return a negative value
	 *  - tag values
	 *  - simple types
	 *  
	 * @throws IllegalStateException if the logical type is not a integral, tag, or simple value
	 * @throws ArithmeticException if the additional info value overflows or underflows an 64-bit signed long integer.
	 */
	default long additionalInfoAsLong() throws IllegalStateException, ArithmeticException {
		InitialByte ib = getInitialByte();
		Major major = ib.getMajor();
		InfoFormat format = ib.getAdditionalInfoFormat();		
		long value = getAdditionalInfo();

		switch (major) {
		case INTEGER:
		case TAG:
			if (value < 0)
				throw new ArithmeticException("value out of bounds of 64-bit signed integer");
			return value;
		case NEGATIVE_INTEGER:
			if (value < 0 || value == Long.MAX_VALUE)
				throw new ArithmeticException("value out of bounds of 64-bit signed integer");
			return ~value;
		case ETC:
			if (format != InfoFormat.IMMEDIATE && format != InfoFormat.BYTE) {
				throw new IllegalStateException("value not integer, tag, or simple type");
			}
			return value;
		default:
			throw new IllegalStateException(new CborIncorrectMajorTypeException(ib, Major.INTEGER, Major.NEGATIVE_INTEGER, Major.TAG));	
		}
	}

	/** Return the additional info value, interpreted as a arbitrary length integer. The following logical types are supported:
	 * 
	 *  - integral (and negative integral) values. Negative integrals will return a negative value
	 *  - tag values
	 *  - simple types
	 *  
	 * @throws IllegalStateException if the logical type is not a integral, tag, or simple value
	 */
	default BigInteger additionalInfoAsBigInteger() throws IllegalStateException {
		InitialByte ib = getInitialByte();
		long value = getAdditionalInfo();

		try {
			ib.assertMajorType(Major.INTEGER, Major.NEGATIVE_INTEGER, Major.TAG, Major.ETC);
		} catch (CborIncorrectMajorTypeException e) {
			throw new IllegalStateException(e);
		}
		BigInteger result = BigInteger.valueOf(value);
		if (value < 0)
			result = result.add(TWO_TO_THE_64TH);
		if (ib.getMajor() == Major.NEGATIVE_INTEGER) {
			result = result.negate();
		}
		return result;
	}

	/** Return the additional info value, interpreted as a 32-bit signed integer count of contained data. 
	 *  The following logical types are supported:
	 * 
	 *  - byte strings and text strings, in which case the count is a byte count
	 *  - arrays, in which case the count is the number of child data items
	 *  - maps, in which case the count is the number of name, value pairs. The number of child data items is
	 *    effectively double the returned count
	 *  - tags, in which case the count is `1` regardless of the particular type of tag
	 *  
	 *  Note: this method does not have a `long` form, as Java containers are limited to approximately 
	 *  {@link Integer#MAX_VALUE} length.
	 *  @returns the container count, or `-1` if the container is indefinite length.
	 *  
	 * @throws IllegalStateException if the logical type is not a byte/text string, array, map or tag
	 * @throws ArithmeticException if the value cannot fit within the bounds of a 32-bit signed integer
	 */
	default int additionalInfoAsCount() throws IllegalStateException {
		try {
			InitialByte ib = getInitialByte();
			long value = getAdditionalInfo();
	
			switch (ib.getMajor() ) {
			case BYTE_STRING:
			case TEXT_STRING:
			case ARRAY:
			case MAP:
				if (isStartOfIndefiniteLengthContainer()) {
					return -1;
				}
				if (value < 0 || value > Integer.MAX_VALUE) {
					throw new ArithmeticException("count out of bounds of 32-bit signed integer");
				}
				return (int) value;
			case TAG:
				return 1;
			default:
				throw new CborIncorrectMajorTypeException(ib, Major.BYTE_STRING, Major.TEXT_STRING, Major.ARRAY, Major.MAP, Major.TAG);
			}
		} catch (CborIncorrectMajorTypeException e) {
			throw new IllegalStateException(e);
		}

	}

	/** Return the additional info value, interpreted as a half float. As Java does not contain binary16 support, this
	 * return value is currently represented as a `short`. This does not do conversion between other width floating
	 * point values.
	 * 
	 * @throws IllegalStateException if the data event does not represent a half float
	 */
	default short additionalInfoAsHalfFloat() {
		InitialByte ib = getInitialByte();
		long value = getAdditionalInfo();

		try {
			ib.assertMajorType(Major.ETC);
		} catch (CborIncorrectMajorTypeException e) {
			throw new IllegalStateException(e);
		}
		if (ib.getAdditionalInfoFormat() != InfoFormat.SHORT) {
			throw new IllegalStateException("Expected 16 bit half float (binary16)");
		}
		return (short) value;
	}

	/** Return the additional info value, interpreted as a float (IEEE binary32). 
	 * This does not do conversion between other width floating point values.
	 * 
	 * @throws IllegalStateException if the data event does not represent a 32-bit float
	 */
	default float additionalInfoAsFloat() {
		InitialByte ib = getInitialByte();
		long value = getAdditionalInfo();

		try {
			ib.assertMajorType(Major.ETC);
		} catch (CborIncorrectMajorTypeException e) {
			throw new IllegalStateException(e);
		}
		if (ib.getAdditionalInfoFormat() != InfoFormat.INT) {
			throw new IllegalStateException("Expected 32-bit float (binary32)");
		}
		return Float.intBitsToFloat((int) value);
	}

	/** Return the additional info value, interpreted as a double-width float (IEEE binary64). 
	 * This does not do conversion between other width floating point values.
	 * 
	 * @throws IllegalStateException if the data event does not represent a 64-bit float
	 */
	default double additionalInfoAsDouble() {
		InitialByte ib = getInitialByte();
		long value = getAdditionalInfo();

		try {
			ib.assertMajorType(Major.ETC);
		} catch (CborIncorrectMajorTypeException e) {
			throw new IllegalStateException(e);
		}
		if (ib.getAdditionalInfoFormat() != InfoFormat.LONG) {
			throw new IllegalStateException("Expected 64-bit double (binary64)");
		}
		return Double.longBitsToDouble(value);
	}

	/** Return whether the boolean value of this data item.
	 * 
	 * @return boolean true or false, corresponding to simple values 'true' and 'false'
	 * @throws IllegalStateException if the data is not one of the two boolean simple values
	 */
	default boolean booleanValue() {
		InitialByte ib = getInitialByte();

		if (ib == InitialByte.TRUE) {
			return true;
		}
		if (ib == InitialByte.FALSE) {
			return false;
		}
		try {
			ib.assertMajorType(Major.ETC);
		} catch (CborIncorrectMajorTypeException e) {
			throw new IllegalStateException(e);
		}
		throw new IllegalStateException("Expected boolean simple type (true or false)");
	}

	/** Return if this data item represents the start of an indefinite-length container.
	 * @see InitialByte#isStartOfIndefiniteContainer()
	 * @return
	 */
	default boolean isStartOfIndefiniteLengthContainer() {
		return getInitialByte().isStartOfIndefiniteContainer();
	}
	
	/** Return true if this is a null data item, false for all others (including `undefined`) */
	default boolean isNull() {
		return getInitialByte() == InitialByte.NULL;
	}

	/** Return true if this is a undefined data item simple value. */
	default boolean isUndefined() {
		return getInitialByte() == InitialByte.UNDEFINED;
	}

	/** Return a byte sequence associated with a single binary or text chunk. Will return {@link Bytes#empty()} for 
	 * other types */
	Bytes bytes();
	
	/** Return the byte sequence associated with a single binary or text chunk, interpreted as UTF-8 bytes into a string.
	 * Will return an empty String for other types. */
	String asTextValue();
	
	/** Return true if this represents a break signal to an indefinite container */
	default boolean isBreak() {
		return getInitialByte() == InitialByte.BREAK;
	}
	
	/** Returns true if this is a simple value such as `true`, `false`, `null`, `undefined`, or another simple value
	 * not defined within the core CBOR spec.
	 */
	default boolean isSimpleValue() {
		return getInitialByte().isSimpleValue();
	}
	
	/** Returns a helper which provided additional API for dealing with potentially null values */
	default 	CborOptionalValue optional() {
		return new CborOptionalValue(this);
	}
	
	/** Write this data event to a {@link DataOutput} instance. This will include the initial byte, additional info,
	 *  and binary data, but will not include any contained data as this is not a full data item.
	 *  
	 * @param output data output instance to use
	 * @throws IOException if the DataOutput fails to write
	 */
	default void write(DataOutput output) throws IOException {
		Objects.requireNonNull(output);
		
		InitialByte ib = getInitialByte();
		ib.write(output);
		InfoFormat format = ib.getAdditionalInfoFormat();
		long rawValue = getAdditionalInfo();
		switch (format) {
		case IMMEDIATE:
		case INDEFINITE:
		default:
			break;
		case BYTE:
			output.writeByte((byte) rawValue);
			break;
		case SHORT:
			output.writeShort((short) rawValue);
			break;
		case INT:
			output.writeInt((int) rawValue);
			break;
		case LONG:
			output.writeLong(rawValue);
		}
		Bytes bytes = bytes();
		if (bytes != null && !bytes.isEmpty()) {
			bytes.intoDataOutput(output);
		}
	}
}