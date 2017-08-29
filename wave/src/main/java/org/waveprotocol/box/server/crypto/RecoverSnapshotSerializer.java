/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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
        docBuilder.addCipherText(CipherText.newBuilder().setIndex(index).setCiphertext(ciphertext));
      });

      // Cipher pieces
      pieceTree.pieces.forEach((index, piece) -> {
        docBuilder.addCipherPiece(
            CipherPiece.newBuilder().setIndex(index).setOp(piece.opId).setLength(piece.len).setOffset(piece.offset));
      });

      waveletBuilder.addDocumentData(docBuilder);

    });

    return waveletBuilder.build();
  }
}
