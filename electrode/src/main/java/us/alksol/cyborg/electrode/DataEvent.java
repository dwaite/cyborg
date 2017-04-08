package us.alksol.cyborg.electrode;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Objects;

import us.alksol.bytestring.Bytes;
import us.alksol.cyborg.electrode.impl.CborOptionalValueEventWrapper;

public final class DataEvent implements CborEvent, Comparable<DataEvent>, Serializable {
	private static final long serialVersionUID = 1L;	
	
	// constant big integer to convert values above Long#MAX_VALUE to a Java-compatible numeric
	private static final BigInteger TWO_TO_THE_64TH = 
			new BigInteger(1, new byte[] {0x1, 0, 0, 0, 0, 0, 0, 0, 0});

	private final InitialByte type;
	private final long value;
	private final Bytes data;
	
	/** singletons for single byte complete data events */
	private static final DataEvent IMMEDIATES[];
	
	/** Data item of the integer value zero */
	private static final DataEvent ZERO;

	/** Data item of the integer value one */
	private static final DataEvent ONE;
	
	/** Data item of the simple boolean value `false` */
	private static final DataEvent FALSE;
	
	/** Data item of the simple boolean value `true` */
	private static final DataEvent TRUE;
	
	/** Data item of the simple value `null`, representing the absence of value */
	private static final DataEvent NULL;
	
	/** Data item of the simple value `undefined`, representing a value is neither defined as having a value or 
	 * explicitly as having no value. */
	private static final DataEvent UNDEFINED;
	
	/** Data item representing the `break` signal, terminating an indefinite-length container. */
	private static final DataEvent BREAK;
	
	/** Data item representing a known-zero-length/empty byte array */
	private static final DataEvent EMPTY_BYTES;
	
	/** Data item representing a known-zero-length/empty text string */
	private static final DataEvent EMPTY_TEXT;
	
	/** Data item representing a known-zero-count/empty array */
	private static final DataEvent EMPTY_ARRAY;
	
	/** Data item representing a known-zero-count/empty map */
	private static final DataEvent EMPTY_MAP;
	
	/** Data item representing infinity as a half float */
	private static final DataEvent INF;

	/** Data item representing negative infinity as a half float */
	private static final DataEvent NEG_INF;

	/** Data item representing canonical NaN as a half float */
	private static final DataEvent NAN;

	static {
		IMMEDIATES = new DataEvent[256];
		for (Major major: Major.values()) {
			int hb = major.getHighOrderBits();
			if (major.isPossiblyFollowedByBinaryData()) {
				int ib = hb;
				IMMEDIATES[ib] = new DataEvent(InitialByte.initialByte(ib).get(), 0, Bytes.empty());
				ib = ib | AdditionalInfoFormat.INDEFINITE.getLowOrderBits();
				IMMEDIATES[ib] = new DataEvent(InitialByte.initialByte(ib).get(), 0, null);
			}
			else {
				for(int i =0; i < 24; i++) {
					int ib = hb | i; 
					IMMEDIATES[ib] = new DataEvent(InitialByte.initialByte(ib).get(), i, null);
				}
				if (major.isIndefiniteAllowed()) {
					int ib = hb | AdditionalInfoFormat.INDEFINITE.getLowOrderBits(); 
					IMMEDIATES[ib] = new DataEvent(InitialByte.initialByte(ib).get(), 0, null);
				}
			}
		}
		
		ZERO			= IMMEDIATES[0x00];
		ONE 			= IMMEDIATES[0x01];
		FALSE		= IMMEDIATES[0xf4];
		TRUE			= IMMEDIATES[0xf5];
		NULL			= IMMEDIATES[0xf6];
		UNDEFINED	= IMMEDIATES[0xf7];
		BREAK		= IMMEDIATES[0xff];
		EMPTY_BYTES = IMMEDIATES[0x40];
		EMPTY_TEXT  = IMMEDIATES[0x60];
		EMPTY_ARRAY = IMMEDIATES[0x80];
		EMPTY_MAP	= IMMEDIATES[0xa0];
		
		// common floating-point values
		INF     = new DataEvent(InitialByte.fromMajorAndFormat(Major.ETC, AdditionalInfoFormat.SHORT), 0x7c00, null);
		NEG_INF = new DataEvent(InitialByte.fromMajorAndFormat(Major.ETC, AdditionalInfoFormat.SHORT), 0xfc00, null);
		NAN     = new DataEvent(InitialByte.fromMajorAndFormat(Major.ETC, AdditionalInfoFormat.SHORT), 0x7e00, null);		

	}
	
	protected DataEvent(InitialByte type, long value, Bytes data) {
		Objects.requireNonNull(type, "type");
		if (!type.isIndefinite() && (type.getMajorType() == Major.BYTE_STRING || type.getMajorType() == Major.TEXT_STRING)) {
			Objects.requireNonNull(data, "data");
		} else {
			if (data != null) {
				throw new IllegalArgumentException("data");
			}
		}
		this.type = type;
		this.data = data;
		this.value = value;
	}

	public static DataEvent zero() {
		return ZERO;
	}
	public static DataEvent one() {
		return ONE;
	}
	public static DataEvent ofBoolean(boolean v) {
		return v ? TRUE : FALSE;
	}
	
	public static DataEvent ofNull() {
		return NULL;
	}
	
	public static DataEvent ofUndefined() {
		return UNDEFINED;
	}
	
	public static DataEvent ofBreak() {
		return BREAK;
	}
	
	public static DataEvent emptyBytes() {
		return EMPTY_BYTES;
	}
	
	public static DataEvent emptyText() {
		return EMPTY_TEXT;
	}
	
	public static DataEvent emptyArray() {
		return EMPTY_ARRAY;
	}
	
	public static DataEvent emptyMap() {
		return EMPTY_MAP;
	}
	

	/** Construct a Data item representing a simple value 
	 * 
	 * @param value simple value as an unsigned byte, excluding the values from 24 through 31, which are reserved
	 * to avoid confusion.
	 * @return Data item representing simple value
	 * @throws IllegalArgumentException given value is not in the range appropriate for simple values
	 */
	public static DataEvent ofSimpleValue(int value) throws IllegalArgumentException {
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
		InitialByte type = InitialByte.fromMajorAndFormat(Major.ETC, AdditionalInfoFormat.BYTE);
		return new DataEvent(type, value, null);
	}
	
	static DataEvent ofUnsignedLong(long value) {
		InitialByte ib = InitialByte.forCanonicalLongValue(Major.INTEGER, value);
		DataEvent event = IMMEDIATES[ib.getRepresentation()];
		if (event != null) {
			return event;
		}
		return new DataEvent(ib, value, null);
	}
	
	static CborEvent ofNegativeUnsignedLong(long value) {
		InitialByte ib = InitialByte.forCanonicalLongValue(Major.NEGATIVE_INTEGER, value);
		DataEvent event = IMMEDIATES[ib.getRepresentation()];
		if (event != null) {
			return event;
		}
		return new DataEvent(ib, value, null);
	}

	public static DataEvent ofBytes(byte[] b) {
		return ofFixedBinary(new Bytes(b), false);
	}

	public static DataEvent ofBytes(Bytes b) {
		return ofFixedBinary(b, false);
	}
	
	public static DataEvent ofText(String str) {
		return ofFixedBinary(Bytes.ofUTF8(str), true);
	}

	public static DataEvent ofText(Bytes utf8bytes) {
		return ofFixedBinary(utf8bytes, true);
	}

	public static DataEvent ofFixedBinary(Bytes bytes, boolean isText) {
		int length = bytes.length();
		if (length == 0) {
			return isText ? EMPTY_TEXT : EMPTY_BYTES;
		}
		
		Major major = isText ? Major.TEXT_STRING : Major.BYTE_STRING;
		InitialByte ib = InitialByte.forCanonicalLongValue(major, length);
		DataEvent event = IMMEDIATES[ib.getRepresentation()];
		if (event != null) {
			return event;
		}
		return new DataEvent(ib, length, bytes);
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
	public static DataEvent canonicalOfMajorAndLongValue(Major major, long value) {
		Objects.requireNonNull(major, "major");
		if (major == Major.ETC) {
			throw new IllegalArgumentException(
					"major cannot be a simple or float type, as the value is not to be interpreted as an integer");
		}
		if (major.isPossiblyFollowedByBinaryData()) {
			throw new IllegalArgumentException("following binary data cannot be specified by this factory method");
		}
		if (value < 0) {
			throw new ArithmeticException("value must be non-negative / fit within a signed long");
		}
		InitialByte type;
		AdditionalInfoFormat format = AdditionalInfoFormat.canonicalFromLongValue(value);
		if (format == AdditionalInfoFormat.IMMEDIATE) {
			type = InitialByte.immediate(major, (int) value);
		} else {
			type = InitialByte.fromMajorAndFormat(major, format);
		}
		DataEvent event = IMMEDIATES[type.getRepresentation()];
		if (event != null) {
			return event;
		}
		return new DataEvent(type, value, null);
	}
	
	public static DataEvent ofLong(long value) {
		if (value >= 0) {
			return canonicalOfMajorAndLongValue(Major.INTEGER, value);
		} else {
			value = ~value;
			// TODO - fix duplication from above due to the possibility of value being Long.MIN_VALUE
			AdditionalInfoFormat format = AdditionalInfoFormat.canonicalFromLongValue(value);
			InitialByte type;
			if (format == AdditionalInfoFormat.IMMEDIATE) {
				type = InitialByte.immediate(Major.NEGATIVE_INTEGER, (int) value);
			}
			else {
				type = InitialByte.fromMajorAndFormat(Major.NEGATIVE_INTEGER, format);
			}
			DataEvent event = IMMEDIATES[type.getRepresentation()];
			if (event != null) {
				return event;
			}
			return new DataEvent(type, value, null);
		}
	}
	
	public static DataEvent fromDataInput(DataInput source) throws CborException, IOException, ArithmeticException {
		InitialByte type;
		try {
			type = InitialByte.readDataType(source).orElseThrow(CborNotWellFormedException::new);
		}
		catch (EOFException e) {
			return null;
		}
		try {
			if (IMMEDIATES[type.getRepresentation()] != null) {
				return IMMEDIATES[type.getRepresentation()];
			}
			long rawValue = type.readAdditionalInfo(source);
			Bytes bytes = null;
			if (type.getMajorType().isPossiblyFollowedByBinaryData() && !type.isIndefinite()) {
				if (rawValue < 0 || rawValue > Integer.MAX_VALUE) {
					throw new ArithmeticException("value");
				}
				byte[] data = new byte[(int)rawValue];
				source.readFully(data);
				bytes = new Bytes(data);
			}
			return new DataEvent(type, rawValue, bytes);
		}
		catch (EOFException e) {
			throw new CborNotWellFormedException("EOF encountered in the middle of a data item/event", e);
		}
	}
	
	@Override
	public int compareTo(DataEvent o) {
		Objects.requireNonNull(o);
		if (this == o) {
			return 0;
		}
		int comparison = type.compareTo(o.type);
		if (comparison != 0) {
			return comparison;
		}
		comparison = Long.compareUnsigned(value, value);
		if (comparison != 0) {
			return comparison;
		}
		if (data != null) {
			return data.compareTo(o.data);
		}
		return 0;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (other == null) {
			return false;
		}
		if (!(other instanceof DataEvent)) {
			return false;
		}
		DataEvent rhs = (DataEvent) other;
		if (!type.equals(rhs.type)) {
			return false;
		}
		if (value != rhs.value) {
			return false;
		}
		return Objects.equals(data, rhs.data);	
	}
	
	@Override 
	public int hashCode() {
		return Objects.hash(type, value, data);
	}

	@Override
	public InitialByte getDataType() {
		return type;
	}

	@Override
	public Type getType() {
		return Type.fromDataType(type);
	}

	@Override
	public long rawValue() {
		return value;
	}

	@Override
	public int intValue() {
		switch (type.getMajorType() ) {
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
			AdditionalInfoFormat format = type.getAdditionalInfoFormat();
			if (format != AdditionalInfoFormat.IMMEDIATE && format != AdditionalInfoFormat.BYTE) {
				throw new IllegalStateException("value not integer, tag, or simple type");
			}
			return (int) value;
		default:
			throw new IllegalStateException(new CborIncorrectMajorTypeException(type, Major.INTEGER, Major.NEGATIVE_INTEGER, Major.TAG));	
		}
	}
	
	@Override
	public long longValue() {
		switch (type.getMajorType() ) {
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
			AdditionalInfoFormat format = type.getAdditionalInfoFormat();
			if (format != AdditionalInfoFormat.IMMEDIATE && format != AdditionalInfoFormat.BYTE) {
				throw new IllegalStateException("value not integer, tag, or simple type");
			}
			return value;
		default:
			throw new IllegalStateException(new CborIncorrectMajorTypeException(type, Major.INTEGER, Major.NEGATIVE_INTEGER, Major.TAG));	
		}
	}

	@Override
	public BigInteger bigIntegerValue() {
		try {
			type.assertMajorType(Major.INTEGER, Major.NEGATIVE_INTEGER, Major.TAG, Major.ETC);
		} catch (CborIncorrectMajorTypeException e) {
			throw new IllegalStateException(e);
		}
		BigInteger result = BigInteger.valueOf(value);
		if (value < 0)
			result = result.add(TWO_TO_THE_64TH);
		if (type.getMajorType() == Major.NEGATIVE_INTEGER) {
			result = result.negate();
		}
		return result;
	}

	@Override
	public boolean isIndefiniteLengthContainer() {
		return type.isIndefinite() && type.getMajorType().isContainer();
	}

	@Override
	public int count() throws CborException {
		switch (type.getMajorType() ) {
		case BYTE_STRING:
		case TEXT_STRING:
		case ARRAY:
		case MAP:
			if (isIndefiniteLengthContainer()) {
				return -1;
			}
			if (value < 0 || value > Integer.MAX_VALUE) {
				throw new ArithmeticException("count out of bounds of 32-bit signed integer");
			}
			return (int) value;
		case TAG:
			return 1;
		default:
			throw new CborIncorrectMajorTypeException(type, Major.BYTE_STRING, Major.TEXT_STRING, Major.ARRAY, Major.MAP, Major.TAG);
		}
	}

	@Override
	public short halfFloatValue() {
		try {
			type.assertMajorType(Major.ETC);
		} catch (CborIncorrectMajorTypeException e) {
			throw new IllegalStateException(e);
		}
		if (type.getAdditionalInfoFormat() != AdditionalInfoFormat.SHORT) {
			throw new IllegalStateException("Expected 16 bit half float (binary16)");
		}
		return (short) value;
	}

	@Override
	public float floatValue() {
		try {
			type.assertMajorType(Major.ETC);
		} catch (CborIncorrectMajorTypeException e) {
			throw new IllegalStateException(e);
		}
		if (type.getAdditionalInfoFormat() != AdditionalInfoFormat.INT) {
			throw new IllegalStateException("Expected 32-bit float (binary32)");
		}
		return Float.intBitsToFloat((int) value);
	}

	@Override
	public double doubleValue() {
		try {
			type.assertMajorType(Major.ETC);
		} catch (CborIncorrectMajorTypeException e) {
			throw new IllegalStateException(e);
		}
		if (type.getAdditionalInfoFormat() != AdditionalInfoFormat.LONG) {
			throw new IllegalStateException("Expected 64-bit double (binary64)");
		}
		return Double.longBitsToDouble(value);
	}

	@Override
	public boolean booleanValue() {
		if (type == InitialByte.TRUE) {
			return true;
		}
		if (type == InitialByte.FALSE) {
			return false;
		}
		try {
			type.assertMajorType(Major.ETC);
		} catch (CborIncorrectMajorTypeException e) {
			throw new IllegalStateException(e);
		}
		throw new IllegalStateException("Expected boolean simple type (true or false)");
	}

	@Override
	public boolean isNull() {
		return type == InitialByte.NULL;
	}

	@Override
	public boolean isUndefined() {
		return type == InitialByte.UNDEFINED;
	}

	@Override
	public Bytes bytes() {
		return data;
	}

	@Override
	public String asTextValue() {
		if (data == null) {
			return null;
		}
		return data.asUTF8String();
	}

	@Override
	public boolean isBreak() {
		return type == InitialByte.BREAK;
	}

	@Override
	public boolean isLiteralBreak() {
		return type == InitialByte.BREAK;
	}

	@Override
	public CborOptionalValue optional() {
		return new CborOptionalValueEventWrapper(this);
	}
	
	public static void write(CborEvent event, DataOutput output) throws IOException {
		InitialByte type = event.getDataType();
		type.write(output);
		AdditionalInfoFormat format = type.getAdditionalInfoFormat();
		long rawValue = event.rawValue();
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
		Bytes bytes = event.bytes();
		if (bytes != null && !bytes.isEmpty()) {
			bytes.intoDataOutput(output);
		}
	}

	public static DataEvent startIndefiniteByteArray() {
		return IMMEDIATES[Major.BYTE_STRING.getHighOrderBits() | AdditionalInfoFormat.INDEFINITE.getLowOrderBits()];
	}

	static DataEvent startIndefiniteTextArray() {
		return IMMEDIATES[Major.TEXT_STRING.getHighOrderBits() | AdditionalInfoFormat.INDEFINITE.getLowOrderBits()];
	}

	static DataEvent ofFloat(float v) {
		// TODO Auto-generated method stub
		return null;
	}

	static DataEvent ofDouble(double v) {
		// TODO Auto-generated method stub
		return null;
	}

	static DataEvent ofHalfFloat(short v) {
		// TODO Auto-generated method stub
		return null;
	}

	static DataEvent ofInfinity() {
		return INF;
	}
	
	static DataEvent ofNegativeInfinity() {
		return NEG_INF;
	}
	
	static DataEvent ofNaN() {
		return NAN;
	}

	static DataEvent ofTag(long tag) {
		if (tag < 0) {
			throw new IndexOutOfBoundsException();
		}
		InitialByte ib = InitialByte.forCanonicalLongValue(Major.TAG, tag);
		DataEvent event = IMMEDIATES[ib.getRepresentation()];
		if (event != null) {
			return event;
		}
		return new DataEvent(ib, tag, null);
	}

	static DataEvent startArray(int count) {
		if (count < 0 || count > Integer.MAX_VALUE) {
			throw new IndexOutOfBoundsException();
		}
		InitialByte ib = InitialByte.forCanonicalLongValue(Major.ARRAY, count);
		DataEvent event = IMMEDIATES[ib.getRepresentation()];
		if (event != null) {
			return event;
		}
		return new DataEvent(ib, count, null);
	}

	static DataEvent startIndefiniteArray() {
		return IMMEDIATES[InitialByte.indefinite(Major.ARRAY).getRepresentation()];
	}

	static DataEvent startMap(int keyValuePairs) {
		if (keyValuePairs < 0 || keyValuePairs > Integer.MAX_VALUE) {
			throw new IndexOutOfBoundsException();
		}
		InitialByte ib = InitialByte.forCanonicalLongValue(Major.MAP, keyValuePairs);
		DataEvent event = IMMEDIATES[ib.getRepresentation()];
		if (event != null) {
			return event;
		}
		return new DataEvent(ib, keyValuePairs, null);
	}

	static DataEvent startIndefiniteMap() {
		return IMMEDIATES[InitialByte.indefinite(Major.MAP).getRepresentation()];
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
			DataEvent.write(this, dos);
			dos.close();
			builder.append(bytesToHex(baos.toByteArray()));
		} catch (IOException e) {
			builder.append("exception");
		}
		Major major = getDataType().getMajorType();
		AdditionalInfoFormat format = getDataType().getAdditionalInfoFormat();
		builder.append(" ").append(major).append(":").append(format).append(" ");
		switch (major) {
		case INTEGER:
			builder.append(value);
			break;
		case NEGATIVE_INTEGER:
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
			case INDEFINITE:
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
}

