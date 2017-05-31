package us.alksol.cyborg.electrode;

import java.io.EOFException;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import us.alksol.bytestring.Bytes;
import us.alksol.cyborg.electrode.InitialByte.InfoFormat;
import us.alksol.cyborg.electrode.InitialByte.Major;

public class DiagnosticOutput implements CborGenerator {
	Writer output;
	StateGenerator stateGenerator;
	DiagnosticOutput(Writer debugOutput) {
		this.output = debugOutput;
		stateGenerator = new StateGenerator(
				new DefaultDataItemGenerator(
						new FailingGenerator()));
	}
	
	@Override
	public DiagnosticOutput next(CborEvent event) throws IOException {
		stateGenerator.next(event);
		return this;
	}
	private static String bytesToHex(byte[] in) {
	    final StringBuilder builder = new StringBuilder();
	    for(byte b : in) {
	        builder.append(String.format("%02x", b));
	    }
	    return builder.toString();
	}
	private class FailingGenerator implements CborGenerator {
		public FailingGenerator next(CborEvent event) {
			throw new IllegalStateException("no more items expected in well-formed CBOR");
		}
	}
	
	private class DefaultDataItemGenerator implements CborGenerator {
		CborGenerator parent;

		public DefaultDataItemGenerator(CborGenerator parent) {
			this.parent = parent;
		}
		public DefaultDataItemGenerator next(CborEvent event) throws IOException {
			InitialByte ib = event.getInitialByte();
			Major major = ib.getMajor();
			InfoFormat format = ib.getAdditionalInfoFormat();
			
			switch(major) {
			case INTEGER:
				output.write(Long.toUnsignedString(event.getAdditionalInfo()));
				break;
			case NEGATIVE_INTEGER:
				output.write("-");
				output.write(Long.toUnsignedString(event.getAdditionalInfo() + 1));
				break;
			case ETC: {
				
			}
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
				output.write("simple(" + event.getAdditionalInfo() + ")");
				break;
			case FLOAT:
				output.write(Float.toString(event.additionalInfoAsFloat()));
				break;
			case DOUBLE:
				output.write(Double.toString(event.additionalInfoAsDouble()));
				break;
			case HALF_FLOAT:
				output.write("<half float>");
				break;
			case START_BINARY_CHUNKS:
			case START_TEXT_CHUNKS:
			case START_ARRAY_ELEMENTS:
				if (event.isStartOfIndefiniteLengthContainer()) {
					readIndefiniteArray();
				} else {
					readArray((int)event.getAdditionalInfo());
				}
				break;
			case START_MAP_ENTRIES:
				if (event.isStartOfIndefiniteLengthContainer()) {
					readIndefiniteMap();
				} else {
					readMap((int)event.getAdditionalInfo());
				}
				break;
			case BYTE_STRING:
			case TEXT_STRING:
			case ARRAY:
			case MAP:
			case TAG:
				output.write(Long.toUnsignedString(event.getAdditionalInfo()));
				output.write("(");
				stateGenerator.setGenerator(new DefaultDataItemGenerator(this));
				return this;
				break;
				output.write(")");
				break;
			default:
				throw new IllegalStateException(event.toString());
			}
			return this;
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
		if (event.getInitialByte().isIndefinite()) {
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
		if (event.getInitialByte().isIndefinite()) {
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