package org.waveprotocol.box.server.crypto;

import org.waveprotocol.box.common.comms.WaveClientRpc.CipherPiece;
import org.waveprotocol.box.common.comms.WaveClientRpc.CipherText;
import org.waveprotocol.box.common.comms.WaveClientRpc.DocumentEncryptedData;
import org.waveprotocol.box.common.comms.WaveClientRpc.WaveletEncryptedData;
import org.waveprotocol.box.common.comms.WaveClientRpc.WaveletEncryptedData.Builder;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveletName;

public class RecoverSnapshotSerializer {

	
	private RecoverSnapshotSerializer() {		
	}
	
	public static WaveletEncryptedData serialize(WaveletName waveletName, RecoverSnapshot encryptedData) {
		
		Builder waveletBuilder = WaveletEncryptedData.newBuilder();
		
		String waveletId = ModernIdSerialiser.INSTANCE.serialiseWaveletId(waveletName.waveletId);
		waveletBuilder.setWaveletId(waveletId);
				
		encryptedData.trees.forEach((docId, pieceTree) -> {
			
			DocumentEncryptedData.Builder docBuilder = DocumentEncryptedData.newBuilder();
			
			// Document Id
			docBuilder.setDocumentId(docId);
			
			// Cipher texts
			pieceTree.getCiphertexts().forEach((index, ciphertext) -> {
				docBuilder.addCipherText(
					 CipherText.newBuilder().setIndex(index).setCiphertext(ciphertext));
			});
						
			// Cipher pieces
			pieceTree.pieces.forEach((index, piece) -> {			
				docBuilder.addCipherPart(
						CipherPiece.newBuilder()
							.setIndex(index)
							.setOp(piece.opId)
							.setLength(piece.len)
							.setOffset(piece.offset));
			});
			
			
			waveletBuilder.addDocumentData(docBuilder);
			
		});
		
		return waveletBuilder.build();		
	}
}
