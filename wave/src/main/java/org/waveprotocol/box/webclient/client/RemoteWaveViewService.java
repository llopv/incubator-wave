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

package org.waveprotocol.box.webclient.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.waveprotocol.box.common.comms.CipherPiece;
import org.waveprotocol.box.common.comms.CipherText;
import org.waveprotocol.box.common.comms.DocumentEncryptedData;
import org.waveprotocol.box.common.comms.DocumentSnapshot;
import org.waveprotocol.box.common.comms.ProtocolSubmitResponse;
import org.waveprotocol.box.common.comms.ProtocolWaveletUpdate;
import org.waveprotocol.box.common.comms.WaveletEncryptedData;
import org.waveprotocol.box.common.comms.WaveletSnapshot;
import org.waveprotocol.box.common.comms.jso.ProtocolSubmitRequestJsoImpl;
import org.waveprotocol.box.common.comms.jso.ProtocolWaveletUpdateJsoImpl;
import org.waveprotocol.box.stat.AsyncCallContext;
import org.waveprotocol.box.webclient.client.WaveCryptoManager.Cipher;
import org.waveprotocol.box.webclient.common.WaveletOperationSerializer;
import org.waveprotocol.wave.client.common.util.ClientPercentEncoderDecoder;
import org.waveprotocol.wave.client.common.util.CountdownLatch;
import org.waveprotocol.wave.client.events.Log;
import org.waveprotocol.wave.concurrencycontrol.channel.WaveViewService;
import org.waveprotocol.wave.concurrencycontrol.common.ResponseCode;
import org.waveprotocol.wave.federation.ProtocolHashedVersion;
import org.waveprotocol.wave.federation.ProtocolWaveletDelta;
import org.waveprotocol.wave.federation.jso.ProtocolWaveletDeltaJsoImpl;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.DocOpComponentType;
import org.waveprotocol.wave.model.document.operation.impl.DocInitializationBuffer;
import org.waveprotocol.wave.model.document.operation.impl.DocOpUtil;
import org.waveprotocol.wave.model.id.IdFilter;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.InvalidIdException;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.version.HashedVersion;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionZeroFactoryImpl;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.DocumentFactory;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.impl.WaveletDataImpl;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.gwt.core.client.Callback;
import com.google.gwt.user.client.Command;

/**
 * Implements the {@link WaveViewService} using RPCs.
 */
public final class RemoteWaveViewService implements WaveViewService, WaveWebSocketCallback {

  private static final Log LOG = Log.get(RemoteWaveViewService.class);

  /**
   * Provides an update notification by lazily extracting and deserializing
   * components out of a serialized update message.
   */
  private class WaveViewServiceUpdateImpl implements WaveViewServiceUpdate {
    private final ProtocolWaveletUpdate update;

    // Cache expensive values
    private List<TransformedWaveletDelta> deltas;
    private ObservableWaveletData snapshot;

    WaveViewServiceUpdateImpl(ProtocolWaveletUpdate update) {
      this.update = update;
    }

    @Override
    public boolean hasChannelId() {
      return update.hasChannelId();
    }

    @Override
    public String getChannelId() {
      return update.getChannelId();
    }

    @Override
    public boolean hasCurrentVersion() {
      return update.hasResultingVersion();
    }

    @Override
    public HashedVersion getCurrentVersion() {
      return deserialize(update.getResultingVersion());
    }

    @Override
    public boolean hasDeltas() {
      return update.getAppliedDeltaSize() > 0;
    }

    @Override
    public List<TransformedWaveletDelta> getDeltaList() {
      return deltas == null //
          ? deltas = deserialize(update.getAppliedDelta(), update.getResultingVersion()) : deltas;
    }

    @Override
    public boolean hasLastCommittedVersion() {
      return update.hasCommitNotice();
    }

    @Override
    public HashedVersion getLastCommittedVersion() {
      return deserialize(update.getCommitNotice());
    }

    @Override
    public boolean hasWaveletId() {
      // The proto definition is incorrect, and is marked as a required field,
      // so there is no generated hasWaveletName().
      return update.getWaveletName() != null;
    }

    @Override
    public WaveletId getWaveletId() {
      return deserialize(update.getWaveletName()).waveletId;
    }

    @Override
    public boolean hasWaveletSnapshot() {
      return update.hasSnapshot();
    }

    @Override
    public ObservableWaveletData getWaveletSnapshot() {
      return snapshot == null ? snapshot = deserialize(waveId, update.getSnapshot(), update.getEncryptedSnapshot(), new Callback<ObservableWaveletData, Exception>() {

			@Override
			public void onFailure(Exception reason) {
				LOG.severe("Exception deserializing snaphost", reason);
			}

			@Override
			public void onSuccess(ObservableWaveletData waveletData) {
				LOG.info("Wavelet "+update.getWaveletName() + " fully loaded!");
				WaveViewLoadSyncronizer.tick(waveId);
			}
			
		})
          : snapshot;
    }

    @Override
    public boolean hasMarker() {
      return update.hasMarker() && update.getMarker();
    }
  }

  /**
   * The box server uses an incompatible signature scheme to the wave-protocol
   * libraries. This manager resolves those incompatibilities.
   */
  private static class VersionSignatureManager {
    private static final HashedVersionFactory HASHER = new HashedVersionZeroFactoryImpl(
        new IdURIEncoderDecoder(new ClientPercentEncoderDecoder()));

    /** Most recent signed versions. */
    private final Map<WaveletName, ProtocolHashedVersion> versions = CollectionUtils.newHashMap();

    /**
     * Records a signed server version.
     */
    void updateHistory(WaveletName wavelet, ProtocolHashedVersion update) {
      ProtocolHashedVersion current = versions.get(wavelet);
      if (current != null && current.getVersion() > update.getVersion()) {
        LOG.info("Ignoring superceded hash update: " + wavelet.toString() + " => " + update.getVersion());
        return;
      }
      versions.put(wavelet, update);
    }

    /**
     * Finds the most recent signed version for a delta.
     */
    ProtocolHashedVersion getServerVersion(WaveletName wavelet, WaveletDelta delta) {
      if (delta.getTargetVersion().getVersion() == 0) {
        return serialize(HASHER.createVersionZero(wavelet));
      } else {
        ProtocolHashedVersion current = versions.get(wavelet);
        Preconditions.checkNotNull(current);
        double prevVersion = current.getVersion();
        double deltaVersion = delta.getTargetVersion().getVersion();
        if (deltaVersion != prevVersion) {
          throw new IllegalArgumentException("Client delta expressed against non-server version.  Server version: "
              + prevVersion + ", client delta: " + deltaVersion);
        }
        return current;
      }
    }
  }

  private final WaveId waveId;
  private final RemoteViewServiceMultiplexer mux;
  private final DocumentFactory<?> docFactory;
  private final VersionSignatureManager versions = new VersionSignatureManager();

  /** Filter for client-side filtering. */
  // TODO: remove after Issue 124 is addressed.
  // http://code.google.com/p/wave-protocol/issues/detail?id=124
  private IdFilter filter;

  /** Callback once opened. */
  private OpenCallback callback;

  private AsyncCallContext openContext;

  /**
   * Creates a service.
   *
   * @param waveId
   *          wave this service serves
   * @param mux
   *          underlying communication channel
   * @param docFactory
   *          document factory to use when deserializing snapshots
   */
  public RemoteWaveViewService(WaveId waveId, RemoteViewServiceMultiplexer mux, DocumentFactory<?> docFactory) {
    this.waveId = waveId;
    this.mux = mux;
    this.docFactory = docFactory;
  }

  //
  // ViewService API.
  //

  @Override
  public void viewOpen(final IdFilter filter, final Map<WaveletId, List<HashedVersion>> knownWavelets,
      final OpenCallback callback) {
    LOG.info("viewOpen called on " + waveId + " with " + filter);

    // Some legacy hack. Important updates are sent to a "dummy+root" wavelet.
    // TODO: remove this once Issue 125 is fixed.
    // http://code.google.com/p/wave-protocol/issues/detail?id=125
    Set<String> newPrefixes = new HashSet<String>(filter.getPrefixes());
    newPrefixes.add("dummy");
    this.filter = IdFilter.of(filter.getIds(), newPrefixes);
    this.callback = callback;

    openContext = AsyncCallContext.start("ProtocolOpenRequest");
    mux.open(waveId, filter, this);
  }

  @Override
  public String viewSubmit(final WaveletName wavelet, WaveletDelta delta, String channelId,
      final SubmitCallback callback) {
    ProtocolSubmitRequestJsoImpl submitRequest = ProtocolSubmitRequestJsoImpl.create();
    submitRequest.setWaveletName(serialize(wavelet));
    submitRequest.setDelta(serialize(wavelet, delta));
    submitRequest.setChannelId(channelId);

    final AsyncCallContext callContext = AsyncCallContext.start("SubmitRequest");
    mux.submit(submitRequest, new SubmitResponseCallback() {
      @Override
      public void run(ProtocolSubmitResponse response) {
        callContext.stop();
        HashedVersion resultVersion = HashedVersion.unsigned(0);
        if (response.hasHashedVersionAfterApplication()) {
          resultVersion = WaveletOperationSerializer.deserialize(response.getHashedVersionAfterApplication());
          versions.updateHistory(wavelet, response.getHashedVersionAfterApplication());
        }
        callback.onSuccess(resultVersion, response.getOperationsApplied(), null, ResponseCode.OK);
      }
    });

    // We don't support the getDebugProfiling thing anyway.
    return null;
  }

  @Override
  public void viewClose(final WaveId waveId, final String channelId, final CloseCallback callback) {
    Preconditions.checkArgument(this.waveId.equals(waveId));
    LOG.info("closing channel " + waveId);
    callback.onSuccess();
    mux.close(waveId, this);
  }

  @Override
  public String debugGetProfilingInfo(final String requestId) {
    throw new UnsupportedOperationException();
  }

  //
  // Incoming updates.
  //

  @Override
  public void onWaveletUpdate(ProtocolWaveletUpdate update) {
    if (shouldAccept(update)) {
      // Update last-known-version map, so that outgoing deltas can be
      // appropriately rewritten.
      if (update.hasResultingVersion()) {
        versions.updateHistory(getTarget(update), update.getResultingVersion());
      }

      // Adapt broken parts of the box server, to make them speak the proper
      // wave protocol:
      // 1. Channel id must be in its own message.
      // 2. Synthesize the open-finished marker that the box server leaves out.
      if (update.hasChannelId() && (update.hasCommitNotice() || update.hasMarker() || update.hasSnapshot()
          || update.getAppliedDeltaSize() > 0)) {
        ProtocolWaveletUpdate fake = ProtocolWaveletUpdateJsoImpl.create();
        fake.setChannelId(update.getChannelId());
        update.clearChannelId();
        callback.onUpdate(deserialize(fake));
        callback.onUpdate(deserialize(update));
      } else {
        if (update.hasMarker()) {
          openContext.stop();
        }
        callback.onUpdate(deserialize(update));
      }
    }
  }

  private WaveViewServiceUpdateImpl deserialize(ProtocolWaveletUpdate update) {
    return new WaveViewServiceUpdateImpl(update);
  }

  /** @return the target wavelet of an update. */
  private WaveletName getTarget(ProtocolWaveletUpdate update) {
    WaveletName name = deserialize(update.getWaveletName());
    Preconditions.checkState(name.waveId.equals(waveId));
    return name;
  }

  /** @return true if this update matches this service's filter. */
  private boolean shouldAccept(ProtocolWaveletUpdate update) {
    return IdFilter.accepts(filter, getTarget(update).waveletId);
  }

  //
  // Serialization.
  //

  private ProtocolWaveletDelta serialize(WaveletName wavelet, WaveletDelta delta) {
    ProtocolWaveletDeltaJsoImpl protocolDelta = ProtocolWaveletDeltaJsoImpl.create();
    for (WaveletOperation op : delta) {
      protocolDelta.addOperation(WaveletOperationSerializer.serialize(op));
    }
    protocolDelta.setAuthor(delta.getAuthor().getAddress());
    protocolDelta.setHashedVersion(versions.getServerVersion(wavelet, delta));
    return protocolDelta;
  }

  private static List<TransformedWaveletDelta> deserialize(List<? extends ProtocolWaveletDelta> deltas,
      ProtocolHashedVersion end) {
    if (deltas == null) {
      return null;
    } else {
      List<TransformedWaveletDelta> parsed = new ArrayList<TransformedWaveletDelta>();
      for (int i = 0; i < deltas.size(); i++) {
        ProtocolHashedVersion thisEnd = //
            i < deltas.size() - 1 ? deltas.get(i + 1).getHashedVersion() : end;
        parsed.add(deserialize(deltas.get(i), thisEnd));
      }
      return parsed;
    }
  }

  private static TransformedWaveletDelta deserialize(ProtocolWaveletDelta delta, ProtocolHashedVersion end) {
    return WaveletOperationSerializer.deserialize(delta, deserialize(end));
  }

  /**
   * Deserialize a snaphost received as a RPC proto object. Decrypt documents if
   * encrypted wavelet data is available.
   * 
   * @param waveId
   * @param snapshot
   * @param waveletEncryptedData
   * @return
   */
  private ObservableWaveletData deserialize(WaveId waveId, WaveletSnapshot snapshot,
      WaveletEncryptedData waveletEncryptedData, Callback<ObservableWaveletData, Exception> onCompleted) {
    WaveletId id;
    try {
      id = ModernIdSerialiser.INSTANCE.deserialiseWaveletId(snapshot.getWaveletId());
    } catch (InvalidIdException e) {
      throw new IllegalArgumentException(e);
    }
    ParticipantId creator = ParticipantId.ofUnsafe(snapshot.getParticipantId(0));
    HashedVersion version = deserialize(snapshot.getVersion());
    long lmt = snapshot.getLastModifiedTime();
    long ctime = snapshot.getCreationTime();
    long lmv = version.getVersion();

    WaveletDataImpl waveletData = new WaveletDataImpl(id, creator, ctime, lmv, version, lmt, waveId, docFactory);
    for (String participant : snapshot.getParticipantId()) {
      waveletData.addParticipant(new ParticipantId(participant));
    }

    boolean isEncryptedWave = IdUtil.isEncryptedWaveId(waveId) && waveletEncryptedData != null;
    Map<String, DocumentEncryptedData> docEncryptedDataMap = new HashMap<String, DocumentEncryptedData>();
    if (isEncryptedWave) {
      waveletEncryptedData.getDocumentData().forEach(docEncryptedData -> {
        docEncryptedDataMap.put(docEncryptedData.getDocumentId(), docEncryptedData);
      });
    }

    // Wait until all documents has been loaded
    final CountdownLatch syncDocDecrypt = CountdownLatch.create(docEncryptedDataMap.size() + 1, new Command() {
        @Override
        public void execute() {
        	if (onCompleted != null) {
        		onCompleted.onSuccess(waveletData);
        	}
        }
      });
    
    for (DocumentSnapshot docSnapshot : snapshot.getDocument()) {

      // Deserialize only if there is encrypted data for the doc
      if (docEncryptedDataMap.containsKey(docSnapshot.getDocumentId()))
        deserializeEncrypted(waveletData, docSnapshot, docEncryptedDataMap.get(docSnapshot.getDocumentId()), new Command() {
			@Override
			public void execute() {
				syncDocDecrypt.tick();
			}        	
        });
      else
        deserialize(waveletData, docSnapshot);
    }
    syncDocDecrypt.tick(); // also wait for non encrypted doc processed synchronously
    
    return waveletData;
  }

  private static void deserialize(WaveletDataImpl waveletData, DocumentSnapshot docSnapshot) {
    DocInitialization content = DocOpUtil
        .asInitialization(WaveletOperationSerializer.deserialize(docSnapshot.getDocumentOperation()));
    String docId = docSnapshot.getDocumentId();
    ParticipantId author = ParticipantId.ofUnsafe(docSnapshot.getAuthor());
    List<ParticipantId> contributors = Lists.newArrayList();
    for (String contributor : docSnapshot.getContributor()) {
      contributors.add(ParticipantId.ofUnsafe(contributor));
    }
    long lmt = docSnapshot.getLastModifiedTime();
    long lmv = docSnapshot.getLastModifiedVersion();
    waveletData.createDocument(docId, author, contributors, content, lmt, lmv);
  }

  /**
   * Deserialize and decrypt a wavelet's encrypted document from a RPC proto object
   * 
   * @param waveletData the wavelet data object containing documents
   * @param docSnapshot proto object having the obfusctated document 
   * @param documentEncryptedData proto object having the encrypted data of the document
   */
  private static void deserializeEncrypted(WaveletDataImpl waveletData, DocumentSnapshot docSnapshot, DocumentEncryptedData documentEncryptedData, Command onCompleted) {

    DocInitialization obfuscatedDocInit =
          DocOpUtil.asInitialization(WaveletOperationSerializer.deserialize(docSnapshot
              .getDocumentOperation()));

    decryptDocInitialization(waveletData.getWaveId(), obfuscatedDocInit, documentEncryptedData, new Callback<DocInitialization, Throwable>() {

      @Override
      public void onFailure(Throwable reason) {
      	LOG.severe("Exception decrypting snapshot document "+ ModernIdSerialiser.INSTANCE.serialiseWaveletId(waveletData.getWaveletId())+ "/" +docSnapshot.getDocumentId());
        throw new RuntimeException(reason.toString());
      }

      @Override
      public void onSuccess(DocInitialization decryptedDocInit) {
    	  
    	LOG.info("Successfully decrypted snapshot document "+ ModernIdSerialiser.INSTANCE.serialiseWaveletId(waveletData.getWaveletId())+ "/" +docSnapshot.getDocumentId());
    	
        String docId = docSnapshot.getDocumentId();
        ParticipantId author = ParticipantId.ofUnsafe(docSnapshot.getAuthor());
        List<ParticipantId> contributors = Lists.newArrayList();
        for (String contributor : docSnapshot.getContributor()) {
          contributors.add(ParticipantId.ofUnsafe(contributor));
        }
        long lmt = docSnapshot.getLastModifiedTime();
        long lmv = docSnapshot.getLastModifiedVersion();
        waveletData.createDocument(docId, author, contributors, decryptedDocInit, lmt, lmv);
        if (onCompleted != null)
        	onCompleted.execute();
      }
  	});
	}

  /**
   * Decrypt operations of a document initialization using encrypted data of the
   * document.
   * 
   * @param encryptedContent
   * @param decryptedDocData
   * @return
   */
  private static void decryptDocInitialization(WaveId waveId, DocInitialization encryptedContent,
      DocumentEncryptedData decryptedDocData, Callback<DocInitialization, Throwable> callback) {
	 
	// no cipher texts => nothing to decrypt  
	if (decryptedDocData.getCipherText().size() == 0) {
		callback.onSuccess(encryptedContent);
	}	
	  
    Cipher cipher = WaveCryptoManager.get().getCipher(waveId);
    Map<Integer, String> ciphertexts = new HashMap<Integer, String>();
    for (CipherText ciphertext: decryptedDocData.getCipherText()) {
      ciphertexts.put((int) ciphertext.getIndex(), ciphertext.getCiphertext());
    }

    cipher.decryptAll(ciphertexts, new WaveCryptoManager.Callback<Map<Integer, String>, Throwable>() {

      @Override
      public void onFailure(Throwable reason) {
        callback.onFailure(reason);
      }

      @Override
      public void onSuccess(Map<Integer, String> texts) {
        String text = "";
        for (CipherPiece piece : decryptedDocData.getCipherPart()) {
          text += texts.get(piece.getOp()).substring(piece.getOffset(), piece.getOffset() + piece.getLength());
        }

        DocInitializationBuffer buffer = new DocInitializationBuffer();
        int offset = 0;
        for (int i = 0; i < encryptedContent.size(); i++) {
          if (encryptedContent.getType(i) == DocOpComponentType.CHARACTERS) {
            int len = encryptedContent.getCharactersString(i).length();
            buffer.characters(text.substring(offset, offset + len));
            offset += len;
          } else {
            encryptedContent.applyComponent(i, buffer);
          }
        }
        callback.onSuccess(buffer.finish());
      }
    });
  }

  private static String serialize(WaveletName wavelet) {
    return RemoteViewServiceMultiplexer.serialize(wavelet);
  }

  private static WaveletName deserialize(String wavelet) {
    WaveletName name = RemoteViewServiceMultiplexer.deserialize(wavelet);
    return name;
  }

  private static ProtocolHashedVersion serialize(HashedVersion version) {
    return WaveletOperationSerializer.serialize(version);
  }

  private static HashedVersion deserialize(ProtocolHashedVersion version) {
    return WaveletOperationSerializer.deserialize(version);
  }
}
