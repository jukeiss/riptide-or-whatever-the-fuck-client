package riptide.util.lan;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public abstract class LanPacket {
   protected final LanPacketType type;
   protected String sessionId;

   public LanPacket(LanPacketType type, String sessionId) {
      this.type = type;
      this.sessionId = sessionId != null ? sessionId : "";
   }

   public LanPacketType getType() {
      return this.type;
   }

   public String getSessionId() {
      return this.sessionId;
   }

   public void setSessionId(String sessionId) {
      this.sessionId = sessionId;
   }

   public abstract void write(DataOutputStream var1) throws IOException;

   public abstract void read(DataInputStream var1) throws IOException;

   protected static int readCount(DataInputStream in, int limit) throws IOException {
      int count = in.readInt();
      if (count >= 0 && count <= limit) {
         return count;
      } else {
         throw new IOException("Collection length out of range: " + count);
      }
   }

   public static LanPacket create(LanPacketType type, String sessionId) {
      switch (type) {
         case SEARCH_REQUEST:
            return new LanPacket.SearchRequestPacket(sessionId);
         case SESSION:
            return new LanPacket.SessionPacket(sessionId, 0L, "");
         case JOIN:
            return new LanPacket.JoinPacket(sessionId, "");
         case REQUEST_CLIENT_LIST:
            return new LanPacket.RequestClientListPacket(sessionId);
         case CLIENT_LIST:
            return new LanPacket.ClientListPacket(sessionId, new ArrayList<>());
         case LEAVE:
            return new LanPacket.LeavePacket(sessionId, "");
         case REQUEST_MACRO:
            return new LanPacket.RequestMacroPacket(sessionId, "");
         case MACRO_DATA:
            return new LanPacket.MacroDataPacket(sessionId, "", "", "", (byte)0);
         case MACRO_DELETE:
            return new LanPacket.MacroDeletePacket(sessionId, "", "");
         case MACRO_LIST:
            return new LanPacket.MacroListPacket(sessionId, "", new ArrayList<>());
         case QUEUE_SYNC:
            return new LanPacket.QueueSyncPacket(sessionId, "", "");
         case PRESET_DATA:
            return new LanPacket.PresetDataPacket(sessionId, "", "", "");
         case CHAT_MESSAGE:
            return new LanPacket.ChatMessagePacket(sessionId, "", "");
         case TCP_COMMAND_ACK:
            return new LanPacket.TcpCommandAckPacket(sessionId, "", 0L);
         case PLAYER_OFFSETS:
            return new LanPacket.PlayerOffsetsPacket(sessionId, new HashMap<>());
         case CLIENT_OFFSET_UPDATE:
            return new LanPacket.ClientOffsetUpdatePacket(sessionId, "", "", 0);
         case PREPARE_EXECUTION:
            return new LanPacket.PrepareExecutionPacket(sessionId, 0L, false, "", "", "");
         case CLIENT_READY:
            return new LanPacket.ClientReadyPacket(sessionId, 0L, "");
         case EXECUTE_NOW:
            return new LanPacket.ExecuteNowPacket(sessionId, 0L, 0L);
         case GO:
            return new LanPacket.GoPacket(sessionId, 0L);
         case HEARTBEAT:
            return new LanPacket.HeartbeatPacket(sessionId, "", 0L);
         case SYNC_STATE_UPDATE:
            return new LanPacket.SyncStateUpdatePacket(sessionId, 0, "");
         case REQUEST_SYNC:
            return new LanPacket.RequestSyncPacket(sessionId, "", false, "", "");
         case OFFSET_SYNC:
            return new LanPacket.OffsetSyncPacket(sessionId, "", "", 0);
         case MACRO_STEP_PROGRESS:
            return new LanPacket.MacroStepProgressPacket(sessionId, "", 0, 0, "");
         case MACRO_DATA_CHUNK:
            return new LanPacket.MacroDataChunkPacket(sessionId, "", "", (byte)0, 0, 0, new byte[0]);
         case MACRO_ASSIGNMENT_SYNC:
            return new LanPacket.MacroAssignmentSyncPacket(sessionId, "", "");
         default:
            return null;
      }
   }

   public static class ChatMessagePacket extends LanPacket {
      public String senderUsername;
      public String message;

      public ChatMessagePacket(String sessionId, String senderUsername, String message) {
         super(LanPacketType.CHAT_MESSAGE, sessionId);
         this.senderUsername = senderUsername != null ? senderUsername : "";
         this.message = message != null ? message : "";
      }

      public ChatMessagePacket() {
         super(LanPacketType.CHAT_MESSAGE, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.senderUsername);
         out.writeUTF(this.message);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.senderUsername = in.readUTF();
         this.message = in.readUTF();
      }
   }

   public static class ClientListPacket extends LanPacket {
      public List<LanPacket.ClientListPacket.ClientEntry> clients = new ArrayList<>();
      public int executionMethod = 0;

      public ClientListPacket(String sessionId, List<LanPacket.ClientListPacket.ClientEntry> clients) {
         super(LanPacketType.CLIENT_LIST, sessionId);
         this.clients = clients;
      }

      public ClientListPacket(String sessionId, List<LanPacket.ClientListPacket.ClientEntry> clients, int executionMethod) {
         this(sessionId, clients);
         this.executionMethod = executionMethod;
      }

      public ClientListPacket() {
         super(LanPacketType.CLIENT_LIST, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeInt(this.clients.size());

         for (LanPacket.ClientListPacket.ClientEntry client : this.clients) {
            out.writeUTF(client.name);
            out.writeBoolean(client.isHost);
         }

         out.writeInt(this.executionMethod);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         int size = readCount(in, 1024);
         this.clients.clear();

         for (int i = 0; i < size; i++) {
            this.clients.add(new LanPacket.ClientListPacket.ClientEntry(in.readUTF(), in.readBoolean()));
         }

         try {
            this.executionMethod = in.readInt();
         } catch (EOFException var4) {
            this.executionMethod = 0;
         }
      }

      public static class ClientEntry {
         public String name;
         public boolean isHost;

         public ClientEntry(String name, boolean isHost) {
            this.name = name;
            this.isHost = isHost;
         }
      }
   }

   public static class ClientOffsetUpdatePacket extends LanPacket {
      public String senderUsername;
      public String targetUsername;
      public int offsetMs;

      public ClientOffsetUpdatePacket(String sessionId, String senderUsername, String targetUsername, int offsetMs) {
         super(LanPacketType.CLIENT_OFFSET_UPDATE, sessionId);
         this.senderUsername = senderUsername;
         this.targetUsername = targetUsername;
         this.offsetMs = offsetMs;
      }

      public ClientOffsetUpdatePacket() {
         super(LanPacketType.CLIENT_OFFSET_UPDATE, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.senderUsername);
         out.writeUTF(this.targetUsername);
         out.writeInt(this.offsetMs);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.senderUsername = in.readUTF();
         this.targetUsername = in.readUTF();
         this.offsetMs = in.readInt();
      }
   }

   public static class ClientReadyPacket extends LanPacket {
      public long executionId;
      public String senderUsername;

      public ClientReadyPacket(String sessionId, long executionId, String senderUsername) {
         super(LanPacketType.CLIENT_READY, sessionId);
         this.executionId = executionId;
         this.senderUsername = senderUsername;
      }

      public ClientReadyPacket() {
         super(LanPacketType.CLIENT_READY, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeLong(this.executionId);
         out.writeUTF(this.senderUsername);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.executionId = in.readLong();
         this.senderUsername = in.readUTF();
      }
   }

   public static class ExecuteNowPacket extends LanPacket {
      public long executionId;
      public long targetTime;

      public ExecuteNowPacket(String sessionId, long executionId, long targetTime) {
         super(LanPacketType.EXECUTE_NOW, sessionId);
         this.executionId = executionId;
         this.targetTime = targetTime;
      }

      public ExecuteNowPacket() {
         super(LanPacketType.EXECUTE_NOW, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeLong(this.executionId);
         out.writeLong(this.targetTime);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.executionId = in.readLong();
         this.targetTime = in.readLong();
      }
   }

   public static class GoPacket extends LanPacket {
      public long executionId;
      public int executionMethod = 0;
      public long targetTick = -1L;

      public GoPacket(String sessionId, long executionId) {
         super(LanPacketType.GO, sessionId);
         this.executionId = executionId;
      }

      public GoPacket(String sessionId, long executionId, int executionMethod, long targetTick) {
         this(sessionId, executionId);
         this.executionMethod = executionMethod;
         this.targetTick = targetTick;
      }

      public GoPacket() {
         super(LanPacketType.GO, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeLong(this.executionId);
         out.writeInt(this.executionMethod);
         out.writeLong(this.targetTick);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.executionId = in.readLong();

         try {
            this.executionMethod = in.readInt();
            this.targetTick = in.readLong();
         } catch (EOFException var3) {
            this.executionMethod = 0;
            this.targetTick = -1L;
         }
      }
   }

   public static class HeartbeatPacket extends LanPacket {
      public String senderUsername;
      public long timestamp;

      public HeartbeatPacket(String sessionId, String senderUsername, long timestamp) {
         super(LanPacketType.HEARTBEAT, sessionId);
         this.senderUsername = senderUsername != null ? senderUsername : "";
         this.timestamp = timestamp;
      }

      public HeartbeatPacket() {
         super(LanPacketType.HEARTBEAT, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.senderUsername);
         out.writeLong(this.timestamp);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.senderUsername = in.readUTF();
         this.timestamp = in.readLong();
      }
   }

   public static class JoinPacket extends LanPacket {
      public String username;

      public JoinPacket(String sessionId, String username) {
         super(LanPacketType.JOIN, sessionId);
         this.username = username;
      }

      public JoinPacket() {
         super(LanPacketType.JOIN, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.username);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.username = in.readUTF();
      }
   }

   public static class LeavePacket extends LanPacket {
      public String username;

      public LeavePacket(String sessionId, String username) {
         super(LanPacketType.LEAVE, sessionId);
         this.username = username;
      }

      public LeavePacket() {
         super(LanPacketType.LEAVE, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.username);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.username = in.readUTF();
      }
   }

   public static class MacroAssignmentSyncPacket extends LanPacket {
      public String senderUsername = "";
      public String assignments = "";

      public MacroAssignmentSyncPacket(String sessionId, String senderUsername, String assignments) {
         super(LanPacketType.MACRO_ASSIGNMENT_SYNC, sessionId);
         this.senderUsername = senderUsername != null ? senderUsername : "";
         this.assignments = assignments != null ? assignments : "";
      }

      public MacroAssignmentSyncPacket() {
         super(LanPacketType.MACRO_ASSIGNMENT_SYNC, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.senderUsername);
         out.writeUTF(this.assignments);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.senderUsername = in.readUTF();
         this.assignments = in.readUTF();
      }
   }

   public static class MacroDataChunkPacket extends LanPacket {
      public String senderUsername;
      public static final int MAX_CHUNK_BYTES = 64000;
      public String macroName;
      public byte sourceType;
      public int chunkIndex;
      public int totalChunks;
      public byte[] chunkData;

      public MacroDataChunkPacket(String sessionId, String senderUsername, String macroName, byte sourceType, int chunkIndex, int totalChunks, byte[] chunkData) {
         super(LanPacketType.MACRO_DATA_CHUNK, sessionId);
         this.senderUsername = senderUsername != null ? senderUsername : "";
         this.macroName = macroName != null ? macroName : "";
         this.sourceType = sourceType;
         this.chunkIndex = chunkIndex;
         this.totalChunks = totalChunks;
         this.chunkData = chunkData != null ? chunkData : new byte[0];
      }

      public MacroDataChunkPacket() {
         super(LanPacketType.MACRO_DATA_CHUNK, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.senderUsername);
         out.writeUTF(this.macroName);
         out.writeByte(this.sourceType);
         out.writeInt(this.chunkIndex);
         out.writeInt(this.totalChunks);
         out.writeInt(this.chunkData.length);
         out.write(this.chunkData);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.senderUsername = in.readUTF();
         this.macroName = in.readUTF();
         this.sourceType = in.readByte();
         this.chunkIndex = in.readInt();
         this.totalChunks = in.readInt();
         int len = in.readInt();
         if (len >= 0 && len <= 64000) {
            this.chunkData = new byte[len];
            in.readFully(this.chunkData);
         } else {
            throw new IOException("Macro chunk length out of range: " + len);
         }
      }
   }

   public static class MacroDataPacket extends LanPacket {
      public String senderUsername;
      public String macroName;
      public String nbtData;
      public byte sourceType;

      public MacroDataPacket(String sessionId, String senderUsername, String macroName, String nbtData, byte sourceType) {
         super(LanPacketType.MACRO_DATA, sessionId);
         this.senderUsername = senderUsername;
         this.macroName = macroName;
         this.nbtData = nbtData != null ? nbtData : "";
         this.sourceType = sourceType;
      }

      public MacroDataPacket() {
         super(LanPacketType.MACRO_DATA, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.senderUsername);
         out.writeUTF(this.macroName);
         out.writeUTF(this.nbtData);
         out.writeByte(this.sourceType);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.senderUsername = in.readUTF();
         this.macroName = in.readUTF();
         this.nbtData = in.readUTF();

         try {
            this.sourceType = in.readByte();
         } catch (EOFException var3) {
            this.sourceType = 0;
         }
      }
   }

   public static class MacroDeletePacket extends LanPacket {
      public String senderUsername;
      public String macroName;

      public MacroDeletePacket(String sessionId, String senderUsername, String macroName) {
         super(LanPacketType.MACRO_DELETE, sessionId);
         this.senderUsername = senderUsername;
         this.macroName = macroName;
      }

      public MacroDeletePacket() {
         super(LanPacketType.MACRO_DELETE, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.senderUsername);
         out.writeUTF(this.macroName);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.senderUsername = in.readUTF();
         this.macroName = in.readUTF();
      }
   }

   public static class MacroListPacket extends LanPacket {
      public String senderUsername;
      public List<String> macroNames = new ArrayList<>();

      public MacroListPacket(String sessionId, String senderUsername, List<String> macroNames) {
         super(LanPacketType.MACRO_LIST, sessionId);
         this.senderUsername = senderUsername;
         this.macroNames = macroNames;
      }

      public MacroListPacket() {
         super(LanPacketType.MACRO_LIST, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.senderUsername);
         out.writeInt(this.macroNames.size());

         for (String name : this.macroNames) {
            out.writeUTF(name);
         }
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.senderUsername = in.readUTF();
         int size = readCount(in, 100000);
         this.macroNames.clear();

         for (int i = 0; i < size; i++) {
            this.macroNames.add(in.readUTF());
         }
      }
   }

   public static class MacroStepProgressPacket extends LanPacket {
      public String senderUsername;
      public int completedStep;
      public int totalSteps;
      public String macroName;

      public MacroStepProgressPacket(String sessionId, String senderUsername, int completedStep, int totalSteps, String macroName) {
         super(LanPacketType.MACRO_STEP_PROGRESS, sessionId);
         this.senderUsername = senderUsername != null ? senderUsername : "";
         this.completedStep = completedStep;
         this.totalSteps = totalSteps;
         this.macroName = macroName != null ? macroName : "";
      }

      public MacroStepProgressPacket() {
         super(LanPacketType.MACRO_STEP_PROGRESS, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.senderUsername);
         out.writeInt(this.completedStep);
         out.writeInt(this.totalSteps);
         out.writeUTF(this.macroName);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.senderUsername = in.readUTF();
         this.completedStep = in.readInt();
         this.totalSteps = in.readInt();
         this.macroName = in.readUTF();
      }
   }

   public static class OffsetSyncPacket extends LanPacket {
      public String senderUsername;
      public String targetUsername;
      public int offsetMs;

      public OffsetSyncPacket(String sessionId, String senderUsername, String targetUsername, int offsetMs) {
         super(LanPacketType.OFFSET_SYNC, sessionId);
         this.senderUsername = senderUsername != null ? senderUsername : "";
         this.targetUsername = targetUsername != null ? targetUsername : "";
         this.offsetMs = offsetMs;
      }

      public OffsetSyncPacket() {
         super(LanPacketType.OFFSET_SYNC, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.senderUsername);
         out.writeUTF(this.targetUsername);
         out.writeInt(this.offsetMs);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.senderUsername = in.readUTF();
         this.targetUsername = in.readUTF();
         this.offsetMs = in.readInt();
      }
   }

   public static class PlayerOffsetsPacket extends LanPacket {
      public Map<String, Integer> offsets = new HashMap<>();

      public PlayerOffsetsPacket(String sessionId, Map<String, Integer> offsets) {
         super(LanPacketType.PLAYER_OFFSETS, sessionId);
         this.offsets = offsets != null ? new HashMap<>(offsets) : new HashMap<>();
      }

      public PlayerOffsetsPacket() {
         super(LanPacketType.PLAYER_OFFSETS, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeInt(this.offsets.size());

         for (Entry<String, Integer> entry : this.offsets.entrySet()) {
            out.writeUTF(entry.getKey());
            out.writeInt(entry.getValue());
         }
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         int count = readCount(in, 1024);
         this.offsets = new HashMap<>();

         for (int i = 0; i < count; i++) {
            String player = in.readUTF();
            int offset = in.readInt();
            this.offsets.put(player, offset);
         }
      }
   }

   public static class PrepareExecutionPacket extends LanPacket {
      public long executionId;
      public boolean isMacro;
      public String targetName;
      public String queueData;
      public String senderUsername;
      public String macroAssignments = "";
      public int executionMethod = 0;
      public long targetTick = -1L;

      public PrepareExecutionPacket(String sessionId, long executionId, boolean isMacro, String targetName, String queueData, String senderUsername) {
         super(LanPacketType.PREPARE_EXECUTION, sessionId);
         this.executionId = executionId;
         this.isMacro = isMacro;
         this.targetName = targetName;
         this.queueData = queueData != null ? queueData : "";
         this.senderUsername = senderUsername;
      }

      public PrepareExecutionPacket(
         String sessionId, long executionId, boolean isMacro, String targetName, String queueData, String senderUsername, int executionMethod, long targetTick
      ) {
         this(sessionId, executionId, isMacro, targetName, queueData, senderUsername);
         this.executionMethod = executionMethod;
         this.targetTick = targetTick;
      }

      public PrepareExecutionPacket() {
         super(LanPacketType.PREPARE_EXECUTION, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeLong(this.executionId);
         out.writeBoolean(this.isMacro);
         out.writeUTF(this.targetName);
         out.writeUTF(this.queueData);
         out.writeUTF(this.senderUsername);
         out.writeUTF(this.macroAssignments);
         out.writeInt(this.executionMethod);
         out.writeLong(this.targetTick);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.executionId = in.readLong();
         this.isMacro = in.readBoolean();
         this.targetName = in.readUTF();
         this.queueData = in.readUTF();

         try {
            this.senderUsername = in.readUTF();
         } catch (EOFException var5) {
            this.senderUsername = "";
            return;
         }

         try {
            this.macroAssignments = in.readUTF();
         } catch (EOFException var4) {
            this.macroAssignments = "";
            return;
         }

         try {
            this.executionMethod = in.readInt();
            this.targetTick = in.readLong();
         } catch (EOFException var3) {
            this.executionMethod = 0;
            this.targetTick = -1L;
         }
      }
   }

   public static class PresetDataPacket extends LanPacket {
      public String senderUsername;
      public String presetName;
      public String presetJson;

      public PresetDataPacket(String sessionId, String senderUsername, String presetName, String presetJson) {
         super(LanPacketType.PRESET_DATA, sessionId);
         this.senderUsername = senderUsername;
         this.presetName = presetName;
         this.presetJson = presetJson;
      }

      public PresetDataPacket() {
         super(LanPacketType.PRESET_DATA, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.senderUsername);
         out.writeUTF(this.presetName);
         out.writeUTF(this.presetJson);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.senderUsername = in.readUTF();
         this.presetName = in.readUTF();
         this.presetJson = in.readUTF();
      }
   }

   public static class QueueSyncPacket extends LanPacket {
      public String queueData;
      public String senderUsername;

      public QueueSyncPacket(String sessionId, String queueData, String senderUsername) {
         super(LanPacketType.QUEUE_SYNC, sessionId);
         this.queueData = queueData;
         this.senderUsername = senderUsername;
      }

      public QueueSyncPacket() {
         super(LanPacketType.QUEUE_SYNC, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.queueData);
         out.writeUTF(this.senderUsername);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.queueData = in.readUTF();
         this.senderUsername = in.readUTF();
      }
   }

   public static class RequestClientListPacket extends LanPacket {
      public RequestClientListPacket(String sessionId) {
         super(LanPacketType.REQUEST_CLIENT_LIST, sessionId);
      }

      public RequestClientListPacket() {
         super(LanPacketType.REQUEST_CLIENT_LIST, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
      }

      @Override
      public void read(DataInputStream in) throws IOException {
      }
   }

   public static class RequestMacroPacket extends LanPacket {
      public String macroName;

      public RequestMacroPacket(String sessionId, String macroName) {
         super(LanPacketType.REQUEST_MACRO, sessionId);
         this.macroName = macroName;
      }

      public RequestMacroPacket() {
         super(LanPacketType.REQUEST_MACRO, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.macroName);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.macroName = in.readUTF();
      }
   }

   public static class RequestSyncPacket extends LanPacket {
      public String senderUsername;
      public boolean isMacro;
      public String macroName;
      public String command;
      public int executionMethod = 0;

      public RequestSyncPacket(String sessionId, String senderUsername, boolean isMacro, String macroName, String command) {
         super(LanPacketType.REQUEST_SYNC, sessionId);
         this.senderUsername = senderUsername != null ? senderUsername : "";
         this.isMacro = isMacro;
         this.macroName = macroName != null ? macroName : "";
         this.command = command != null ? command : "";
      }

      public RequestSyncPacket() {
         super(LanPacketType.REQUEST_SYNC, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.senderUsername);
         out.writeBoolean(this.isMacro);
         out.writeUTF(this.macroName);
         out.writeUTF(this.command);
         out.writeInt(this.executionMethod);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.senderUsername = in.readUTF();
         this.isMacro = in.readBoolean();
         this.macroName = in.readUTF();
         this.command = in.readUTF();

         try {
            this.executionMethod = in.readInt();
         } catch (EOFException var3) {
            this.executionMethod = 0;
         }
      }
   }

   public static class SearchRequestPacket extends LanPacket {
      public SearchRequestPacket(String sessionId) {
         super(LanPacketType.SEARCH_REQUEST, sessionId);
      }

      public SearchRequestPacket() {
         super(LanPacketType.SEARCH_REQUEST, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
      }

      @Override
      public void read(DataInputStream in) throws IOException {
      }
   }

   public static class SessionPacket extends LanPacket {
      public long startTime;
      public String hostName;
      public byte transportType;

      public SessionPacket(String sessionId, long startTime, String hostName, byte transportType) {
         super(LanPacketType.SESSION, sessionId);
         this.startTime = startTime;
         this.hostName = hostName;
         this.transportType = transportType;
      }

      public SessionPacket(String sessionId, long startTime, String hostName) {
         this(sessionId, startTime, hostName, (byte)1);
      }

      public SessionPacket() {
         super(LanPacketType.SESSION, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeLong(this.startTime);
         out.writeUTF(this.hostName);
         out.writeByte(this.transportType);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.startTime = in.readLong();
         this.hostName = in.readUTF();

         try {
            this.transportType = in.readByte();
         } catch (EOFException var3) {
            this.transportType = 1;
         }
      }
   }

   public static class SyncStateUpdatePacket extends LanPacket {
      public int stateOrdinal;
      public String detail;

      public SyncStateUpdatePacket(String sessionId, int stateOrdinal, String detail) {
         super(LanPacketType.SYNC_STATE_UPDATE, sessionId);
         this.stateOrdinal = stateOrdinal;
         this.detail = detail != null ? detail : "";
      }

      public SyncStateUpdatePacket() {
         super(LanPacketType.SYNC_STATE_UPDATE, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeInt(this.stateOrdinal);
         out.writeUTF(this.detail);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.stateOrdinal = in.readInt();
         this.detail = in.readUTF();
      }
   }

   public static class TcpCommandAckPacket extends LanPacket {
      public String senderUsername;
      public long executeNanoTime;
      public int executionMethod = 0;
      public long targetTick = -1L;
      public long actualTick = -1L;
      public boolean late = false;

      public TcpCommandAckPacket(String sessionId, String senderUsername, long executeNanoTime) {
         super(LanPacketType.TCP_COMMAND_ACK, sessionId);
         this.senderUsername = senderUsername != null ? senderUsername : "";
         this.executeNanoTime = executeNanoTime;
      }

      public TcpCommandAckPacket(
         String sessionId, String senderUsername, long executeNanoTime, int executionMethod, long targetTick, long actualTick, boolean late
      ) {
         this(sessionId, senderUsername, executeNanoTime);
         this.executionMethod = executionMethod;
         this.targetTick = targetTick;
         this.actualTick = actualTick;
         this.late = late;
      }

      public TcpCommandAckPacket() {
         super(LanPacketType.TCP_COMMAND_ACK, "");
      }

      @Override
      public void write(DataOutputStream out) throws IOException {
         out.writeUTF(this.senderUsername);
         out.writeLong(this.executeNanoTime);
         out.writeInt(this.executionMethod);
         out.writeLong(this.targetTick);
         out.writeLong(this.actualTick);
         out.writeBoolean(this.late);
      }

      @Override
      public void read(DataInputStream in) throws IOException {
         this.senderUsername = in.readUTF();
         this.executeNanoTime = in.readLong();

         try {
            this.executionMethod = in.readInt();
            this.targetTick = in.readLong();
            this.actualTick = in.readLong();
            this.late = in.readBoolean();
         } catch (EOFException var3) {
            this.executionMethod = 0;
            this.targetTick = -1L;
            this.actualTick = -1L;
            this.late = false;
         }
      }
   }
}
