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

import java.io.IOException;

import javax.inject.Singleton;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.waveprotocol.box.common.Receiver;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.rpc.ProtoSerializer;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.model.id.IdURIEncoderDecoder;
import org.waveprotocol.wave.model.id.ModernIdSerialiser;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.operation.wave.TransformedWaveletDelta;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.version.HashedVersionFactory;
import org.waveprotocol.wave.model.version.HashedVersionZeroFactoryImpl;
import org.waveprotocol.wave.model.waveref.InvalidWaveRefException;
import org.waveprotocol.wave.model.waveref.WaveRef;
import org.waveprotocol.wave.util.escapers.jvm.JavaUrlCodec;
import org.waveprotocol.wave.util.escapers.jvm.JavaWaverefEncoder;
import org.waveprotocol.wave.util.logging.Log;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.stream.JsonWriter;
import com.google.inject.Inject;

/**
 * GET /crypto/snapshot/{waveId}/{waveletId}
 *
 * List ciphertexts and document pieces needed to decrypt a document snapshot
 *
 * @author pablojan@apache.org (Pablo Ojanguren)
 * @author llop@protonmail.com (David Llop)
 */
@SuppressWarnings("serial")
@Singleton
public class SnapshotServlet extends HttpServlet {
  private static final Log LOG = Log.get(SnapshotServlet.class);

  private static boolean NO_CACHE_RESPONSE = true;

  private static class SnapshotReplayer implements Receiver<TransformedWaveletDelta> {

    final RecoverSnapshot recoverService;

    public SnapshotReplayer() {
      this.recoverService = new RecoverSnapshot();
    }

    public void toJson(JsonWriter writer) throws IOException {
      recoverService.toJson(writer);
    }

    @Override
    public boolean put(TransformedWaveletDelta delta) {
      for (WaveletOperation wop : delta) {
        recoverService.replay(wop);
      }
      return true;
    }
  }

  HashedVersionFactory HASHER = new HashedVersionZeroFactoryImpl(new IdURIEncoderDecoder(new JavaUrlCodec()));

  private final WaveletProvider waveletProvider;

  @Inject
  public SnapshotServlet(WaveletProvider waveletProvider, ProtoSerializer serializer, SessionManager sessionManager) {
    this.waveletProvider = waveletProvider;
  }

  /**
   *
   */
  @Override
  @VisibleForTesting
  protected void doGet(HttpServletRequest req, HttpServletResponse response) throws IOException {

    // This path will look like "/example.com/ew+abc123/foo.com/conv+root"
    // Strip off the leading '/'.
    String urlPath = req.getPathInfo().substring(1);

    // Extract the name of the wavelet from the URL
    WaveRef waveref;
    try {
      waveref = JavaWaverefEncoder.decodeWaveRefFromPath(urlPath);
    } catch (InvalidWaveRefException e) {
      // The URL contains an invalid waveref. There's no document at this path.
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    doGetSnapshot(waveref, response);

  }

  protected void doGetSnapshot(WaveRef waveRef, HttpServletResponse response) throws IOException {

    JsonWriter writer = getResponseJsonWriter(response, NO_CACHE_RESPONSE);
    writer.beginObject();

    writer.name("waveId").value(ModernIdSerialiser.INSTANCE.serialiseWaveId(waveRef.getWaveId()));
    writer.name("waveletId").value(ModernIdSerialiser.INSTANCE.serialiseWaveletId(waveRef.getWaveletId()));
    writer.name("snapshots");

    try {
      SnapshotReplayer replayer = new SnapshotReplayer();
      WaveletName waveletName = WaveletName.of(waveRef.getWaveId(), waveRef.getWaveletId());
      CommittedWaveletSnapshot committedSnaphot = waveletProvider.getSnapshot(waveletName);
      waveletProvider.getHistory(waveletName, HASHER.createVersionZero(waveletName),
          committedSnaphot.snapshot.getHashedVersion(), replayer);
      replayer.toJson(writer);

    } catch (WaveServerException e) {
      LOG.info("Error processing wavelet history", e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
      return;
    }

    writer.endObject();
    writer.flush();
  }

  protected JsonWriter getResponseJsonWriter(HttpServletResponse response, boolean noCache) throws IOException {

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    if (noCache)
      response.setHeader("Cache-Control", "no-store");
    else
      response.setHeader("Cache-control", "public, max-age=86400"); // 24h

    return new JsonWriter(response.getWriter());
  }

}