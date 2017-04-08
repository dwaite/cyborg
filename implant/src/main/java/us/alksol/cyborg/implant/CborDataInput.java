package us.alksol.cyborg.implant;

import java.io.DataInput;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.function.Consumer;

import us.alksol.cyborg.implant.DataType.Major;

public class CborDataInput implements CborInput {
	final DataInput input;
	DataType header;
	
	public CborDataInput(DataInput input) {
		this.input = input;
		this.header = null;
	}

	@Override
	public DataType peekDataType() throws IOException {
		if (header == null) {
			header = DataType.readDataType(input);
		}
		return header;
	}
	

	public void readText(Consumer<String> textBlockConsumer) throws CborException, IOException {
		peekDataType();
		if (header.getMajorType() != Major.TEXT_STRING) {
			throw new CborIncorrectMajorTypeException(header, Major.TEXT_STRING);
		}
		Charset utf8 = Charset.forName("UTF-8");
		if (peekDataType().isIndeterminate()) {
			header = null;
			byte[] block;
			while ((block = readBlock(Major.TEXT_STRING)) != null) {
				String strBlock = new String(block, utf8);
				textBlockConsumer.accept(strBlock);
			}
		} else {
			byte[] block = readBlock(Major.TEXT_STRING);
			String strBlock = new String(block, utf8);
			textBlockConsumer.accept(strBlock);
		}
	}

	public void readBytes(Consumer<byte[]> byteBlockConsumer) throws CborException, IOException {
		peekDataType();
		if (header.getMajorType() != Major.BYTE_STRING) {
			throw new CborIncorrectMajorTypeException(header, Major.BYTE_STRING);
		}
		if (peekDataType().isIndeterminate()) {
			header = null;
			byte[] block;
			while ((block = readBlock(Major.BYTE_STRING)) != null) {
				byteBlockConsumer.accept(block);
			}
		} else {
			byte[] block = readBlock(Major.BYTE_STRING);
			byteBlockConsumer.accept(block);
		}
	}
	private byte[] readBlock(Major major) throws IOException {
		peekDataType();
		if (header.equals(DataType.BREAK)) {
			header = null;
			return null;
		}
		if (header.getMajorType() != major) {
			throw new IOException("cbor data not well-formed", new CborIncorrectMajorTypeException(header, major));
		}
		int length = (int) header.getValue();
		byte block[] = new byte[length];
		input.readFully(block);
		header = null;
		return block;
	}

	@Override
	public void readCBOR(CborOutput output) throws IOException, CborException {
		throw new UnsupportedOperationException();
	}

	@Override
	public DataType consumeDataType() throws IOException, CborException {
		DataType type = peekDataType();
		header = null;
		return type;
	}

	@Override
	public byte[] readCBOR() throws IOException, CborException {
		// TODO Auto-generated method stub
		return null;
	}
}
