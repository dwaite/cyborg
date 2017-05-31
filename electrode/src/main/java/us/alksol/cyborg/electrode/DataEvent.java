package us.alksol.cyborg.electrode;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;

import us.alksol.bytestring.Bytes;
import us.alksol.cyborg.electrode.InitialByte.InfoFormat;
import us.alksol.cyborg.electrode.InitialByte.LogicalType;
import us.alksol.cyborg.electrode.InitialByte.Major;

/** This implementation of {@link CborEvent} is meant for general use, most particularly in building an output 
 *  CBOR document. It does not attempt to capture any information on the event source, but as a consequence has many
 *  static factory methods to easily create new events.
 *  
 *  Instances of this type are immutable, and several common values are internally managed as singleton instances.
 */
public final class DataEvent implements CborEvent, Comparable<DataEvent>, Serializable {
	private static final long serialVersionUID = 1L;	

	private final InitialByte type;
	private final long info;
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
	
	static {
		IMMEDIATES = new DataEvent[256];
		for (Major major: Major.values()) {
			int hb = major.getHighOrderBits();
			if (major.isPossiblyFollowedByBinaryData()) {
				int ib = hb;
				IMMEDIATES[ib] = new DataEvent(InitialByte.initialByte(ib).get(), 0, Bytes.empty());
				ib = ib | InfoFormat.INDEFINITE.getLowOrderBits();
				IMMEDIATES[ib] = new DataEvent(InitialByte.initialByte(ib).get(), 0, null);
			}
			else {
				for(int i =0; i < 24; i++) {
					int ib = hb | i; 
					IMMEDIATES[ib] = new DataEvent(InitialByte.initialByte(ib).get(), i, null);
				}
				if (major.isIndefiniteAdditionalInfoAllowed()) {
					int ib = hb | InfoFormat.INDEFINITE.getLowOrderBits(); 
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
	}
	
	protected DataEvent(InitialByte type, long value, Bytes data) {
		Objects.requireNonNull(type, "type");
		LogicalType logicalType = type.getLogicalType();
		if (logicalType == LogicalType.BINARY_CHUNK || logicalType == LogicalType.TEXT_CHUNK) {
			Objects.requireNonNull(data, "data");
		} else {
			if (data != null && !data.isEmpty()) {
				throw new IllegalArgumentException("data");
			}
		}
		this.type = type;
		this.data = data;
		this.info = value;
	}

	/** A data event of the integer value zero */
	public static DataEvent zero() {
		return ZERO;
	}

	/** A data event of the integer value one */
	public static DataEvent one() {
		return ONE;
	}

	/** A data event of the boolean value `true` or `false` */
	public static DataEvent ofBoolean(boolean v) {
		return v ? TRUE : FALSE;
	}
	
	/** A data event of the simple value `null` */
	public static DataEvent ofNull() {
		return NULL;
	}
	
	/** A data event of the simple value `undefined` */
	public static DataEvent ofUndefined() {
		return UNDEFINED;
	}
	
	/** A data event of the `break` signal to end indefinite-length containers */
	public static DataEvent ofBreak() {
		return BREAK;
	}
	
	/** A data event of a byte chunk containing no data. */
	public static DataEvent emptyBytes() {
		return EMPTY_BYTES;
	}
	
	/** A data event of an empty string */
	public static DataEvent emptyText() {
		return EMPTY_TEXT;
	}
	
	/** A data event of an empty array */
	public static DataEvent emptyArray() {
		return EMPTY_ARRAY;
	}
	
	/** A data event of an empty map */
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
		InitialByte type = InitialByte.fromMajorAndFormat(Major.ETC, InfoFormat.BYTE);
		return new DataEvent(type, value, null);
	}
	
	/** Constructs a data item from a long integer value from 0 to 2^^64-1. This value is supplied encoded within a
	 * signed java long primitive.
	 */
	public static DataEvent ofUnsignedLong(long value) {
		InitialByte ib = InitialByte.forCanonicalLongValue(Major.INTEGER, value);
		DataEvent event = IMMEDIATES[ib.getRepresentation()];
		if (event != null) {
			return event;
		}
		return new DataEvent(ib, value, null);
	}
	
	/** Constructs a data item from a negative long integer value from -1 to -2^^64. This value is supplied encoded 
	 * within a signed java long primitive. The value is a one's complement, e.g. -value -1. 0 thus represents -1, 10
	 * represents -11, and so on.
	 */
	public static CborEvent ofNegativeUnsignedLong(long value) {
		InitialByte ib = InitialByte.forCanonicalLongValue(Major.NEGATIVE_INTEGER, value);
		DataEvent event = IMMEDIATES[ib.getRepresentation()];
		if (event != null) {
			return event;
		}
		return new DataEvent(ib, value, null);
	}

	/** Construct a data item of a fixed length of bytes */
	public static DataEvent ofBytes(byte[] b) {
		return ofFixedBinary(new Bytes(b), false);
	}

	/** Construct a data item of a fixed length of bytes */
	public static DataEvent ofBytes(Bytes b) {
		return ofFixedBinary(b, false);
	}
	
	/** Construct a data item of a text string. */
	public static DataEvent ofText(String str) {
		return ofFixedBinary(Bytes.ofUTF8(str), true);
	}

	/** Construct a data item of a text string based on supplied binary data of UTF-8 encoding. No validation is done
	 * to ensure data corresponds to correct UTF-8 encoding. */
	public static DataEvent ofText(Bytes utf8bytes) {
		return ofFixedBinary(utf8bytes, true);
	}

	private static DataEvent ofFixedBinary(Bytes bytes, boolean isText) {
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
		InfoFormat format = InfoFormat.canonicalFromLongValue(value);
		if (format == InfoFormat.IMMEDIATE) {
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
	
	/** Construct a data item of a signed long value. This will be represented as a {@link Major#NEGATIVE_INTEGER} if
	 * the value is negative.
	 */
	public static DataEvent ofLong(long value) {
		if (value >= 0) {
			return canonicalOfMajorAndLongValue(Major.INTEGER, value);
		} else {
			value = ~value;
			// TODO - fix duplication from above due to the possibility of value being Long.MIN_VALUE
			InfoFormat format = InfoFormat.canonicalFromLongValue(value);
			InitialByte type;
			if (format == InfoFormat.IMMEDIATE) {
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
	
	/** Construct a data item via a {@link DataInput} source
	 * 
	 * @param source source of data
	 * @return data event, or `null` if the end of stream is encountered.
	 * 
	 * @throws CborException if data is not well formed.
	 * @throws IOException if the underlying DataInput source fails
	 * @throws ArithmeticException if this value corresponds to a single binary or text chunk larger than
	 * {@link Long#MAX_VALUE} (approx. 2 GiB)
	 */
	public static DataEvent fromDataInput(DataInput source) throws CborNotWellFormedException, IOException, ArithmeticException {
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
			if (type.getMajor().isPossiblyFollowedByBinaryData() && !type.isIndefinite()) {
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
		comparison = Long.compareUnsigned(info, info);
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
		if (info != rhs.info) {
			return false;
		}
		return Objects.equals(data, rhs.data);	
	}
	
	@Override 
	public int hashCode() {
		return Objects.hash(type, info, data);
	}

	@Override
	public InitialByte getInitialByte() {
		return type;
	}

	@Override
	public long getAdditionalInfo() {
		return info;
	}


	@Override
	public Bytes bytes() {
		return data != null ? data : Bytes.empty();
	}

	@Override
	public String asTextValue() {
		if (data == null) {
			return new String();
		}
		return data.asUTF8String();
	}
	
	/** Return a data event marking the start of an indefinite length binary string */
	public static DataEvent startIndefiniteByteArray() {
		return IMMEDIATES[Major.BYTE_STRING.getHighOrderBits() | InfoFormat.INDEFINITE.getLowOrderBits()];
	}

	/** Return a data event marking the start of an indefinite length text string */
	static DataEvent startIndefiniteTextArray() {
		return IMMEDIATES[Major.TEXT_STRING.getHighOrderBits() | InfoFormat.INDEFINITE.getLowOrderBits()];
	}

	/** Return a data event representing this floating point value */
	static DataEvent ofFloat(float v) {
		int value = Float.floatToIntBits(v);
		return new DataEvent(InitialByte.fromMajorAndFormat(Major.ETC, InfoFormat.INT), value, null);
	}

	/** Return a data event representing this double floating point value */
	static DataEvent ofDouble(double v) {
		long value = Double.doubleToLongBits(v);
		return new DataEvent(InitialByte.fromMajorAndFormat(Major.ETC, InfoFormat.LONG), value, null);
	}

	/** Return a data event representing the half float value */
	static DataEvent ofHalfFloat(short v) {
		return new DataEvent(InitialByte.fromMajorAndFormat(Major.ETC, InfoFormat.SHORT), v, null);
	}

	/** Return a data event starting a piece of tagged data.
	 * The tag is interpreted as a signed long, meaning tags of value over {@link Long#MAX_VALUE} are not supported
	 * via this method.
	 * 
	 * @param tag value as signed long
	 */
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

	/** Return a data event starting a known-length array (not terminated with a `break` signal)
	 * 
	 * @param count number of child data items
	 */
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

	/** Return a data event starting a unknown-length array (terminated with a `break` signal)
	 */
	static DataEvent startIndefiniteArray() {
		return IMMEDIATES[InitialByte.indefinite(Major.ARRAY).getRepresentation()];
	}

	/** Return a data event starting a map with a known number of key, value data item pairs (e.g. map not terminated with a `break` signal)
	 * 
	 * @param count number of child data item pairs
	 */
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

	/** Return a data event starting a unknown-length map (terminated with a `break` signal)
	 */
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
			write(dos);
			dos.close();
			builder.append(bytesToHex(baos.toByteArray()));
		} catch (IOException e) {
			builder.append("exception");
		}
		Major major = getInitialByte().getMajor();
		InfoFormat format = getInitialByte().getAdditionalInfoFormat();
		builder.append(" ").append(major).append(":").append(format).append(" ");
		switch (major) {
		case INTEGER:
			builder.append(info);
			break;
		case NEGATIVE_INTEGER:
			builder.append(~info);
			break;
		case BYTE_STRING:
		case TEXT_STRING:
		case ARRAY:
		case MAP:
			builder.append("...");
			break;
		case TAG:
			builder.append("tag#").append(info);
			break;
		case ETC:
			switch (format) {
			case IMMEDIATE:
			case BYTE:
				switch((int)info) {
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
					builder.append("simple(").append(info).append(")");
					break;
				}
				break;
			case INDEFINITE:
				builder.append("BREAK");
				break;
			case SHORT:
				short half = (short) info;
				builder.append("binary16(").append(String.format("%4x", half)).append(")");
				break;
			case INT:
				builder.append("float ").append(Float.intBitsToFloat((int)info));
				break;
			case LONG:
				builder.append("double ").append(Double.longBitsToDouble(info));
				break;				
			}
		}
		builder.append("]");
		return builder.toString();
	}
}

