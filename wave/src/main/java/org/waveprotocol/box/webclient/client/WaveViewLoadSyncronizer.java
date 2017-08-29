package org.waveprotocol.box.webclient.client;

import java.util.HashMap;
import java.util.Map;

import org.waveprotocol.wave.client.common.util.CountdownLatch;
import org.waveprotocol.wave.model.id.WaveId;

import com.google.gwt.user.client.Command;

/**
 * 
 *  Utility class to synchronize code that must be executed iff after a wavelet snapshot is fully
 *  loaded and decrypted.
 *  <p>only
 *  This is required on loading encrypted wavelets which requires an asynchronous process for decrypting.
 *  <p>
 *  Aim is to avoid major changes in client side of the Wave C/S protocol implementation. 
 *  This class is used from {@link RemoteWaveViewService}, where wavelet snapshots are deserialized and 
 *  decrypted from received websocket data if necessary. And also from {@link StageTwoProvider}, 
 *  where UI loading process must wait for snapshots to be ready.
 *  
 * 
 * @author Pablo Ojanguren (pablojan@gmail.com)
 *
 */
public class WaveViewLoadSyncronizer {
	
	private static Map<WaveId, Integer> pedingTicks = new HashMap<WaveId, Integer>();
	private static Map<WaveId, CountdownLatch> latchMap = new HashMap<WaveId, CountdownLatch>();
	
	/**
	 * Signal that a wave is going to be loaded from server, expecting to receive 
	 * a certain number of wavelet snapshots and register a command to be executed
	 * when all wavelet snapshots are ready.
	 * 
	 * @param waveId
	 * @param numOfWavelets
	 * @param onLoaded
	 */
	public static void init(WaveId waveId, int numOfWavelets, Command onLoaded) {
		CountdownLatch latch = CountdownLatch.create(numOfWavelets, onLoaded);
		latchMap.put(waveId, latch);
		if (pedingTicks.containsKey(waveId)) {
			int ticks = pedingTicks.get(waveId);
			for (int i = 0; i < ticks; i++)
				latch.tick();
			
			pedingTicks.remove(waveId);
		}
	}

	/**
	 * Mark that a wavelet of the according wave has been fully loaded.
	 * 
	 * @param waveId
	 */
	public static void tick(WaveId waveId) {
		CountdownLatch latch = latchMap.get(waveId);
		if (latch != null) {
			latch.tick();
		} else  {
			if (!pedingTicks.containsKey(waveId))
				pedingTicks.put(waveId, 0);
			
			int current = pedingTicks.get(waveId);
			pedingTicks.put(waveId, current+1);
		}
	}
}
