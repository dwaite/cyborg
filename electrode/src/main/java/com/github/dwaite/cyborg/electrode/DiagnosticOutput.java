package com.github.dwaite.cyborg.electrode;

import java.io.EOFException;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.concurrent.LinkedBlockingDeque;

import com.github.dwaite.bytestring.Bytes;
import com.github.dwaite.cyborg.electrode.InitialByte.InfoFormat;
import com.github.dwaite.cyborg.electrode.InitialByte.LogicalType;
import com.github.dwaite.cyborg.electrode.InitialByte.Major;

/**
 * Output provided {@link CborEvent}s as diagnostic output
 */
public class DiagnosticOutput implements CborGenerator, AutoCloseable {
	private Writer output;
	private LinkedBlockingDeque<CborEvent> queue;
	private Thread writer;
	
	DiagnosticOutput(Writer debugOutput) {
		this.output = debugOutput;
		queue = new LinkedBlockingDeque<>(64);
	}
	
	@Override
	public DiagnosticOutput next(CborEvent event) throws IOException {
		if (writer == null) {
			writer = new Thread(new InternalGenerator());
			writer.start();
		}
		try {
			queue.put(event);
		} catch (InterruptedException e) {
			throw new IOException("Interrupted while adding to queue", e);
		}
		return this;
	}
	private class InternalGenerator implements Runnable {

		
		@Override
		public void run() {
			try {
				process();
			} catch (IOException | InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		
		void process() throws IOException, InterruptedException {
			CborEvent event = queue.take();
			if (event == null) {
				throw new ThreadDeath();
			}
			process(event);
		}
		void process(CborEvent event) throws IOException, InterruptedException {
			InitialByte ib = event.getInitialByte();
			Major major = ib.getMajor();
			InfoFormat format = ib.getAdditionalInfoFormat();
			LogicalType type = ib.getLogicalType();
			switch(type) {
			case INTEGRAL:
				if (major == Major.INTEGER) {
					output.write(Long.toUnsignedString(event.getAdditionalInfo()));
				}
				else {
					output.write("-");
					output.write(Long.toUnsignedString(event.getAdditionalInfo() + 1));
				}
				break;
			case BOOLEAN:
				if (ib == InitialByte.TRUE) {
					output.write("true");
				}
				else if (ib == InitialByte.FALSE) {
					output.write("false");
				}
				break;	
			case NULL:
				output.write("null");
				break;
			case UNDEFINED:
				output.write("undefined");
				break;
			case BREAK:
				throw new IllegalStateException("Should not encounter a break outside a variable length container");
			case OTHER_SIMPLE:
				output.write("simple(" + event.getAdditionalInfo() + ")");
				break;
			case FLOATING:
				if (format == InfoFormat.SHORT) {
					output.write("<half float>");
				}
				else if (format == InfoFormat.INT) {
					output.write(Float.toString(event.additionalInfoAsFloat()));
				}
				else if (format == InfoFormat.LONG) {
					output.write(Double.toString(event.additionalInfoAsDouble()));
				}
				break;
			case TAG:
				output.write(Long.toUnsignedString(event.getAdditionalInfo()));
				output.write("(");
				process();
				output.write(")");
				break;
			case START_ARRAY_ELEMENTS:
				readArray((int)event.getAdditionalInfo());
				break;
			case START_INDEFINITE_ARRAY_ELEMENTS:
				readIndefiniteArray();
				break;
			case START_MAP_PAIRS:
				readMap((int)event.getAdditionalInfo());
				break;
			case START_INDEFINITE_MAP_PAIRS:
				readIndefiniteMap();
				break;
			case START_BINARY_CHUNKS:
			case BINARY_CHUNK:
				writeByteString(event);
				break;
			case START_TEXT_CHUNKS:
			case TEXT_CHUNK:
				writeTextString(event);
			};
		}
		
		private String bytesToHex(byte[] in) {
		    final StringBuilder builder = new StringBuilder();
		    for(byte b : in) {
		        builder.append(String.format("%02x", b));
		    }
		    return builder.toString();
		}
		// convert bytes to hex for toString()
		private String bytesToHex(Bytes in) {
			return bytesToHex(in.toByteArray());
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
		
		private void writeTextString(CborEvent event) throws IOException, InterruptedException {
			if (event.getInitialByte().isIndefinite()) {
				StringWriter writer = new StringWriter();
				output.write("(_ ");
				boolean breakEncountered = false;
				while((event = queue.take()) != null) {
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

		private void writeByteString(CborEvent event) throws IOException, InterruptedException {
			if (event.getInitialByte().isIndefinite()) {
				StringWriter writer = new StringWriter();
				output.write("(_ ");
				boolean breakEncountered = false;
				while((event = queue.take()) != null) {
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

		private void readArray(int count) throws IOException, InterruptedException {
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

		private void readIndefiniteArray() throws IOException, InterruptedException {
			output.write("[_ ");
			boolean first = true;
			CborEvent event = null;
			while((event = queue.peek()) != null) {
				if (event.isBreak()) {
					queue.take();
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

		private void readMap(int count) throws IOException, InterruptedException {
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

		private void readIndefiniteMap() throws IOException, InterruptedException {
			output.write("{_ ");
			boolean first = true;
			CborEvent event = null;
			while((event = queue.peek()) != null) {
				if (event.isBreak()) {
					queue.take();
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
	@Override
	public void close() throws Exception {
		writer.join();
	}
}