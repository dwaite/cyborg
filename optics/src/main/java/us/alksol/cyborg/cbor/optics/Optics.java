package us.alksol.cyborg.cbor.optics;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Optional;

import us.alksol.cyborg.implant.CborException;
import us.alksol.cyborg.implant.CborInput;
import us.alksol.cyborg.implant.DataType;
import us.alksol.cyborg.implant.DataType.AdditionalInfoFormat;

public class Optics {
	CborInput input;
	Writer output;
	
	Optics(CborInput input, Writer debugOutput) {
		this.input = input;
		this.output = debugOutput;
	}
	
	public static String bytesToHex(byte[] in) {
	    final StringBuilder builder = new StringBuilder();
	    for(byte b : in) {
	        builder.append(String.format("%02x", b));
	    }
	    return builder.toString();
	}
	void process() throws IOException, CborException {
		CborInput.LogicalType type = input.peekLogicalType();
		switch (type) {
		case INTEGER:
			output.write(Long.toString(input.readLong()));
			break;
		case BYTES:
			writeByteString();
			break;
		case TEXT:
			writeTextString();
			break;
		case ARRAY:
			Optional<Integer> arrayCount = input.readPossiblyIndefiniteArrayCount();
			if (arrayCount.isPresent()) {
				readArray(arrayCount.get());
			} else {
				readIndefiniteArray();
			}
			break;
		case MAP:
			Optional<Integer> mapCount = input.readPossiblyIndefiniteMapPairCount();
			if (mapCount.isPresent()) {
				readMap(mapCount.get());
			} else {
				readIndefiniteMap();
			}
			break;
		case TAG:
			int tag = input.readTag();
			output.write(Long.toString(tag) + "(");
			process();
			output.write(")");
			break;
		case BOOLEAN:
			output.write(Boolean.toString(input.readBoolean()));
			break;
		case NULL:
			input.readNull();
			output.write("null");
		case UNDEFINED:
			input.readUndefined();
			output.write("undefined");
		case SIMPLE:
			int simpleValue = input.readSimpleValue();
			output.write("simple(" + simpleValue + ")");
		case FLOAT:
			output.write(Float.toString(input.readFloat()));
		case DOUBLE:
			output.write(Double.toString(input.readDouble()));
		case HALF_FLOAT:
		default:
			throw new IllegalStateException(input.peekDataType().toString());
		}
	}

	private String escapeAndQuoteText(String input) {
		StringBuilder builder = new StringBuilder();
		builder.append("\"");
		input.chars().forEachOrdered((ch) -> {
			switch (ch) {
			case 0x08:
				builder.append("\\b");
				break;
			case 0x0c:
				builder.append("\\f");
				break;
			case 0x0a:
				builder.append("\\n");
				break;
			case 0x0d:
				builder.append("\\r");
				break;
			case 0x09:
				builder.append("\\t");
				break;
			case 0x22:
				builder.append("\\\"");
				break;
			default:
				if (ch < 0x20) {
					builder.append(String.format("\\u%4d", ch));
				}
				else {
					builder.append((char)ch);
				}
			} 
		});
		builder.append("\"");
		return builder.toString();
	}
	private void writeTextString() throws IOException, CborException {
		if (input.peekDataType().getAdditionalInfo() == AdditionalInfoFormat.INDETERMINATE) {
			StringWriter writer = new StringWriter();
			output.write("(_ ");
			input.readText((chunk) -> {
				if (writer.getBuffer().length() != 0) {
					writer.write(", ");
				}
				writer.write(escapeAndQuoteText(chunk));
			});
			output.write(writer.toString());
			output.write(")");
		}
		else {
			output.write(escapeAndQuoteText(input.readText()));
		}
	}

	private void writeByteString() throws IOException, CborException {
		if (input.peekDataType().getAdditionalInfo() == AdditionalInfoFormat.INDETERMINATE) {
			StringWriter writer = new StringWriter();
			output.write("(_ ");
			input.readBytes((chunk) -> {
				if (writer.getBuffer().length() != 0) {
					writer.write(", ");
				}
				writer.write("h'" + bytesToHex(chunk) + "'");
			});
			output.write(writer.toString());
			output.write(")");
		}
		else {
			output.write("h'" + bytesToHex(input.readBytes()) + "'");
		}
	}

	private void readArray(int count) throws IOException, CborException {
		output.write("[");
		boolean first = true;
		for (int i = 0; i< count; i++) {
			if (!first) {
				output.write(", ");
			}
			first = false;			
			process();
		}
		output.write("]");
	}

	private void readIndefiniteArray() throws IOException, CborException {
		output.write("[_ ");
		boolean first = true;
		while (true) {
			DataType type = input.peekDataType();
			if(type.equals(DataType.BREAK)) {
				input.readBreak();
				output.write("]");
				return;
			}
			if (!first) {
				output.write(", ");
			}
			first = false;
			
			process();
		}
	}

	private void readMap(int count) throws IOException, CborException {
		output.write("{");
		boolean first = true;
		for (int i = 0; i< count; i++) {
			if (!first) {
				output.write(", ");
			}
			first = false;			
			process();
			output.write(": ");
			process();
		}
		output.write("}");
	}

	private void readIndefiniteMap() throws IOException, CborException {
		output.write("{_ ");
		boolean first = true;
		while (true) {
			DataType type = input.peekDataType();
			if(type.equals(DataType.BREAK)) {
				input.readBreak();
				output.write("}");
				return;
			}
			if (!first) {
				output.write(", ");
			}
			first = false;
			process();
			output.write(": ");
			process();
		}
	}
}