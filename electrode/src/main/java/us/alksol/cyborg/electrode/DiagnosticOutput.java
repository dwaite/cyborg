package us.alksol.cyborg.electrode;

import java.io.EOFException;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import us.alksol.bytestring.Bytes;

public class DiagnosticOutput {
	Writer output;
	CborParser input;
	private CborEvent event;
	DiagnosticOutput(CborParser input, Writer debugOutput) {
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
	
	public void process() throws IOException {
		if (!input.hasNext()) {
			throw new RuntimeException("Out of data - non-well-formed CBOR?");
		}
		event = input.next();
		CborEvent.Type type = event.getType();
		switch (type) {
		case INTEGER:
			output.write(Long.toUnsignedString(event.rawValue()));
			break;
		case NEGATIVE_INTEGER:
			output.write("-");
			output.write(Long.toUnsignedString(event.rawValue() + 1));
			break;
		case BINARY_CHUNK:
		case START_BINARY_CHUNKS:
			writeByteString();
			break;
		case TEXT_CHUNK:
		case START_TEXT_CHUNKS:
			writeTextString();
			break;
		case START_ARRAY_ELEMENTS:
			if (event.isIndefiniteLengthContainer()) {
				readIndefiniteArray();
			} else {
				readArray((int)event.rawValue());
			}
			break;
		case START_MAP_ENTRIES:
			if (event.isIndefiniteLengthContainer()) {
				readIndefiniteMap();
			} else {
				readMap((int)event.rawValue());
			}
			break;
		case TAG:
			output.write(Long.toUnsignedString(event.rawValue()));
			output.write("(");
			process();
			output.write(")");
			break;
		case TRUE:
			output.write("true");
			break;
		case FALSE:
			output.write("false");
			break;
		case NULL:
			output.write("null");
			break;
		case UNDEFINED:
			output.write("undefined");
			break;
		case OTHER_SIMPLE:
			output.write("simple(" + event.rawValue() + ")");
			break;
		case FLOAT:
			output.write(Float.toString(event.floatValue()));
			break;
		case DOUBLE:
			output.write(Double.toString(event.doubleValue()));
			break;
		case HALF_FLOAT:
			output.write("<half float>");
			break;
		default:
			throw new IllegalStateException(event.toString());
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
	
	private void writeTextString() throws IOException {
		if (event.getDataType().isIndefinite()) {
			StringWriter writer = new StringWriter();
			output.write("(_ ");
			boolean breakEncountered = false;
			while (input.hasNext()) {
				event = input.next();
				if (event.isBreak()) {
					breakEncountered = true;
					break;
				}
				if (writer.getBuffer().length() != 0) {
					writer.write(", ");
				}
				writer.write(escapeAndQuoteText(event.asTextValue()));
			}
			if (!breakEncountered) {
				throw new EOFException();
			}
			output.write(writer.toString());
			output.write(")");
		}
		else {
			output.write(escapeAndQuoteText(event.asTextValue()));
		}
	}

	private void writeByteString() throws IOException {
		if (event.getDataType().isIndefinite()) {
			StringWriter writer = new StringWriter();
			output.write("(_ ");
			boolean breakEncountered = false;
			while (input.hasNext()) {
				event = input.next();
				if (event.isBreak()) {
					breakEncountered = true;
					break;
				}
				if (writer.getBuffer().length() != 0) {
					writer.write(", ");
				}
				writer.write("h'");
				writer.write( bytesToHex(event.bytes()));
				writer.write("'");
			}
			if (!breakEncountered) {
				throw new EOFException();
			}
			output.write(writer.toString());
			output.write(")");
		}
		else {
			output.write("h'");
			output.write( bytesToHex(event.bytes()));
			output.write("'");
		}
	}

	private void readArray(int count) throws IOException {
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

	private void readIndefiniteArray() throws IOException {
		output.write("[_ ");
		boolean first = true;
		while (input.hasNext()) {
			event = input.next();
			if (event.isBreak()) {
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

	private void readMap(int count) throws IOException {
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

	private void readIndefiniteMap() throws IOException {
		output.write("{_ ");
		boolean first = true;
		while (input.hasNext()) {
			event = input.next();
			if (event.isBreak()) {
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
	
	// convert bytes to hex for toString()
	private static String bytesToHex(Bytes in) {
		return bytesToHex(in.toBytes());
	}
}