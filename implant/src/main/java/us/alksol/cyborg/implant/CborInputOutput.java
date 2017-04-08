package us.alksol.cyborg.implant;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import us.alksol.cyborg.implant.DataType.Major;

public class CborInputOutput {
	private final CborInput input;
	private final CborOutput output;
	
	public CborInputOutput(CborInput input, CborOutput output) {
		Objects.requireNonNull(input);
		Objects.requireNonNull(output);
		this.input = input;
		this.output = output;
	}
	
	public void readDataItem() throws IOException, CborException {
		DataType dataType = input.peekDataType();
		Major major = dataType.getMajorType();
		if (major.isCompleteDataItem() && !major.isContainer()) {
			output.writeDataType(input.consumeDataType());
			return;
		}
		switch (major) {
		// this leaves arrays, maps, byte strings and text strings
		case ARRAY:
			output.writeDataType(dataType);
			Optional<Integer> arrayCount = input.readPossiblyIndefiniteArrayCount();
			if (arrayCount.isPresent()) {
				int length = arrayCount.get(); 
				for (int i = 0; i < length; i++) {
					readDataItem();
				}
			}
			else {
				readIndefiniteArray();
			}
			break;
		case MAP:
			output.writeDataType(dataType);
			Optional<Integer> mapPairCount = input.readPossiblyIndefiniteMapPairCount();
			if (mapPairCount.isPresent()) {
				int length = mapPairCount.get() * 2; 
				for (int i = 0; i < length; i++) {
					readDataItem();
				}
			}
			else {
				readIndefiniteMap();
			}
			break;
		case BYTE_STRING:
			if (dataType.isIndeterminate()) {
				output.writeDataType(input.consumeDataType());
				DataType chunk = input.peekDataType();
				while(!chunk.equals(DataType.BREAK)) {
					byte[] data = input.readBytes();
					output.writeBytes(data);
				}
				input.readBreak();
				output.writeBreak();
			} else {
				output.writeBytes(input.readBytes());
			}
			break;
		case TEXT_STRING:
			if (dataType.isIndeterminate()) {
				output.writeDataType(input.consumeDataType());
				DataType chunk = input.peekDataType();
				while(!chunk.equals(DataType.BREAK)) {
					String data = input.readText();
					output.writeText(data);
				}
				input.readBreak();
				output.writeBreak();
			} else {
				output.writeText(input.readText());
			}
		default:
			break;
		}
	}

	private void readIndefiniteMap() throws IOException, CborException {
		while(true) {
			DataType dataType = input.peekDataType();
			if (dataType.equals(DataType.BREAK)) {
				input.readBreak();
				output.writeBreak();
				return;
			} else {
				readDataItem();
				readDataItem();
			}
		}
	}

	private void readIndefiniteArray() throws IOException, CborException {
		while(true) {
			DataType dataType = input.peekDataType();
			if (dataType.equals(DataType.BREAK)) {
				input.readBreak();
				output.writeBreak();
				return;
			} else {
				readDataItem();
			}
		}
	}
}
