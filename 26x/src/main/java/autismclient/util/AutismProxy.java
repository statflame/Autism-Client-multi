package autismclient.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public class AutismProxy {
    public enum Status {
        UNCHECKED,
        CHECKING,
        DEAD,
        ALIVE;

        public String display() {
            return switch (this) {
                case UNCHECKED -> "";
                case CHECKING -> "...";
                case DEAD -> "X";
                case ALIVE -> "O";
            };
        }

        public int color() {
            return switch (this) {
                case UNCHECKED, CHECKING -> 0xFFA8A8A8;
                case DEAD -> 0xFFFF5A5A;
                case ALIVE -> 0xFF5AFF91;
            };
        }
    }

    public String name = "";
    public AutismProxyType type = AutismProxyType.Socks5;
    public String address = "";
    public int port = 0;
    public boolean enabled = false;
    public String username = "";
    public String password = "";
    public volatile Status status = Status.UNCHECKED;
    public volatile long latency = 0L;

    public record CheckResult(Status status, long latency, int code) {
        public CheckResult {
            status = status == null ? Status.DEAD : status;
            latency = Math.max(0L, latency);
        }
    }

    private record TypeProbeResult(boolean alive, boolean timeout, long latency) {
    }

    public AutismProxy() {
    }

    public AutismProxy(Tag tag) {
        if (tag instanceof CompoundTag compoundTag) fromTag(compoundTag);
    }

    public boolean isValid() {
        return address != null && !address.isBlank() && port > 0 && port <= 65535;
    }

    public String displayName() {
        String label = name == null || name.isBlank() ? address : name;
        return label == null ? "" : label;
    }

    public synchronized int checkStatus(int timeoutMs) {
        status = Status.CHECKING;
        CheckResult result = probeStatus(timeoutMs);
        applyCheckResult(result);
        return result.code();
    }

    public synchronized CheckResult probeStatus(int timeoutMs) {
        boolean timeout = false;
        TypeProbeResult primary = checkType(type == AutismProxyType.Socks4 ? AutismProxyType.Socks4 : AutismProxyType.Socks5, timeoutMs);
        if (primary.alive()) return new CheckResult(Status.ALIVE, primary.latency(), 1);
        if (primary.timeout()) timeout = true;
        TypeProbeResult fallback = checkType(type == AutismProxyType.Socks4 ? AutismProxyType.Socks5 : AutismProxyType.Socks4, timeoutMs);
        if (fallback.alive()) return new CheckResult(Status.ALIVE, fallback.latency(), 1);
        if (fallback.timeout()) timeout = true;
        return new CheckResult(Status.DEAD, 0L, timeout ? 3 : 2);
    }

    public synchronized void applyCheckResult(CheckResult result) {
        if (result == null) {
            status = Status.UNCHECKED;
            latency = 0L;
            return;
        }
        status = result.status();
        latency = result.status() == Status.ALIVE ? result.latency() : 0L;
    }

    private TypeProbeResult checkType(AutismProxyType checkType, int timeoutMs) {
        try {
            Instant before = Instant.now();
            boolean alive = checkType == AutismProxyType.Socks4 ? isSocks4(timeoutMs) : isSocks5(timeoutMs);
            if (alive) {
                return new TypeProbeResult(true, false, Duration.between(before, Instant.now()).toMillis());
            }
        } catch (SocketTimeoutException e) {
            return new TypeProbeResult(false, true, 0L);
        } catch (IOException ignored) {
        }
        return new TypeProbeResult(false, false, 0L);
    }

    private boolean isSocks4(int timeoutMs) throws IOException {
        ByteBuffer bb;
        byte[] user = safe(username).getBytes();
        if (isIpv4Address(address)) {
            bb = ByteBuffer.allocate(9 + user.length)
                .put((byte) 4)
                .put((byte) 1)
                .putShort((short) port)
                .put(InetAddress.getByName(address).getAddress())
                .put(user)
                .put((byte) 0);
        } else {
            byte[] addr = safe(address).getBytes();
            bb = ByteBuffer.allocate(10 + user.length + addr.length)
                .put((byte) 4)
                .put((byte) 1)
                .putShort((short) port)
                .put(new byte[]{0, 0, 0, 1})
                .put(user)
                .put((byte) 0)
                .put(addr)
                .put((byte) 0);
        }
        byte[] data = sendData(bb.array(), 8, timeoutMs);
        return data.length >= 2 && data[0] == 0 && data[1] == 90;
    }

    private boolean isSocks5(int timeoutMs) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(4)
            .put((byte) 5)
            .put((byte) 2)
            .put((byte) 0)
            .put((byte) 2);
        byte[] data = sendData(bb.array(), 2, timeoutMs);
        return data.length >= 2 && data[0] == 5 && (data[1] == 0 || data[1] == 2);
    }

    private byte[] sendData(byte[] data, int read, int timeoutMs) throws IOException {
        try (Socket socket = new Socket()) {
            int timeout = Math.max(1, timeoutMs);
            socket.setSoTimeout(timeout);
            socket.connect(new InetSocketAddress(address, port), timeout);
            OutputStream output = socket.getOutputStream();
            output.write(data);
            return socket.getInputStream().readNBytes(read);
        }
    }

    private static boolean isIpv4Address(String value) {
        if (value == null) return false;
        String[] parts = value.split("\\.");
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                int i = Integer.parseInt(part);
                if (i < 0 || i > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", safe(name));
        tag.putString("type", type == null ? AutismProxyType.Socks5.name() : type.name());
        tag.putString("address", safe(address));
        tag.putInt("port", port);
        tag.putBoolean("enabled", enabled);
        tag.putString("username", safe(username));
        tag.putString("password", safe(password));
        return tag;
    }

    public AutismProxy fromTag(CompoundTag tag) {
        name = tag.getStringOr("name", "");
        String typeName = tag.getStringOr("type", AutismProxyType.Socks5.name());
        try {
            type = AutismProxyType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            type = typeName.toLowerCase(Locale.ROOT).contains("4") ? AutismProxyType.Socks4 : AutismProxyType.Socks5;
        }
        address = tag.getStringOr("address", "");
        port = tag.getIntOr("port", 0);
        enabled = tag.getBooleanOr("enabled", false);
        username = tag.getStringOr("username", "");
        password = tag.getStringOr("password", "");
        status = Status.UNCHECKED;
        latency = 0L;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AutismProxy proxy)) return false;
        return port == proxy.port && Objects.equals(address, proxy.address) && type == proxy.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, address, port);
    }
}
