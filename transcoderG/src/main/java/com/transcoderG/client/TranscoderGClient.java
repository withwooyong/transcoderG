package com.transcoderG.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import com.transcoderG.redis.TranscoderGRedisCommand;

/**
 * Create a simple Java thread by extending Thread, and managed by Spring’s container via @Component. 
 * The bean scope must be “prototype“, so that each request will return a new instance, to run each individual thread.
 * http://stackoverflow.com/questions/10927718/how-to-read-ffmpeg-response-from-java-and-use-it-to-create-a-progress-bar
 * @author user
 */
@Component
@Scope("prototype")
public class TranscoderGClient implements Runnable {

	protected Logger log = LoggerFactory.getLogger(this.getClass());
	
	private String command;
	
	private String correlation_ID;
	
	private ValueOperations<String, String> valueOps;
	
	private TranscoderGRedisCommand redisCommand;

	public TranscoderGClient(String correlation_ID, String command, ValueOperations<String, String> valueOps, TranscoderGRedisCommand redisCommand) {
		this.correlation_ID = correlation_ID;
		this.command = command;
		this.valueOps = valueOps;
		this.redisCommand = redisCommand;
	}
	
	/**
	 * FFMPEG 실행
	 * FFMPEG Output 메시지를 가지고 redis에 진행상태를 업데이트 한다.
	 */
	@Override
	public void run() {
		log.info("correlation_ID={} command={}", correlation_ID, command);
		//ProcessBuilder pb = new ProcessBuilder(new String[] { "/bin/sh", "-c", command });
		//ProcessBuilder pb = new ProcessBuilder(new String[] { "/bin/sh", "-c", "/root/bin/clienttest", correlation_ID, command });
		//ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", "/root/bin/clienttest", correlation_ID, command);
		
		ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", "/root/ffmpeg_run " + correlation_ID + " \"" + command + "\"");
		
		ProcessBuilder pb2 = new ProcessBuilder("/bin/sh", "-c", "/root/ffmpeg_progress " + correlation_ID);
		
		
		Process p;
		Process p2;
		Scanner sc = null; 
		try {
			p = pb.start();
			p2 = pb2.start();
			int errCode = p.waitFor();
			int errCode2 = p2.waitFor();
			log.info("Echo command executed, any errors1? {}", (errCode == 0 ? "No" : "Yes"));
			log.info("Echo Output1:{}", output(p.getInputStream()));
			
			log.info("Echo command executed, any errors2? {}", (errCode2 == 0 ? "No" : "Yes"));
			log.info("Echo Output2:{}", output(p2.getInputStream()));
			
//			sc = new Scanner(p.getErrorStream());
//			log.info(sc.toString());
			/*
			// Find duration 
			// Duration: 00:20:00.19, start: 0.000000, bitrate: 1127 kb/s Total duration: 1200.19 seconds.
			Pattern durPattern = Pattern.compile("(?<=Duration: )[^,]*");
			String dur = sc.findWithinHorizon(durPattern, 0);
			if (dur == null) {
				throw new RuntimeException("Could not parse duration.");
			}
			String[] hms = dur.split(":"); // Math.round(100.56)
			double totalSecs = Integer.parseInt(hms[0]) * 3600 + Integer.parseInt(hms[1]) * 60 + Math.round(Double.parseDouble(hms[2]));
			log.info("Total duration: " + totalSecs + " seconds.");

			// frame= 3721 fps=336 q=23.0 size=    6208kB time=00:02:05.43 bitrate= 405.4kbits/s dup=1 drop=0    
			// With ffmpeg 2.0, time output is HH:mm:ss.S so the timePattern needs a to incorporate a :
			String match;
			Pattern timePattern = Pattern.compile("(?<=time=)[\\d:.]*");
			String[] matchSplit;
			while (null != (match = sc.findWithinHorizon(timePattern, 0))) {
				matchSplit = match.split(":");
//				for (String str : matchSplit) {
//					log.info("matchSplit : {}", str);
//				}
				
				double progress = (Integer.parseInt(matchSplit[0]) * 3600 + Integer.parseInt(matchSplit[1]) * 60 + Math.round(Double.parseDouble(matchSplit[2]))) / totalSecs;
				log.info("Progress: {} %", Double.parseDouble(String.format("%.2f", progress * 100)));
				
				// redis에 진행상태 업데이트
				String value = (String)valueOps.get(correlation_ID);
				log.info("value={}", value);
				Gson gson = new Gson();
				redisCommand = gson.fromJson(value, TranscoderGRedisCommand.class);
				redisCommand.setProgress(Double.parseDouble(String.format("%.2f", progress * 100)));
				String jsonString = gson.toJson(redisCommand);
				valueOps.getAndSet(correlation_ID, jsonString);
			}
			*/
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			if (sc != null) {
				sc.close();
			}
		}
	}
	
	private static String output(InputStream inputStream) throws IOException {
		StringBuilder sb = new StringBuilder();
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(inputStream));
			String line = null;
			while ((line = br.readLine()) != null) {
				sb.append(line + System.getProperty("line.separator"));
			}
		} finally {
			br.close();
		}
		return sb.toString();
	}

}
